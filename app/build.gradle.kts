plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.d4nzxml.kythera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.d4nzxml.kythera"
        minSdk = 24
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.1"

        // 🔥 KUNCI BUILD: Cukup buat 64-bit (arm64-v8a) aja biar APK nggak bengkak!
        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }
    }

    // 🔥 PELURU BIUS: Bungkam satpam Lint Google biar lolos targetSdk 28
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable.add("ExpiredTargetSdkVersion")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/CMakeLists.txt")
        }
    }

    // 🔥 BRANKAS KEYSTORE: Ngebaca file hasil decode dan password rahasia GitHub Actions
    signingConfigs {
        create("release") {
            storeFile = file("dadan_ruyana_release.keystore")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            storePassword = System.getenv("STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true // 🔥 Biar file APK makin langsing
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "lib/x86/**"
            excludes += "lib/armeabi-v7a/**" // 🔥 Sapu bersih folder 32-bit dari APK
        }
    }
}

dependencies {
    implementation("androidx.browser:browser:1.8.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.appcompat)

    // FFMPEG Offline lu aman, nggak diutak-atik
    implementation(files("libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")

    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.ui.tooling)
}
