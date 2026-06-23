plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.mias.core.data"
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
    implementation(project(":core:language"))

    // Room (local DB only)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
