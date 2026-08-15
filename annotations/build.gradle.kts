import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // `PoseConfig` declares @Composable members, so this module's bytecode must be
    // Compose-compatible, but we use it compilation level.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // compileOnly, the markers must not drag Compose onto a consumer's classpath.
            compileOnly(libs.compose.mpp.runtime)
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = "io.github.akshaychordiya.pose",
        artifactId = "annotations",
        version = project.version.toString(),
    )
    pom {
        name.set("Pose Annotations")
        description.set("Marker annotations (@Pose, @PoseSample, @PoseIgnore) and the PoseConfig/@PoseSetup config surface for the Pose KSP processor. Kotlin Multiplatform, no Compose dependency.")
    }
}
