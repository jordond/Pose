plugins {
    alias(libs.plugins.kotlin.jvm)
    // `PoseConfig` declares @Composable members, so this module's bytecode must be
    // Compose-compatible, but we use it compilation level.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    compileOnly(platform(libs.compose.bom))
    compileOnly(libs.compose.runtime)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.akshaychordiya.pose",
        artifactId = "annotations",
        version = project.version.toString(),
    )
    pom {
        name.set("Pose Annotations")
        description.set("Marker annotations (@Pose, @PoseProvider, @PoseIgnore) for the Pose KSP processor. Pure Kotlin JVM, no Compose dependency.")
    }
}
