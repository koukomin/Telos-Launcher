-keepattributes SourceFile,LineNumberTable
# === TELOS_PENDING_REVIEW_START: perf_optimizations ===
# Telos note: Optimizations were disabled here (-dontoptimize). Removing the flag allows R8 to execute inlining, dead code removal, and constant propagation.
# -dontoptimize

# Preserve Shizuku internal methods accessed via reflection by SmartFreezeService
-keep class rikka.shizuku.** { *; }
# === TELOS_PENDING_REVIEW_END: perf_optimizations ===
-keep public class de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM {
    public <init>();
}


# Debug and verbose log calls do nothing useful in a release build and cost time and size
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Telos Files: SSH, SMB and FTP clients use reflection and optional libraries that Android does not have
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
# BouncyCastleProvider loads its algorithm classes by name; everything else is reached from code
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.pqc.jcajce.provider.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-dontwarn javax.security.auth.**
-dontwarn java.lang.management.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn net.lingala.zip4j.**

# Telos PDF tools: PdfBox-Android loads fonts, CMaps and filters by name through reflection
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn org.apache.pdfbox.jbig2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# TvPlayerController compares this activity class name with SettingsDeepLinkContract.
-keepnames class de.mm20.launcher2.ui.settings.SettingsActivity

# JavaMail resolves these enabled transports through META-INF/javamail.providers.
-keep class com.sun.mail.imap.IMAPSSLStore { public <init>(javax.mail.Session, javax.mail.URLName); }
-keep class com.sun.mail.smtp.SMTPTransport { public <init>(javax.mail.Session, javax.mail.URLName); }
-keep class com.sun.mail.smtp.SMTPSSLTransport { public <init>(javax.mail.Session, javax.mail.URLName); }
# Activation loads these MIME content handlers from the library mailcap resource.
-keep class com.sun.mail.handlers.** implements javax.activation.DataContentHandler { public <init>(); public *; }

# Preserve the package used by AnyAscii Unicode resource lookups.
-keepnames class com.anyascii.AnyAscii
