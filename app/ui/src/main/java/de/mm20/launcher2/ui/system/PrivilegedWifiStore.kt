package de.mm20.launcher2.ui.system

import android.content.AttributionSource
import android.content.Context
import android.net.wifi.IActionListener
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import de.mm20.launcher2.ktx.PrivilegedShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import java.security.SecureRandom

internal enum class WifiPrivilege { Root, Shizuku, None }

internal data class WifiPrivilegeState(
    val privilege: WifiPrivilege,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuAuthorized: Boolean,
    val rootAvailable: Boolean,
)

internal object PrivilegedWifiStore {
    private const val TAG = "PrivilegedWifiStore"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val NOTES_PREFS = "telos_wifi_notes"
    private const val SETTINGS_PREFS = "telos_wifi_manager"
    private const val KEY_AUTO_PERSIST = "auto_persist"
    private const val APP_ONLY_FILE = "telos_wifi_app_only.json"

    fun shizukuInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true }.getOrDefault(false)

    fun shizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun shizukuAuthorized(): Boolean =
        shizukuRunning() && runCatching { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    suspend fun detect(context: Context): WifiPrivilegeState = withContext(Dispatchers.IO) {
        val root = PrivilegedShell.rootAvailable()
        val running = shizukuRunning()
        val authorized = shizukuAuthorized()
        val privilege = when {
            root -> WifiPrivilege.Root
            authorized -> WifiPrivilege.Shizuku
            else -> WifiPrivilege.None
        }
        WifiPrivilegeState(privilege, shizukuInstalled(context), running, authorized, root)
    }

    suspend fun requestShizuku(): Boolean = withContext(Dispatchers.Main) {
        if (!shizukuRunning()) return@withContext false
        if (shizukuAuthorized()) return@withContext true
        suspendCancellableCoroutine { cont ->
            val code = 0x57494649
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != code) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            runCatching { Shizuku.requestPermission(code) }.onFailure {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) {
            context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun notes(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)

    fun noteFor(context: Context, ssid: String): String =
        notes(context).getString(ssid, "").orEmpty()

    fun saveNote(context: Context, ssid: String, note: String) {
        notes(context).edit().apply {
            if (note.isBlank()) remove(ssid) else putString(ssid, note)
        }.apply()
    }

    fun autoPersist(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_PERSIST, false)

    fun setAutoPersist(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_PERSIST, enabled).apply()
    }

    suspend fun load(context: Context): List<WifiEntry> = withContext(Dispatchers.IO) {
        val api = privilegedConfigurations()
        val fromApi = api.mapNotNull { it.toWifiEntry() }
        val fromXml = if (fromApi.none { !it.psk.isNullOrBlank() }) readXmlStore() else emptyList()
        val merged = LinkedHashMap<String, WifiEntry>()
        (fromApi + fromXml).forEach { merged[it.ssid] = it }
        loadAppOnly(context).forEach { local ->
            merged.putIfAbsent(local.ssid, local.copy(existInSystem = false))
        }
        merged.values.toList()
    }

    suspend fun persistEphemeral(): Int = withContext(Dispatchers.IO) {
        val configs = privilegedConfigurations()
        var count = 0
        configs.forEach { config ->
            if (!isEphemeral(config)) return@forEach
            setBooleanField(config, "ephemeral", false)
            setBooleanField(config, "fromWifiNetworkSuggestion", false)
            if (addOrUpdate(config)) count++
        }
        count
    }

    suspend fun forget(netId: Int): Boolean = withContext(Dispatchers.IO) {
        if (netId < 0) return@withContext false
        val wm = wifiManager() ?: return@withContext shellForget(netId)
        runCatching {
            awaitAction { listener ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HiddenApiBypass.invoke(wm.javaClass, wm, "forget", netId, listener)
                } else {
                    HiddenApiBypass.invoke(wm.javaClass, wm, "forget", netId, Binder(), listener, listener.hashCode())
                }
            }
        }.getOrElse { shellForget(netId) }
    }

    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        val wm = wifiManager() ?: return@withContext PrivilegedShell.run("cmd wifi disconnect")?.exitCode == 0
        val pkg = callingPackage()
        runCatching { HiddenApiBypass.invoke(wm.javaClass, wm, "disconnect", pkg) as Boolean }.getOrDefault(false)
    }

    suspend fun connect(netId: Int): Boolean = withContext(Dispatchers.IO) {
        if (netId < 0) return@withContext false
        val wm = wifiManager() ?: return@withContext PrivilegedShell.run("cmd wifi connect-network $netId")?.exitCode == 0
        runCatching {
            awaitAction { listener ->
                when {
                    Build.VERSION.SDK_INT >= 35 -> HiddenApiBypass.invoke(
                        wm.javaClass, wm, "connect", null, netId, listener, callingPackage(), extrasBundle(),
                    )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> HiddenApiBypass.invoke(
                        wm.javaClass, wm, "connect", null, netId, listener, callingPackage(),
                    )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> HiddenApiBypass.invoke(
                        wm.javaClass, wm, "connect", null, netId, listener,
                    )
                    else -> HiddenApiBypass.invoke(
                        wm.javaClass, wm, "connect", null, netId, Binder(), listener, listener.hashCode(),
                    )
                }
            }
        }.getOrDefault(false)
    }

    suspend fun forgetAll(entries: List<WifiEntry>): Int {
        var n = 0
        entries.filter { it.existInSystem && it.networkId >= 0 }.forEach { if (forget(it.networkId)) n++ }
        return n
    }

    fun rememberAppOnly(context: Context, entry: WifiEntry) {
        val file = File(context.applicationContext.filesDir, APP_ONLY_FILE)
        val existing = loadAppOnly(context).associateBy { it.ssid }.toMutableMap()
        existing[entry.ssid] = entry.copy(existInSystem = false, networkId = -1)
        file.writeText(exportJson(existing.values.toList()))
    }

    fun loadAppOnly(context: Context): List<WifiEntry> {
        val file = File(context.applicationContext.filesDir, APP_ONLY_FILE)
        if (!file.exists()) return emptyList()
        return runCatching { importJson(file.readText()) }.getOrDefault(emptyList())
    }

    fun exportJson(entries: List<WifiEntry>): String {
        val array = JSONArray()
        entries.forEach { row ->
            array.put(JSONObject().apply {
                put("ssid", row.ssid)
                put("password", row.psk ?: JSONObject.NULL)
                put("security", row.security)
                put("hidden", row.hidden)
                put("autojoin", row.autoJoin)
                put("note", row.note ?: JSONObject.NULL)
            })
        }
        return array.toString(2)
    }

    fun importJson(raw: String): List<WifiEntry> {
        val trimmed = raw.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONArray().put(JSONObject(trimmed))
        }
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val ssid = obj.optString("ssid").ifBlank { obj.optString("SSID") }
                if (ssid.isBlank()) continue
                val password = obj.optString("password").ifBlank { obj.optString("psk") }.takeIf { it.isNotBlank() && it != "null" }
                add(
                    WifiEntry(
                        ssid = ssid,
                        psk = password,
                        security = obj.optString("security"),
                        hidden = obj.optBoolean("hidden"),
                        autoJoin = obj.optBoolean("autojoin", true),
                        note = obj.optString("note").takeIf { it.isNotBlank() && it != "null" },
                        existInSystem = false,
                    ),
                )
            }
        }
    }

    fun gzip(bytes: ByteArray): ByteArray = java.io.ByteArrayOutputStream().use { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
        out.toByteArray()
    }

    fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    fun encrypt(content: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return byteArrayOf(1) + salt + iv + cipher.doFinal(content)
    }

    fun decrypt(content: ByteArray, password: String): ByteArray {
        val salt = content.copyOfRange(1, 17)
        val iv = content.copyOfRange(17, 29)
        val encrypted = content.copyOfRange(29, content.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun wifiManager(): Any? {
        if (!shizukuAuthorized() && !runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return null
        if (!shizukuAuthorized()) return null
        return runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/net/wifi/", "Landroid/os/")
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.WIFI_SERVICE))
            val stub = Class.forName("android.net.wifi.IWifiManager\$Stub")
            HiddenApiBypass.invoke(stub, null, "asInterface", binder)
        }.onFailure { Log.w(TAG, "IWifiManager unavailable", it) }.getOrNull()
    }

    private fun privilegedUser(): String = when (runCatching { Shizuku.getUid() }.getOrDefault(-1)) {
        Process.ROOT_UID -> "root"
        Process.SYSTEM_UID -> "android"
        Process.SHELL_UID -> "shell"
        else -> "shell"
    }

    private fun callingPackage(): String =
        if (runCatching { Shizuku.getUid() }.getOrDefault(-1) == Process.SYSTEM_UID) "android" else "com.android.shell"

    private fun extrasBundle(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(Process.SHELL_UID)
        val source = AttributionSource.Builder(uid).setPackageName(callingPackage()).build()
        return Bundle().apply { putParcelable("EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE", source) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun privilegedConfigurations(): List<WifiConfiguration> {
        val wm = wifiManager() ?: return emptyList()
        val extras = extrasBundle()
        val slice = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                HiddenApiBypass.invoke(wm.javaClass, wm, "getPrivilegedConfiguredNetworks", privilegedUser(), callingPackage(), extras)
            } else {
                runCatching {
                    HiddenApiBypass.invoke(wm.javaClass, wm, "getPrivilegedConfiguredNetworks", privilegedUser(), callingPackage())
                }.getOrElse {
                    HiddenApiBypass.invoke(wm.javaClass, wm, "getPrivilegedConfiguredNetworks", privilegedUser(), callingPackage(), extras)
                }
            }
        }.onFailure { Log.w(TAG, "getPrivilegedConfiguredNetworks failed", it) }.getOrNull() ?: return emptyList()
        val list = runCatching { HiddenApiBypass.invoke(slice.javaClass, slice, "getList") as? List<WifiConfiguration> }.getOrNull()
        return list.orEmpty()
    }

    private fun addOrUpdate(config: WifiConfiguration): Boolean {
        val wm = wifiManager() ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val result = HiddenApiBypass.invoke(wm.javaClass, wm, "addOrUpdateNetworkPrivileged", config, privilegedUser())
                val code = result?.javaClass?.getField("statusCode")?.getInt(result)
                code == WifiManager.AddNetworkResult.STATUS_SUCCESS
            } else {
                (HiddenApiBypass.invoke(wm.javaClass, wm, "addOrUpdateNetwork", config, privilegedUser()) as Int) != -1
            }
        }.getOrDefault(false)
    }

    private suspend fun awaitAction(block: (IActionListener) -> Unit): Boolean =
        suspendCancellableCoroutine { cont ->
            val listener = object : IActionListener.Stub() {
                override fun onSuccess() { if (cont.isActive) cont.resume(true) }
                override fun onFailure(reason: Int) { if (cont.isActive) cont.resume(false) }
            }
            runCatching { block(listener) }.onFailure { if (cont.isActive) cont.resume(false) }
        }

    private suspend fun shellForget(netId: Int): Boolean =
        PrivilegedShell.run("cmd wifi forget-network $netId")?.exitCode == 0

    private suspend fun readXmlStore(): List<WifiEntry> {
        val files = listOf(
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/wifi/WifiConfigStore.xml",
            "/data/misc/wifi/wpa_supplicant.conf",
        )
        val raw = files.mapNotNull { PrivilegedShell.run("cat $it 2>/dev/null")?.output?.takeIf { out -> out.isNotBlank() } } +
            listOfNotNull(PrivilegedShell.run("cat /data/misc_ce/*/wifi/WifiConfigStore.xml /data/misc_ce/*/apexdata/com.android.wifi/WifiConfigStore.xml 2>/dev/null")?.output?.takeIf { it.isNotBlank() })
        return raw.flatMap(SavedWifiParser::parse).distinctBy { it.ssid }
    }

    private fun WifiConfiguration.toWifiEntry(): WifiEntry? {
        val ssid = (SSID ?: "").trim().removeSurrounding("\"").ifBlank { return null }
        val psk = preSharedKey?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "*" }
            ?: wepKeys?.firstOrNull { !it.isNullOrBlank() && it != "*" }?.removeSurrounding("\"")
        return WifiEntry(
            ssid = ssid,
            psk = psk,
            networkId = networkId,
            security = securityLabel(this),
            hidden = hiddenSSID,
            autoJoin = runCatching { allowAutojoin }.getOrDefault(true),
            existInSystem = true,
            ephemeral = isEphemeral(this),
        )
    }

    private fun securityLabel(config: WifiConfiguration): String {
        val km = config.allowedKeyManagement
        return when {
            km.get(WifiConfiguration.KeyMgmt.SAE) -> "WPA3"
            km.get(WifiConfiguration.KeyMgmt.WPA_PSK) -> "WPA2"
            km.get(WifiConfiguration.KeyMgmt.NONE) && config.wepKeys?.firstOrNull() != null -> "WEP"
            km.get(WifiConfiguration.KeyMgmt.OWE) -> "OWE"
            else -> "OPEN"
        }
    }

    private fun isEphemeral(config: WifiConfiguration): Boolean =
        runCatching {
            val field = WifiConfiguration::class.java.getDeclaredField("ephemeral")
            field.isAccessible = true
            field.getBoolean(config)
        }.getOrDefault(false)

    private fun setBooleanField(config: WifiConfiguration, name: String, value: Boolean) {
        runCatching {
            val field = WifiConfiguration::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.setBoolean(config, value)
        }
    }
}
