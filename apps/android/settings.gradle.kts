pluginManagement {
    repositories {
        // Official Google CDN fallback for networks where dl.google.com is unavailable.
        maven("https://edgedl.me.gvt1.com/android/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Official Google CDN fallback for networks where dl.google.com is unavailable.
        maven("https://edgedl.me.gvt1.com/android/maven2/")
        mavenCentral()
    }
}

rootProject.name = "lulu-android"
include(":app")
