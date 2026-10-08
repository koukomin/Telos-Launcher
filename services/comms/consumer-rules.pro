
# libtorrent4j: the native library calls swig classes by name (fields, director callbacks), so they must not be shrunk or renamed
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.libtorrent4j.**
