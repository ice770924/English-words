plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kkamppagienglish"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kkamppagienglish"
        // 2020년 출시된 Android 11 (API 30) 이상 모든 기기에서 작동
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // 오프라인 온디바이스 영어 텍스트 인식 (카메라 사진 영단어 스캔용)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
