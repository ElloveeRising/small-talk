import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing config is read from keystore.properties (gitignored, never committed).
// If it's absent (e.g. someone else cloned the repo), release builds are simply left
// unsigned and debug builds work as normal — only the maintainer with the keystore can
// cut an official signed release.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}
val hasReleaseKeystore = keystorePropsFile.exists()

android {
    namespace = "com.ryan.smalltalk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ryan.smalltalk"
        minSdk = 35
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // The bundled lint's NonNullableMutableLiveDataDetector crashes with an
    // IncompatibleClassChangeError against this Kotlin analysis API (a known lint
    // tooling bug, unrelated to our code). Skip lint on release builds so it can't
    // block the APK; correctness is unaffected.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Extract LiteRT-LM native libs to disk at install instead of loading them
            // embedded from the APK. Required for locally-installed (non-Play) builds on
            // 16 KB-page devices (Pixel 9 Pro / Android 16) — otherwise libLiteRt.so /
            // liblitertlm_jni.so fail to load and engine creation dies.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // On-device LLM runtime — LiteRT-LM (mirrors Google AI Edge Gallery 0.11.0).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    // HTTP + HTML parsing for the web tools.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")

    // Image loading for chat thumbnails.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines for the async inference pipeline.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
