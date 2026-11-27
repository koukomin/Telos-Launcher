package de.mm20.launcher2.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import de.mm20.launcher2.base.R as BaseR

enum class AndroidFlavor(val displayName: String) {
    OneUI("Samsung OneUI"),
    ColorOS("Oppo / OnePlus ColorOS"),
    HyperOS("Xiaomi / Poco HyperOS"),
    GrapheneOS("GrapheneOS / Hardened Android"),
    PixelUI("Google Pixel UI"),
    Stock("Standard Android / AOSP")
}

data class OemHelpGuide(
    val title: String,
    val description: String,
    val autostartIntent: Intent?,
    val batteryIntent: Intent?,
    val specialIntent: Intent? = null,
    val specialLabel: String? = null
)

/**
 * OEM Integration Engine for Telos Launcher.
 * Automatically detects Android OS variants (OneUI, ColorOS, HyperOS, GrapheneOS)
 * and provides OS-specific auto-configurations, intents, and guided help.
 */
object OemIntegrationEngine {

    fun detectFlavor(): AndroidFlavor {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        val host = Build.HOST.lowercase()

        return when {
            host.contains("grapheneos") || fingerprint.contains("grapheneos") -> AndroidFlavor.GrapheneOS
            manufacturer.contains("samsung") || brand.contains("samsung") -> AndroidFlavor.OneUI
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> AndroidFlavor.ColorOS
            manufacturer.contains("xiaomi") || brand.contains("poco") || brand.contains("redmi") -> AndroidFlavor.HyperOS
            manufacturer.contains("google") || brand.contains("pixel") -> AndroidFlavor.PixelUI
            else -> AndroidFlavor.Stock
        }
    }

    fun getHelpGuide(context: Context): OemHelpGuide {
        val flavor = detectFlavor()
        val pkg = context.packageName

        return when (flavor) {
            AndroidFlavor.OneUI -> OemHelpGuide(
                title = "Samsung OneUI Integration",
                description = "To ensure uninterrupted widgets and live wallpapers, set Battery to Unrestricted in Device Care.",
                autostartIntent = Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                specialIntent = context.packageManager.getLaunchIntentForPackage("com.samsung.knox.securefolder"),
                specialLabel = "Open Knox Secure Folder"
            )
            AndroidFlavor.ColorOS -> OemHelpGuide(
                title = "ColorOS / OxygenOS Integration",
                description = "Enable Auto-Launch in Security Center so Telos Launcher remains active in background.",
                autostartIntent = Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                specialIntent = Intent().setComponent(ComponentName("com.coloros.smartsidebar", "com.coloros.smartsidebar.SmartSidebarSettingActivity")),
                specialLabel = "ColorOS Smart Sidebar"
            )
            AndroidFlavor.HyperOS -> OemHelpGuide(
                title = "Xiaomi HyperOS / MIUI Integration",
                description = "Grant Autostart and Display Floating Windows in Security Center to prevent background kills.",
                autostartIntent = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                batteryIntent = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
                specialIntent = Intent("miui.intent.action.APP_PERM_EDITOR").setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")).putExtra("extra_pkgname", pkg),
                specialLabel = "HyperOS App Permissions"
            )
            AndroidFlavor.GrapheneOS -> OemHelpGuide(
                title = "GrapheneOS / Hardened Android Integration",
                description = "Telos Launcher is 100% compliant with Storage & Contact Scopes and runs without Google Play Services.",
                autostartIntent = null,
                batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                specialIntent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", pkg),
                specialLabel = "GrapheneOS Notification Scopes"
            )
            else -> OemHelpGuide(
                title = "Standard Android Integration",
                description = "Ensure Telos is set as Default Home App and exempted from battery optimizations.",
                autostartIntent = Intent(Settings.ACTION_HOME_SETTINGS),
                batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
            )
        }
    }
}
