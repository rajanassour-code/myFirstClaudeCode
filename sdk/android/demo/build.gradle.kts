plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rajanassour.demobank"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.rajanassour.demobank"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Your Live Video Assist server (the same one the operator console runs on)
        buildConfigField("String", "LIVE_ASSIST_SERVER", "\"https://video.rajanassour.com\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":liveassist"))
}
