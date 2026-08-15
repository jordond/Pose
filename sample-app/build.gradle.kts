import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // Builds an Android library, not an APK - the runnable app is `:sample-android`.
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "io.github.akshaychordiya.pose.sample"
        compileSdk = 37
        minSdk = 24
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain {
            // KSP writes here; it is not a source root by default.
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")

            dependencies {
                implementation(project(":annotations"))

                implementation(libs.compose.mpp.runtime)
                implementation(libs.compose.mpp.ui)
                implementation(libs.compose.mpp.foundation)
                implementation(libs.compose.mpp.material3)
                implementation(libs.compose.mpp.ui.tooling.preview)
                // `WellKnownTypesCard` takes a StateFlow parameter.
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

dependencies {
    // Only the common metadata compilation is processed. Adding per-target KSP
    // configurations (kspAndroid, kspJvm, …) would re-emit the same previews once per
    // target and fail with duplicate declarations.
    add("kspCommonMainMetadata", project(":processor"))

    // Studio's preview renderer. The KMP Android plugin is single-variant, so there is
    // no `debugImplementation` to hang this off — `androidRuntimeClasspath` is its
    // replacement.
    androidRuntimeClasspath(libs.compose.mpp.ui.tooling)
}

// Every other compilation, and every per-target KSP task, reads the generated sources,
// so all of them must wait for them. Without this, `compileKotlinJvm` races the metadata
// KSP task and sees an empty source dir on a clean build. The per-target KSP tasks need
// naming separately - `KspAATask` is not a `KotlinCompilationTask`.
tasks.matching {
    it.name != "kspCommonMainKotlinMetadata" &&
        (it is KotlinCompilationTask<*> || it.name.startsWith("ksp"))
}.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}
