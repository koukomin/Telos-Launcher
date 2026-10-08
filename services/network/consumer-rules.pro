# Firestack (Rethink DNS network engine, Go). The Go runtime (gomobile) finds these classes by name
# through JNI and calls their methods and fields from native code, so none of it may be renamed,
# removed or shrunk. The AAR ships the same two rules; they are repeated here on purpose so that a
# change in the AAR can never silently break release builds.
-keep class go.** { *; }
-keep class com.celzero.firestack.** { *; }
-keepclassmembers class com.celzero.firestack.** { *; }
-dontwarn go.**
-dontwarn com.celzero.firestack.**

# Telos Network: the bridge that firestack calls back into (implements com.celzero.firestack.intra.Bridge)
# is called from native code as well.
-keep class de.mm20.launcher2.network.vpn.FlowBridge { *; }
-keep class de.mm20.launcher2.network.vpn.TelosVpnService { *; }
