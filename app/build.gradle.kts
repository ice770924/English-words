plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kkamppagienglish"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kkamppagienglish"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.0"
    }

    signingConfigs {
        create("fixedDebug") {
            storeFile = file("kkamppagi.keystore")
            storePassword = "android123"
            keyAlias = "kkamppagi"
            keyPassword = "android123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
