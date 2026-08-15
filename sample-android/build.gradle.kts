plugins {
    // AGP 9.0+ has built-in Kotlin support — no need to apply kotlin.android explicitly.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// APK entry point only - every composable, the theme and `@PoseSetup` live in `:sample-app`.
android {
    // Must differ from `:sample-app`'s namespace — AGP 9 enforces unique package names.
    namespace = "io.github.akshaychordiya.pose.sample.android"
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

dependencies {
    implementation(project(":sample-app"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
