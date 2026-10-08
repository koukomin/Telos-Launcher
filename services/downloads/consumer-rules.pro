
# youtubedl-android (only present with -Ptelos.media=true): maps yt-dlp JSON with Jackson through reflection
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.yausername.**
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
