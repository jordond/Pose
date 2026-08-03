plugins {
    alias(libs.plugins.kotlin.jvm)
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
