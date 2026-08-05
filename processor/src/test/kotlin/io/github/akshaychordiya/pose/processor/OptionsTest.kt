package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OptionsTest {

    @Test
    fun `defaults when no options passed`() {
        val opt = Options.from(emptyMap())
        assertThat(opt.themeFqName).isNull()
        assertThat(opt.strict).isTrue()
        assertThat(opt.maxDepth).isEqualTo(8)
        assertThat(opt.collectionSize).isEqualTo(2)
        assertThat(opt.maxPreviewsPerComposable).isEqualTo(8)
        assertThat(opt.verboseSkips).isFalse()
    }

    @Test
    fun `parses all options`() {
        val raw = mapOf(
            "pose.themeFqName" to "com.example.MyTheme",
            "pose.strict" to "false",
            "pose.maxDepth" to "12",
            "pose.collectionSize" to "5",
            "pose.maxPreviewsPerComposable" to "16",
            "pose.verboseSkips" to "true",
        )
        val opt = Options.from(raw)
        assertThat(opt.themeFqName).isEqualTo("com.example.MyTheme")
        assertThat(opt.strict).isFalse()
        assertThat(opt.maxDepth).isEqualTo(12)
        assertThat(opt.collectionSize).isEqualTo(5)
        assertThat(opt.maxPreviewsPerComposable).isEqualTo(16)
        assertThat(opt.verboseSkips).isTrue()
    }

    @Test
    fun `blank themeFqName treated as unset`() {
        val opt = Options.from(mapOf("pose.themeFqName" to "   "))
        assertThat(opt.themeFqName).isNull()
    }

    @Test
    fun `bulk opt-in defaults to false`() {
        val opt = Options.from(emptyMap())
        assertThat(opt.generatePreviewsForAllPublicComposables).isFalse()
    }

    @Test
    fun `bulk opt-in on coerces strict to false regardless of pose_strict`() {
        val opt = Options.from(
            mapOf(
                "pose.generatePreviewsForAllPublicComposables" to "true",
                "pose.strict" to "true", // deliberately conflicting — bulk should win.
            )
        )
        assertThat(opt.generatePreviewsForAllPublicComposables).isTrue()
        assertThat(opt.strict).isFalse()
    }

    @Test
    fun `bulk opt-in off leaves pose_strict alone`() {
        val opt = Options.from(mapOf("pose.strict" to "true"))
        assertThat(opt.strict).isTrue()
    }

    @Test
    fun `provideInspectionMode defaults to true`() {
        assertThat(Options.from(emptyMap()).provideInspectionMode).isTrue()
    }

    @Test
    fun `provideInspectionMode can be turned off`() {
        val opt = Options.from(mapOf("pose.provideInspectionMode" to "false"))
        assertThat(opt.provideInspectionMode).isFalse()
    }

    @Test
    fun `previewWrapperFqName defaults to null and blank is treated as unset`() {
        assertThat(Options.from(emptyMap()).previewWrapperFqName).isNull()
        assertThat(Options.from(mapOf("pose.previewWrapperFqName" to "   ")).previewWrapperFqName).isNull()
    }

    @Test
    fun `previewWrapperFqName is parsed`() {
        val opt = Options.from(mapOf("pose.previewWrapperFqName" to "com.example.PreviewWrapper"))
        assertThat(opt.previewWrapperFqName).isEqualTo("com.example.PreviewWrapper")
    }
}
