import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // KSP заменяет kapt для Room и Hilt (с Hilt 2.51+).
    // KSP работает в ~2× быстрее kapt, т.к. не запускает отдельный stub generation.
    alias(libs.plugins.ksp)
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

fun proxyToken(): String =
    localProperties.getProperty("PROXY_TOKEN", System.getenv("PROXY_TOKEN") ?: "")

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
        val token = proxyToken()
        require(token.isNotBlank()) {
            "PROXY_TOKEN is required. Set it in local.properties or PROXY_TOKEN env variable."
        }
        buildConfigField("String", "PROXY_TOKEN", "\"$token\"")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"${secret("DEEPSEEK_API_KEY")}\"")
            buildConfigField("String", "QWEN_API_KEY", "\"${secret("QWEN_API_KEY")}\"")
            buildConfigField("String", "HUGGINGFACE_TOKEN", "\"${secret("HUGGINGFACE_TOKEN")}\"")
            buildConfigField("String", "GIGACHAT_CLIENT_ID", "\"${secret("GIGACHAT_CLIENT_ID")}\"")
            buildConfigField("String", "GIGACHAT_CLIENT_SECRET", "\"${secret("GIGACHAT_CLIENT_SECRET")}\"")
            buildConfigField("String", "GIGACHAT_AUTHORIZATION_KEY", "\"${secret("GIGACHAT_AUTHORIZATION_KEY")}\"")
            buildConfigField("String", "YANDEX_API_KEY", "\"${secret("YANDEX_API_KEY")}\"")
            buildConfigField("String", "YANDEX_FOLDER_ID", "\"${secret("YANDEX_FOLDER_ID")}\"")
            buildConfigField("String", "ORS_API_KEY", "\"${secret("ORS_API_KEY")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"\"")
            buildConfigField("String", "QWEN_API_KEY", "\"\"")
            buildConfigField("String", "HUGGINGFACE_TOKEN", "\"\"")
            buildConfigField("String", "GIGACHAT_CLIENT_ID", "\"\"")
            buildConfigField("String", "GIGACHAT_CLIENT_SECRET", "\"\"")
            buildConfigField("String", "GIGACHAT_AUTHORIZATION_KEY", "\"\"")
            buildConfigField("String", "YANDEX_API_KEY", "\"\"")
            buildConfigField("String", "YANDEX_FOLDER_ID", "\"\"")
            buildConfigField("String", "ORS_API_KEY", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    // KSP: передаём аргументы для Room (schema location) через ksp block.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    // Robolectric нужны ресурсы и assets из main-сурссета для эмуляции Context.
    // Без этого RobolectricTestRunner не найдёт assets/stations.json.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Добавить ЭТУ строку для исправления проблемы с кириллицей
                it.systemProperty("file.encoding", "UTF-8")
            }
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
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
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Gson for JSON serialization
    implementation(libs.gson)

    // Logging
    implementation(libs.timber)

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
    // Robolectric позволяет запускать Android-зависимый код (Context, SharedPreferences,
    // assets) в unit-тестах на JVM без эмулятора. Используется в GasStationRepositoryTest.
    testImplementation(libs.robolectric)
    // androidx.test.core нужен для ApplicationProvider.getApplicationContext()
    // (используется Robolectric-тестами для получения контекста с доступом к assets).
    testImplementation(libs.androidx.test.core)
    // MockWebServer — эмуляция HTTP-ответов для тестирования OkHttp-клиента
    // (BenzonavtProvider, loadFromRemote в GasStationRepository).
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}