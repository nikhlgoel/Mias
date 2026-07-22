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
// with Kotlin 21 vs Java 8 — inconsistent. Re-align them here, lazily
// (configureEach) to dodge finalize-order races:
//  - :mobile → JVM 21, matching :app/:core:* so it can depend on the core
//    libraries directly (native modules wrap them; no cross-target seams).
//  - :react-native-* (autolinked node_modules libs) → JVM 17, RN's own baseline.
subprojects {
    val target = when {
        path == ":app" -> JavaVersion.VERSION_21 // the RN app module (mobile/android/app)
        path.startsWith(":react-native-") -> JavaVersion.VERSION_17
        else -> null
    }
    if (target != null) {
        val kotlinTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(target.toString())
        // Java target: must go through AGP's finalizeDsl (a raw JavaCompile setting
        // is overridden by AGP back to its 1.8 default). This mirrors exactly what
        // RN's own JdkConfiguratorUtils does for aligned modules.
        plugins.withId("com.android.application") {
            extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
                finalizeDsl {
                    it.compileOptions.sourceCompatibility = target
                    it.compileOptions.targetCompatibility = target
                }
            }
        }
        plugins.withId("com.android.library") {
            extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
                finalizeDsl {
                    it.compileOptions.sourceCompatibility = target
                    it.compileOptions.targetCompatibility = target
                }
            }
        }
        // Kotlin target (lazy — avoids finalize-order races).
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions.jvmTarget.set(kotlinTarget)
        }
    }
}
