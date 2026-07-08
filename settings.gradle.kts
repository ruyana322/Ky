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
        mavenCentral()
        // Repo ffmpeg-kit pindah ke sini sejak versi 6.x
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.pkg.github.com/arthenica/ffmpeg-kit") }
    }
}

rootProject.name = "KytheraTools"
include(":app")
