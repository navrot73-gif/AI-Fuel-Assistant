# Add project specific ProGuard/R8 rules here.
# Most keep rules for this project live in src/main/keepRules (AGP 8+ convention).
# This file exists mainly so the proguardFiles() reference in build.gradle.kts resolves.

# Keep Room entities & DAOs (annotation-based, but this is a safety net)
-keep class com.navrot.aifuelassistant.data.database.entity.** { *; }
-keep interface com.navrot.aifuelassistant.data.database.dao.** { *; }

# okhttp / okio use reflection in places; suppress harmless warnings
-dontwarn okhttp3.**
-dontwarn okio.**
