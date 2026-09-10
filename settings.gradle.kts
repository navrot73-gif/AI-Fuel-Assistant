pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = java.net.URI.create("https://repo1.maven.org/maven2") }
        mavenCentral()
    }
}

rootProject.name = "AI Fuel Assistant"
include(":app")