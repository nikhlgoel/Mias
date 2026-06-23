plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.mias.core.resilience"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    // Run unit tests on the JUnit Platform. Without this the test task uses the
    // JUnit4 runner, discovers zero JUnit5 tests, and passes vacuously.
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
}
