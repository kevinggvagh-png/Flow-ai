plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flowai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flowai.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
}

kotlin {
    jvmToolchain(17)
}
