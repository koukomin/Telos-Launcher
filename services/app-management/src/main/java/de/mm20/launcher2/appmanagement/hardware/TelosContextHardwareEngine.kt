package de.mm20.launcher2.appmanagement.hardware

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import de.mm20.launcher2.appmanagement.stealth.TelosPrivilegedDaemon
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.packages.TelosPackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class HardwareProfile(
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
) {
    val any: Boolean get() = camera || microphone || location
}

data class HardwareAppRow(
    val packageName: String,
    val label: String,
    val profile: HardwareProfile,
    val seeded: Boolean,
)

object TelosContextHardwareEngine {
    private const val TAG = "TelosHwEngine"
    private const val PREFS = "telos_context_hardware"
    private const val KEY_MASTER = "master"
    private const val KEY_FORCE_MIC = "force_mic_calls"
    private const val POLL_MS = 1_000L
    private val SAFE_PKG = Regex("^[a-zA-Z0-9._]+$")

    val PERM_CAMERA = "android.permission.CAMERA"
    val PERM_MIC = "android.permission.RECORD_AUDIO"
    val PERM_GPS = "android.permission.ACCESS_FINE_LOCATION"
    val OP_CAMERA = "CAMERA"
    val OP_MIC = "RECORD_AUDIO"
    val OP_GPS = "FINE_LOCATION"

