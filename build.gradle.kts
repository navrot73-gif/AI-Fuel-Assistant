plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    // KSP должен быть объявлен в root scope вместе с Hilt, иначе
    // Hilt Gradle Plugin не найдёт KSP task class (google/dagger#3965).
    alias(libs.plugins.ksp) apply false
}