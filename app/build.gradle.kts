plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nvlide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nvlide"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Filled from the environment in CI; without it the release APK is built unsigned.
            System.getenv("NVLIDE_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = System.getenv("NVLIDE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("NVLIDE_KEY_ALIAS")
            keyPassword = System.getenv("NVLIDE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("NVLIDE_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
