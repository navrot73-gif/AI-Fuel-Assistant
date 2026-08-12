import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${localProperties.getProperty("DEEPSEEK_API_KEY", "")}\"")
        buildConfigField("String", "QWEN_API_KEY", "\"${localProperties.getProperty("QWEN_API_KEY", "")}\"")
        buildConfigField("String", "HUGGINGFACE_TOKEN", "\"${localProperties.getProperty("HUGGINGFACE_TOKEN", "")}\"")
        buildConfigField("String", "GIGACHAT_CLIENT_ID", "\"${localProperties.getProperty("GIGACHAT_CLIENT_ID", "")}\"")
        buildConfigField("String", "GIGACHAT_CLIENT_SECRET", "\"${localProperties.getProperty("GIGACHAT_CLIENT_SECRET", "")}\"")
        buildConfigField("String", "GIGACHAT_AUTHORIZATION_KEY", "\"${localProperties.getProperty("GIGACHAT_AUTHORIZATION_KEY", "")}\"")
        buildConfigField("String", "YANDEX_API_KEY", "\"${localProperties.getProperty("YANDEX_API_KEY", "")}\"")
        buildConfigField("String", "YANDEX_FOLDER_ID", "\"${localProperties.getProperty("YANDEX_FOLDER_ID", "")}\"")
        buildConfigField("String", "ORS_API_KEY", "\"${localProperties.getProperty("ORS_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    kapt {
        arguments { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Image loading
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.okhttp)

    // Location
    implementation(libs.play.services.location)

    // Maps
    implementation(libs.osmdroid)

    // Desugaring
    coreLibraryDesugaring(libs.desugar)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}