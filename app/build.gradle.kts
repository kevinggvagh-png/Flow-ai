plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val flowApiUrl = providers.gradleProperty("FLOW_API_URL").orElse("").get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.flowai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flowai.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        buildConfigField("String", "FLOW_API_URL", "\"$flowApiUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}