    val SEEDED_CALL: Set<String> = setOf(
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.google.android.apps.googlevoice",
    )
    val SEEDED_VIDEO: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.viber.voip",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thoughtcrime.securesms",
        "us.zoom.videomeetings",
        "com.skype.raider",
        "com.google.android.apps.tachyon",
        "com.facebook.orca",
        "com.discord",
        "com.microsoft.teams",
    )
    val SEEDED_NAV: Set<String> = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "net.osmand",
        "net.osmand.plus",
        "com.mapswithme.maps.pro",
    )

    private val CALL_HINT = Regex("(dialer|incall|phone|voice)", RegexOption.IGNORE_CASE)
    private val VIDEO_HINT = Regex("(whatsapp|viber|telegram|zoom|skype|meet|discord|signal|teams)", RegexOption.IGNORE_CASE)
    private val NAV_HINT = Regex("(maps|waze|osmand|navigation|navi)", RegexOption.IGNORE_CASE)

    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)
    private val started = AtomicBoolean(false)
    private var pollJob: Job? = null
    private var appContext: Context? = null
    private val lastForeground = AtomicReference("")
    private val granted = ConcurrentHashMap<String, HardwareProfile>()
    private var lastUsageQuery = 0L
    private var inPhoneCall = false

    private val _master = MutableStateFlow(true)
    val master: StateFlow<Boolean> = _master.asStateFlow()

    private val _forceMic = MutableStateFlow(true)
    val forceMicOnCalls: StateFlow<Boolean> = _forceMic.asStateFlow()

    private val _rows = MutableStateFlow<List<HardwareAppRow>>(emptyList())
    val rows: StateFlow<List<HardwareAppRow>> = _rows.asStateFlow()

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        loadPrefs(app)
        if (!started.compareAndSet(false, true)) {
            ensurePolling()
            return
        }
        registerTelephony(app)
        scope.launch { refreshRows(app) }
        ensurePolling()
    }

    fun setMaster(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER, enabled).apply()
        _master.value = enabled
        if (!enabled) {
            scope.launch { revokeAll() }
            pollJob?.cancel()
            pollJob = null
        } else {
            ensurePolling()
        }
    }

    fun setForceMicOnCalls(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_MIC, enabled).apply()
        _forceMic.value = enabled
    }

    fun setProfile(context: Context, packageName: String, profile: HardwareProfile) {
        if (!SAFE_PKG.matches(packageName)) return
        val app = context.applicationContext
        prefs(app).edit()
            .putBoolean("cam:$packageName", profile.camera)
            .putBoolean("mic:$packageName", profile.microphone)
            .putBoolean("gps:$packageName", profile.location)
            .putBoolean("set:$packageName", true)
            .apply()
        scope.launch { refreshRows(app) }
    }

    fun profileFor(context: Context, packageName: String): HardwareProfile {
        return resolvedProfile(prefs(context), packageName)
    }

    fun onPreLaunch(context: Context, packageName: String) {
        if (!_master.value) return
        if (!SAFE_PKG.matches(packageName)) return
        scope.launch { applyFor(packageName, reason = "launch") }
    }

    fun onHomeResumed(context: Context) {
        if (!_master.value) return
        if (inPhoneCall || isVoipAudio(context)) return
        scope.launch { revokeAll() }
    }

    fun defaultSeedProfile(packageName: String, label: String = ""): HardwareProfile {
        val hay = "$packageName $label"
        return when {
            packageName in SEEDED_VIDEO || VIDEO_HINT.containsMatchIn(hay) ->
                HardwareProfile(camera = true, microphone = true)
            packageName in SEEDED_NAV || NAV_HINT.containsMatchIn(hay) ->
                HardwareProfile(location = true)
            packageName in SEEDED_CALL || CALL_HINT.containsMatchIn(hay) ->
                HardwareProfile(microphone = true)
            else -> HardwareProfile()
        }
    }

    suspend fun refreshRows(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        val repo = runCatching { TelosPackageRepository.getInstance(app) }.getOrNull()
        val packages = repo?.awaitPackages() ?: emptyList()
        val out = ArrayList<HardwareAppRow>(64)
        for (entry in packages) {
            val seeded = entry.packageName in SEEDED_CALL ||
                entry.packageName in SEEDED_VIDEO ||
                entry.packageName in SEEDED_NAV
            val profile = resolvedProfile(p, entry.packageName, entry.label)
            if (!profile.any && !seeded && !p.getBoolean("set:${entry.packageName}", false)) continue
            out.add(
                HardwareAppRow(
                    packageName = entry.packageName,
                    label = entry.label,
                    profile = profile,
                    seeded = seeded,
                ),
            )
        }
        out.sortWith(
            compareByDescending<HardwareAppRow> { it.profile.any }
                .thenBy { it.label.lowercase(Locale.ROOT) },
        )
        _rows.value = out
    }

    private fun resolvedProfile(
        p: android.content.SharedPreferences,
        packageName: String,
        label: String = "",
    ): HardwareProfile {
        if (p.getBoolean("set:$packageName", false)) {
            return HardwareProfile(
                camera = p.getBoolean("cam:$packageName", false),
                microphone = p.getBoolean("mic:$packageName", false),
                location = p.getBoolean("gps:$packageName", false),
            )
        }
        return defaultSeedProfile(packageName, label)
    }

    private fun ensurePolling() {
        if (!_master.value) return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive && _master.value) {
                pollForeground()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun pollForeground() {
        val app = appContext ?: return
        val fg = readForegroundPackage(app)
        val voip = isVoipAudio(app)
        if (fg != lastForeground.get()) {
            val previous = lastForeground.getAndSet(fg)
            if (previous.isNotEmpty() && previous != fg && previous != app.packageName) {
                if (!inPhoneCall && !voip) revoke(previous)
            }
            if (fg.isNotEmpty() && fg != app.packageName) {
                applyFor(fg, reason = "foreground")
            }
        } else if (inPhoneCall || voip) {
            val target = fg.ifBlank { lastForeground.get() }
            if (target.isNotEmpty()) applyFor(target, reason = "call", forceMic = _forceMic.value)
        }
    }

    private fun readForegroundPackage(context: Context): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return lastForeground.get()
        val now = System.currentTimeMillis()
        val from = if (lastUsageQuery == 0L) now - 8_000L else lastUsageQuery - 1
        val events = runCatching { usm.queryEvents(from, now) }.getOrNull() ?: return lastForeground.get()
        lastUsageQuery = now
        val event = UsageEvents.Event()
        var latest = lastForeground.get()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val resumed = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (resumed) {
                latest = pkg
            }
        }
        return latest
    }

    private fun isVoipAudio(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val mode = am.mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private suspend fun applyFor(packageName: String, reason: String, forceMic: Boolean = false) {
        if (!_master.value) return
        if (!SAFE_PKG.matches(packageName)) return
        val app = appContext ?: return
        if (packageName == app.packageName) return
        var profile = profileFor(app, packageName)
        if (forceMic || (_forceMic.value && (inPhoneCall || isVoipAudio(app)) &&
                (packageName in SEEDED_CALL || packageName in SEEDED_VIDEO || profile.microphone))
        ) {
            profile = profile.copy(microphone = true)
        }
        if (!profile.any) return
        Log.d(TAG, "grant $packageName cam=${profile.camera} mic=${profile.microphone} gps=${profile.location} ($reason)")
        if (profile.camera) grant(packageName, PERM_CAMERA, OP_CAMERA)
        if (profile.microphone) grant(packageName, PERM_MIC, OP_MIC)
        if (profile.location) grant(packageName, PERM_GPS, OP_GPS)
        granted[packageName] = profile
    }

    private suspend fun revoke(packageName: String) {
        val profile = granted.remove(packageName) ?: return
        if (!SAFE_PKG.matches(packageName)) return
        Log.d(TAG, "revoke $packageName")
        if (profile.camera) deny(packageName, PERM_CAMERA, OP_CAMERA)
        if (profile.microphone) deny(packageName, PERM_MIC, OP_MIC)
        if (profile.location) deny(packageName, PERM_GPS, OP_GPS)
    }

    private suspend fun revokeAll() {
        val keys = granted.keys.toList()
        for (pkg in keys) revoke(pkg)
    }

    private suspend fun grant(packageName: String, perm: String, op: String) {
        TelosPrivilegedDaemon.exec("pm grant $packageName $perm")
        TelosPrivilegedDaemon.exec("cmd appops set $packageName $op allow")
    }

    private suspend fun deny(packageName: String, perm: String, op: String) {
        TelosPrivilegedDaemon.exec("cmd appops set $packageName $op ignore")
        TelosPrivilegedDaemon.exec("pm revoke $packageName $perm")
    }

    private fun registerTelephony(context: Context) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        fun onState(state: Int) {
            inPhoneCall = state == TelephonyManager.CALL_STATE_OFFHOOK ||
                state == TelephonyManager.CALL_STATE_RINGING
            if (!inPhoneCall && _master.value && !isVoipAudio(context)) {
                scope.launch { revokeAll() }
            } else if (inPhoneCall && _forceMic.value) {
                val fg = lastForeground.get()
                if (fg.isNotEmpty()) scope.launch { applyFor(fg, "telephony", forceMic = true) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onState(state)
            }
            runCatching {
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
            }
            runCatching {
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun loadPrefs(context: Context) {
        val p = prefs(context)
        _master.value = p.getBoolean(KEY_MASTER, true)
        _forceMic.value = p.getBoolean(KEY_FORCE_MIC, true)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
