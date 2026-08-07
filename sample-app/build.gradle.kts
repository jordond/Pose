plugins {
    // AGP 9.0+ has built-in Kotlin support — no need to apply kotlin.android explicitly.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.akshaychordiya.pose.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.akshaychordiya.pose.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    // Theme + flags now live in SamplePose.kt as type-safe Kotlin. Nothing needed here.
    // The legacy `arg("pose.themeFqName", "…")` still works as a fallback.
}

dependencies {
    implementation(project(":annotations"))
    kspDebug(project(":processor"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
