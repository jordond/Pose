package io.github.akshaychordiya.pose.sample

import androidx.compose.runtime.Composable
import io.github.akshaychordiya.pose.PoseConfig
import io.github.akshaychordiya.pose.PoseSetup

/**
 * Showcase - type-safe per-module configuration.
 */
@PoseSetup(
    generateForAllPublicComposables = false,
    provideInspectionMode = true,
)
internal object SamplePose : PoseConfig {

    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        SampleTheme { content() }
    }
}
