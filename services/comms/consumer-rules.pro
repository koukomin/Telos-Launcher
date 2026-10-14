
# libtorrent4j: the native library calls swig classes by name (fields, director callbacks), so they must not be shrunk or renamed
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.libtorrent4j.**

# SPDX-FileCopyrightText: 2025 Flow
# SPDX-License-Identifier: GPL-3.0-or-later
# NewPipe/Rhino retention rules adapted from Flow app/proguard-rules.pro.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keepattributes Exceptions,InnerClasses
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
