package de.mm20.launcher2.appmanagement.stealth

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.core.content.getSystemService
import de.mm20.launcher2.coroutines.TelosDispatchers
import de.mm20.launcher2.packages.TelosPackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class StealthAppRow(
    val packageName: String,
    val label: String,
    val protected: Boolean,
    val seeded: Boolean,
    val heuristic: Boolean,
)

object TelosStealthBlacklist {
    val SEEDED: Set<String> = setOf(
        // Greek banking & gov
        "gr.nbg.mobilebanking",
        "gr.nbg.nbgpay",
        "com.winbank.mobile",
        "gr.eurobank.mobile",
        "gr.alphabank.myAlphaMobile",
        "gr.cosmote.payzy",
        "com.vivawallet.spoc.payapp",
        "gr.gov.wallet",
        "gr.gov.mindigital.govgr",
        "gr.atticabank.mobile",
        // Global fintech
        "com.revolut.revolut",
        "de.number26.android",
        "com.transferwise.android",
        "com.imaginecurve.curve.prd",
        "com.paypal.android.p2pmobile",
        "com.google.android.apps.walletnfcrel",
        "com.binance.dev",
        "io.metamask",
        // Enterprise
        "com.google.android.gms",
        "com.android.vending",
        "com.microsoft.windowsintune.companyportal",
        "com.azure.authenticator",
        // Anti-cheat titles
        "com.nianticlabs.pokemongo",
        "com.supercell.brawlstars",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.tencent.ig",
        "com.activision.callofduty.shooter",
        "com.aniplex.fategrandorder.en",
    )

    private val HEURISTIC = Regex(
        "(bank|wallet|pay|revolut|finance|gov|intune|authenticator|nbg|eurobank|alpha.?bank)",
        RegexOption.IGNORE_CASE,
    )

    private const val PREFS = "telos_stealth_cloak"
    private const val KEY_EXTRA = "extra_packages"
    private const val KEY_REMOVED = "removed_packages"
    private const val KEY_NEW = "newly_detected"

    private val scope = CoroutineScope(SupervisorJob() + TelosDispatchers.Disk)
    private val callbackRegistered = AtomicBoolean(false)

    private val _rows = MutableStateFlow<List<StealthAppRow>>(emptyList())
    val rows: StateFlow<List<StealthAppRow>> = _rows.asStateFlow()

    private val _newlyDetected = MutableStateFlow<List<String>>(emptyList())
    val newlyDetected: StateFlow<List<String>> = _newlyDetected.asStateFlow()

    fun matchesHeuristic(packageName: String, label: String, category: Int): Boolean {
        // Fallback: CATEGORY_FINANCE is API 35+. We skip it here to fix compilation.
        val hay = "$packageName $label".lowercase(Locale.ROOT)
        return HEURISTIC.containsMatchIn(hay)
    }

    fun isProtected(context: Context, packageName: String): Boolean {
        val prefs = prefs(context)
        val removed = prefs.getStringSet(KEY_REMOVED, emptySet()).orEmpty()
        if (packageName in removed) return false
        if (packageName in SEEDED) return true
        val extra = prefs.getStringSet(KEY_EXTRA, emptySet()).orEmpty()
        return packageName in extra
    }

    fun setProtected(context: Context, packageName: String, protected: Boolean) {
        val prefs = prefs(context)
        val extra = prefs.getStringSet(KEY_EXTRA, emptySet()).orEmpty().toMutableSet()
        val removed = prefs.getStringSet(KEY_REMOVED, emptySet()).orEmpty().toMutableSet()
        if (protected) {
            removed.remove(packageName)
            if (packageName !in SEEDED) extra.add(packageName)
        } else {
            extra.remove(packageName)
            removed.add(packageName)
        }
        prefs.edit().putStringSet(KEY_EXTRA, extra).putStringSet(KEY_REMOVED, removed).apply()
        scope.launch { refresh(context) }
    }

    fun consumeNewlyDetected(context: Context) {
        prefs(context).edit().putStringSet(KEY_NEW, emptySet()).apply()
        _newlyDetected.value = emptyList()
    }

    fun start(context: Context) {
        val app = context.applicationContext
        if (callbackRegistered.compareAndSet(false, true)) {
            runCatching {
                app.getSystemService<LauncherApps>()?.registerCallback(object : LauncherApps.Callback() {
                    override fun onPackageAdded(packageName: String, user: UserHandle) {
                        scope.launch { onPackageMaybeSensitive(app, packageName) }
                    }
                    override fun onPackageChanged(packageName: String, user: UserHandle) = Unit
                    override fun onPackageRemoved(packageName: String, user: UserHandle) {
                        scope.launch { refresh(app) }
                    }
                    override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle, replacing: Boolean) = Unit
                    override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle, replacing: Boolean) = Unit
                })
            }
        }
        scope.launch { refresh(app) }
    }

    private suspend fun onPackageMaybeSensitive(context: Context, packageName: String) {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(packageName)
        if (!matchesHeuristic(packageName, label, info.category) && packageName !in SEEDED) {
            refresh(context)
            return
        }
        val prefs = prefs(context)
        val removed = prefs.getStringSet(KEY_REMOVED, emptySet()).orEmpty()
        if (packageName in removed) {
            refresh(context)
            return
        }
        val extra = prefs.getStringSet(KEY_EXTRA, emptySet()).orEmpty().toMutableSet()
        if (packageName !in SEEDED) extra.add(packageName)
        val newly = prefs.getStringSet(KEY_NEW, emptySet()).orEmpty().toMutableSet()
        newly.add(packageName)
        prefs.edit().putStringSet(KEY_EXTRA, extra).putStringSet(KEY_NEW, newly).apply()
        _newlyDetected.value = newly.toList()
        refresh(context)
    }

    suspend fun refresh(context: Context) = withContext(TelosDispatchers.Disk) {
        val app = context.applicationContext
        val prefs = prefs(app)
        val extra = prefs.getStringSet(KEY_EXTRA, emptySet()).orEmpty()
        val removed = prefs.getStringSet(KEY_REMOVED, emptySet()).orEmpty()
        _newlyDetected.value = prefs.getStringSet(KEY_NEW, emptySet()).orEmpty().toList()
        val repo = runCatching { TelosPackageRepository.getInstance(app) }.getOrNull()
        val packages = repo?.awaitPackages() ?: emptyList()
        val extraMutable = extra.toMutableSet()
        val rows = ArrayList<StealthAppRow>(packages.size)
        for (entry in packages) {
            val seeded = entry.packageName in SEEDED
            val heuristic = matchesHeuristic(entry.packageName, entry.label, ApplicationInfo.CATEGORY_UNDEFINED)
            if (heuristic && entry.packageName !in SEEDED && entry.packageName !in removed) {
                extraMutable.add(entry.packageName)
            }
            val protected = entry.packageName !in removed &&
                (seeded || entry.packageName in extraMutable)
            rows.add(
                StealthAppRow(
                    packageName = entry.packageName,
                    label = entry.label,
                    protected = protected,
                    seeded = seeded,
                    heuristic = heuristic && !seeded,
                )
            )
        }
        if (extraMutable != extra) {
            prefs.edit().putStringSet(KEY_EXTRA, extraMutable).apply()
        }
        rows.sortWith(
            compareByDescending<StealthAppRow> { it.protected }
                .thenByDescending { it.seeded }
                .thenBy { it.label.lowercase(Locale.ROOT) },
        )
        _rows.value = rows
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
