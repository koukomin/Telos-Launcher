-keep class com.kieronquinn.app.smartspacer.sdk.**  { *; }
-dontwarn com.skydoves.balloon.ArrowPositionRules
-dontwarn com.skydoves.balloon.Balloon$Builder
-dontwarn com.skydoves.balloon.Balloon
-dontwarn com.skydoves.balloon.BalloonAnimation
# Telos PDF tools: PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**

# Compose looks up Icons$AutoMirrored$Rounded by Class.forName during draw.
-keep class androidx.compose.material.icons.** { *; }
-keep class androidx.compose.material.icons.Icons { *; }
-keep class androidx.compose.material.icons.Icons$$** { *; }

# PersistentNavigation resolves only serializable UI NavKey routes by their stored
# class name. Keep route names stable in minified builds; serializer members use
# the Kotlin serialization library's existing consumer rules.
-keepnames class de.mm20.launcher2.ui.** implements androidx.navigation3.runtime.NavKey
