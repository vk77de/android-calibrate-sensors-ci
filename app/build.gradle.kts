plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Register the linting tasks for your CI pipeline
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    // Apply the Compose Compiler plugin (Kotlin 2.0+)
    alias(libs.plugins.compose.compiler)
}

// Modern Kotlin compiler configuration (replaces old kotlinOptions)
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
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

fun getGitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
        val result = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (result.isNotEmpty()) result else "unknown"
    } catch (e: Exception) {
        try {
            val process2 = ProcessBuilder("git", "describe", "--always").start()
            val result2 = process2.inputStream.bufferedReader().readText().trim()
            process2.waitFor()
            if (result2.isNotEmpty()) result2 else "unknown"
        } catch (e2: Exception) {
            "unknown"
        }
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

        val gitHash = getGitHash()
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")

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
                "proguard-rules.pro"
            )
        }
    }

    // Turn on Compose and BuildConfig generation flags
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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

    // Jetpack Compose Dependencies (via Version Catalog BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Core Compose Activity Integration
    implementation(libs.androidx.activity.compose)

    // Tooling Debug Suites
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing Dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    implementation("androidx.navigation:navigation-compose:2.8.5")
}
