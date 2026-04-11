plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.kid.core.speech"
    compileSdk = 35

    defaultConfig {
        minSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    api(project(":core:common"))

    // Google ML Kit Speech Recognition (on-device, high quality)
    implementation("com.google.mlkit:speech-recognition:16.1.2")
    
    // Google Audio ML Kit (for audio processing)
    implementation("com.google.mlkit:translate:17.0.2")
    
    // Android Audio Framework
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // DataStore (for language preferences)
    implementation(libs.androidx.datastore.preferences)
    
    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // Ktlint
    implementation(libs.ktlint)
    
    // Testing
    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
