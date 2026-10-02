import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val secrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        load(secretsFile.inputStream())
    }
}

android {
    namespace = "com.example.wordexplainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wordexplainer"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val groqKey = secrets.getProperty("GROQ_API_KEY", "")
        val geminiKeys = secrets.getProperty("GEMINI_API_KEYS", "")
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
        buildConfigField("String", "GEMINI_API_KEYS", "\"$geminiKeys\"")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ML Kit Text Recognition — bundled model, works 100% offline, no Play Services required
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
