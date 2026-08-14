package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Only three `pose.*` keys survive — everything that shapes previews moved to
 * `@PoseSetup`, where it's compiler-checked. These are build-behaviour knobs you
 * might want to differ between a local build and CI.
 */
class OptionsTest {

    @Test
    fun `defaults when no options passed`() {
        val opt = Options.from(emptyMap())
        assertThat(opt.strict).isTrue()
        assertThat(opt.verboseSkips).isFalse()
        assertThat(opt.vogue).isFalse()
    }

    @Test
    fun `parses the surviving options`() {
        val opt = Options.from(
            mapOf(
                "pose.strict" to "false",
                "pose.verboseSkips" to "true",
            )
        )
        assertThat(opt.strict).isFalse()
        assertThat(opt.verboseSkips).isTrue()
    }

    @Test
    fun `preview-shaping settings fall back to defaults without a setup object`() {
        val opt = Options.from(emptyMap())
        assertThat(opt.maxDepth).isEqualTo(8)
        assertThat(opt.collectionSize).isEqualTo(2)
        assertThat(opt.maxPreviewsPerComposable).isEqualTo(8)
        assertThat(opt.generatePreviewsForAllPublicComposables).isFalse()
        assertThat(opt.provideInspectionMode).isTrue()
        assertThat(opt.setupObjectFqn).isNull()
    }

    @Test
    fun `merging a setup object overrides the defaults`() {
        val merged = Options.from(emptyMap()).mergedWith(
            Options.PoseSetupOverrides(
                objectFqn = "com.example.AppPose",
                overridesTheme = true,
                overridesWrapper = false,
                generateForAllPublicComposables = false,
                provideInspectionMode = false,
                maxPreviewsPerComposable = 16,
                maxDepth = 12,
                collectionSize = 5,
                showBackground = false,
            )
        )
        assertThat(merged.setupObjectFqn).isEqualTo("com.example.AppPose")
        assertThat(merged.setupOverridesTheme).isTrue()
        assertThat(merged.setupOverridesWrapper).isFalse()
        assertThat(merged.provideInspectionMode).isFalse()
        assertThat(merged.maxPreviewsPerComposable).isEqualTo(16)
        assertThat(merged.maxDepth).isEqualTo(12)
        assertThat(merged.collectionSize).isEqualTo(5)
        assertThat(merged.showBackground).isFalse()
    }

    @Test
    fun `bulk mode from the setup object coerces strict to false`() {
        val merged = Options.from(mapOf("pose.strict" to "true")).mergedWith(
            overrides(generateForAll = true)
        )
        assertThat(merged.generatePreviewsForAllPublicComposables).isTrue()
        assertThat(merged.strict).isFalse()
    }

    @Test
    fun `bulk mode off leaves pose_strict alone`() {
        val merged = Options.from(mapOf("pose.strict" to "true")).mergedWith(
            overrides(generateForAll = false)
        )
        assertThat(merged.strict).isTrue()
    }

    private fun overrides(generateForAll: Boolean) = Options.PoseSetupOverrides(
        objectFqn = "com.example.AppPose",
        overridesTheme = false,
        overridesWrapper = false,
        generateForAllPublicComposables = generateForAll,
        provideInspectionMode = true,
        maxPreviewsPerComposable = 8,
        maxDepth = 8,
        collectionSize = 2,
        showBackground = true,
    )
}
