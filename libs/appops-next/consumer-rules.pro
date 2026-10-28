# Shizuku loads exactly this UserService by name from the host APK.
-keep class dev.izumi.appopsnext.shizuku.service.AppOpsUserService { public <init>(); public <init>(android.content.Context); }
