/*
 * Mias — Local AI Assistant
 * Package: io.mias.app
 * Release: #001 (first public build)
 * This is the first step of the application. Package name may change on future major platform ports.
 */
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.junit5.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "dev.mias.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.mias.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The on-device inference engine (llama.cpp JNI) is built for
        // arm64-v8a only. Restricting all native libs to this ABI here
        // strips ~38 MB of unused x86 / x86_64 / armeabi-v7a binaries
        // pulled in by AndroidX and MediaPipe transitively.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        // 16 KB page size compliance: tell AGP to use uncompressed JNI libs
        // (required for the loader to honour the 16 KB alignment we set in
        // CMake via -Wl,-z,max-page-size=16384). Default since AGP 4.2, made
        // explicit here so a future flip can't silently re-break alignment.
        jniLibs {
            useLegacyPackaging = false
        }
        // Drop noise that libraries ship but the app doesn't need at runtime.
        // Keeps the APK from carrying duplicate licenses, ktlint baselines,
        // and the coroutines debug agent (only useful when attached).
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "META-INF/proguard/**",
                "META-INF/versions/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "**/*.txt",
            )
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // PDF text extraction for the knowledge base (on-device, no network).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:inference"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:thermal"))
    implementation(project(":core:soul"))
    implementation(project(":core:security"))
    implementation(project(":core:ui"))
    implementation(project(":core:model-hub"))
    implementation(project(":core:agent"))
    implementation(project(":core:resilience"))
    implementation(project(":core:evolution"))
    implementation(project(":core:speech"))
    implementation(project(":core:language"))

    // WorkManager + Hilt-Work (for EvolutionWorker)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Unit Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)

    // Instrumented Testing
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.truth)
}
