-keepattributes SourceFile,LineNumberTable
-dontobfuscate
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
