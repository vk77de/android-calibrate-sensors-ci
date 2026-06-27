plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Register the linting tasks for your CI pipeline
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

// --- AUTOMATED GIT VERSIONING HELPER ROUTINES ---
fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        val result = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (result.isEmpty()) 1 else result.toInt()
    } catch (e: Exception) {
        1 // Fallback if Git is missing or repository is shallow
    }
}

fun getGitVersionName(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--dirty", "--always").start()
        val result = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (result.isEmpty()) "1.0.0-SNAPSHOT" else result
    } catch (e: Exception) {
        "1.0.0-SNAPSHOT" // Fallback if no tags exist yet
    }
}

android {
    namespace = "com.example.helloworldkotlinandroid"
    compileSdk = 34 // Updated to meet modern Android requirements

    // Define the persistent signing configuration
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.example.helloworldkotlinandroid"
        minSdk = 30
        targetSdk = 34 // Updated to bypass the Play Protect block

        // Dynamically assign version metrics straight from Git metadata
        versionCode = getGitCommitCount()
        versionName = getGitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            // Force the debug build to use your persistent key
            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Core UI and AndroidX Libraries
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")

    // CameraX core library and lifecycle modules
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Testing Dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
