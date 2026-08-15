plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    // kctfork exposes its API as @ExperimentalCompilerApi. Opting in at the source-set
    // level keeps tests from having to annotate every function individually.
    sourceSets.getByName("test").languageSettings {
        optIn("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "processor",
        version = project.version.toString(),
    )
    pom {
        name.set("Pose Processor")
        description.set("Auto-generate your Compose @Previews from @Pose-annotated composables at build time.")
    }
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.compile.testing.ksp)
    testImplementation(libs.ksp.symbol.processing)
    testImplementation(project(":annotations"))
}
