
# libtorrent4j: the native library calls swig classes by name (fields, director callbacks), so they must not be shrunk or renamed
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.libtorrent4j.**
# baresip.c binds JNI functions to these classes and calls BaresipService.uaEvent, messageEvent,
# messageResponse, started and stopped by name
-keep class com.tutpro.baresip.Api { *; }
-keep class com.tutpro.baresip.BaresipService { *; }

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

# WorkManager persists this worker's class name across launcher updates.
-keep class de.mm20.launcher2.comms.media.video.trakt.TraktScrobbleWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
