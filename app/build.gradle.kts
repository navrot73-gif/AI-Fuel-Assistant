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

// Разрешение значения секрета: local.properties → env variable → пустая строка.
// Никогда не хардкодим токен в build-скрипте (он извлекается из APK через jadx/apktool).
fun secret(name: String): String =
    localProperties.getProperty(name, System.getenv(name) ?: "")

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

        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${secret("DEEPSEEK_API_KEY")}\"")
        buildConfigField("String", "QWEN_API_KEY", "\"${secret("QWEN_API_KEY")}\"")
        buildConfigField("String", "HUGGINGFACE_TOKEN", "\"${secret("HUGGINGFACE_TOKEN")}\"")
        buildConfigField("String", "GIGACHAT_CLIENT_ID", "\"${secret("GIGACHAT_CLIENT_ID")}\"")
        buildConfigField("String", "GIGACHAT_CLIENT_SECRET", "\"${secret("GIGACHAT_CLIENT_SECRET")}\"")
        buildConfigField("String", "GIGACHAT_AUTHORIZATION_KEY", "\"${secret("GIGACHAT_AUTHORIZATION_KEY")}\"")
        buildConfigField("String", "YANDEX_API_KEY", "\"${secret("YANDEX_API_KEY")}\"")
        buildConfigField("String", "YANDEX_FOLDER_ID", "\"${secret("YANDEX_FOLDER_ID")}\"")
        buildConfigField("String", "ORS_API_KEY", "\"${secret("ORS_API_KEY")}\"")
        buildConfigField("String", "PROXY_TOKEN", "\"${secret("PROXY_TOKEN")}\"")
    }

    buildTypes {
        debug {
            // PROXY_TOKEN теперь берётся из local.properties / env (см. defaultConfig).
        }
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
    lint {
        baseline = file("lint-baseline.xml")
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

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Material Icons
    implementation("androidx.compose.material:material-icons-extended")
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

    // Gson for JSON serialization
    implementation(libs.gson)

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