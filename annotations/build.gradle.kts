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
}
