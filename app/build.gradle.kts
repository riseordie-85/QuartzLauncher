plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tim.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tim.launcher"
        minSdk = 33 // Android 13+ (AGSL shaders for gradients and glass later on)
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-phase0"
    }

    buildTypes {
        release {
            // Phase 0 is a test build; shrinking comes back for real releases
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
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Fold state and hinge position
    implementation("androidx.window:window:1.3.0")
}
