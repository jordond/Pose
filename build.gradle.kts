import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

// Fork coordinates. This branch republishes Pose under `dev.jordond.pose` so internal
// consumers can depend on work that has not landed upstream yet. Override the version
// with `-Ppose.version=…`; scripts/publish-maven-central.sh does exactly that.
allprojects {
    group = "dev.jordond.pose"
    version = project.findProperty("pose.version")?.toString() ?: "0.7.0-jordond.1"

    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)

            // Local installs are never consumed by anyone else, and signing them would
            // demand the GPG key for an ordinary `publishToMavenLocal`.
            if (gradle.startParameter.taskNames.none { it.contains("publishToMavenLocal") }) {
                signAllPublications()
            }

            pom {
                name.set(project.name)
                description.set(
                    "Auto-generates Jetpack Compose @Preview functions via KSP. " +
                        "Fork build of AkshayChordiya/Pose, published from jordond/Pose."
                )
                url.set("https://github.com/jordond/Pose")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("akshaychordiya")
                        name.set("Akshay Chordiya")
                        url.set("https://github.com/AkshayChordiya")
                    }
                    developer {
                        id.set("jordond")
                        name.set("Jordon de Hoog")
                        url.set("https://github.com/jordond")
                    }
                }
                scm {
                    url.set("https://github.com/jordond/Pose")
                    connection.set("scm:git:git://github.com/jordond/Pose.git")
                    developerConnection.set("scm:git:ssh://git@github.com/jordond/Pose.git")
                }
            }
        }
    }
}
