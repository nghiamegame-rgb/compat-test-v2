plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nghia.compattestv2"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.nghia.compattestv2"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // targetSdk 34 là yêu cầu của đặc tả, không phải lỗi.
        disable += "OldTargetApi"
    }
}

dependencies {
    // Cố ý không dùng thư viện ngoài (chỉ Kotlin stdlib) để manifest gộp
    // không bị thư viện chèn thêm quyền.
}
