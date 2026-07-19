import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.navrot.aifuelassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.navrot.aifuelassistant"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    // Секреты берутся только из local.properties и не должны коммититься в Git.
    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

    fun secret(name: String): String =
        localProperties.getProperty(name) ?: ""

    val gigaAuthorizationKey = secret("GIGACHAT_AUTHORIZATION_KEY")
    val gigaClientId = secret("GIGACHAT_CLIENT_ID")
    val gigaClientSecret = secret("GIGACHAT_CLIENT_SECRET")
    val yandexApiKey = secret("YANDEX_API_KEY")
    val yandexFolderId = secret("YANDEX_FOLDER_ID")
    val deepSeekApiKey = secret("DEEPSEEK_API_KEY")
    val qwenApiKey = secret("QWEN_API_KEY")

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "GIGACHAT_AUTHORIZATION_KEY", "\"$gigaAuthorizationKey\"")
            buildConfigField("String", "GIGACHAT_CLIENT_ID", "\"$gigaClientId\"")
            buildConfigField("String", "GIGACHAT_CLIENT_SECRET", "\"$gigaClientSecret\"")
            buildConfigField("String", "YANDEX_API_KEY", "\"$yandexApiKey\"")
            buildConfigField("String", "YANDEX_FOLDER_ID", "\"$yandexFolderId\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekApiKey\"")
            buildConfigField("String", "QWEN_API_KEY", "\"$qwenApiKey\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "GIGACHAT_AUTHORIZATION_KEY", "\"$gigaAuthorizationKey\"")
            buildConfigField("String", "GIGACHAT_CLIENT_ID", "\"$gigaClientId\"")
            buildConfigField("String", "GIGACHAT_CLIENT_SECRET", "\"$gigaClientSecret\"")
            buildConfigField("String", "YANDEX_API_KEY", "\"$yandexApiKey\"")
            buildConfigField("String", "YANDEX_FOLDER_ID", "\"$yandexFolderId\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekApiKey\"")
            buildConfigField("String", "QWEN_API_KEY", "\"$qwenApiKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.17")

    // GPS
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
