# Add project specific ProGuard/R8 rules here.
# Most keep rules for this project live in src/main/keepRules (AGP 8+ convention).
# This file exists mainly so the proguardFiles() reference in build.gradle.kts resolves.

# Keep Room entities & DAOs (annotation-based, but this is a safety net)
-keep class com.navrot.aifuelassistant.data.database.entity.** { *; }
-keep interface com.navrot.aifuelassistant.data.database.dao.** { *; }

# Hilt
-keepclasseswithmembers class * {
    @dagger.hilt.android.HiltAndroidApp <init>(...);
}
-keep class dagger.hilt.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
