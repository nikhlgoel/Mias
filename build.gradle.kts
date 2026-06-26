plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.junit5.android) apply false
    // React Native root-project plugin (resolved from the included gradle-plugin
    // build wired in settings.gradle.kts). Coordinates the RN build.
    id("com.facebook.react.rootproject")
}

// RN's auto JDK-17 alignment is globally disabled (gradle.properties) so it can't
// touch our JVM-21 :app/:core:* (which it would otherwise try to force to 17 after
// they've finalized, failing with "languageVersion is final"). Consequence: the RN
// app + its autolinked node_modules libraries lose RN's alignment too and end up
// with Kotlin 21 vs Java 8 — inconsistent. Re-align ONLY those RN modules to a
// consistent JDK 17 here, lazily (configureEach) to dodge finalize-order races.
// :app/:core keep their own 21; R1 reaches :core from :mobile via a JVM-21 bridge
// library (no cross-target inlining out of this 17 module).
subprojects {
    if (path == ":mobile" || path.startsWith(":react-native-")) {
        // Java target: must go through AGP's finalizeDsl (a raw JavaCompile setting
        // is overridden by AGP back to its 1.8 default). This mirrors exactly what
        // RN's own JdkConfiguratorUtils does for aligned modules.
        plugins.withId("com.android.application") {
            extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
                finalizeDsl {
                    it.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
                    it.compileOptions.targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
        plugins.withId("com.android.library") {
            extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
                finalizeDsl {
                    it.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
                    it.compileOptions.targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
        // Kotlin target (lazy — avoids finalize-order races).
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
