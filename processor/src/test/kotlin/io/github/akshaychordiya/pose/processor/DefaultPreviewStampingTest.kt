package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

class DefaultPreviewStampingTest {

    @Test
    fun `empty previews list stamps a light plus dark pair with showBackground true`() {
        val source = SourceFile.kotlin(
            "Bare.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun BarePreview() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Bare__Preview.kt").readText()

        // 0x10 = UI_MODE_NIGHT_NO, 0x20 = UI_MODE_NIGHT_YES → decimal 16 / 32.
        assertThat(generated).contains("name = \"Light\"")
        assertThat(generated).contains("uiMode = 16")
        assertThat(generated).contains("name = \"Dark\"")
        assertThat(generated).contains("uiMode = 32")

        // showBackground = true stamped on both entries.
        val showBackgroundOccurrences = "showBackground = true".toRegex().findAll(generated).count()
        assertThat(showBackgroundOccurrences).isEqualTo(2)
    }

    @Test
    fun `custom previews list replaces the default light plus dark pair`() {
        val source = SourceFile.kotlin(
            "Custom.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewLightDark
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose(previews = [PreviewLightDark::class])
            fun CustomPreview() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Custom__Preview.kt").readText()

        assertThat(generated).contains("@PreviewLightDark")
        // Default light/dark pair with showBackground would leak the literal string —
        // the custom annotation must have replaced it entirely.
        assertThat(generated).doesNotContain("name = \"Light\"")
        assertThat(generated).doesNotContain("showBackground = true")
    }

    @Test
    fun `previews list with multiple annotations stamps all of them`() {
        val source = SourceFile.kotlin(
            "Multi.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewFontScale
            import androidx.compose.ui.tooling.preview.PreviewLightDark
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose(previews = [PreviewLightDark::class, PreviewFontScale::class])
            fun MultiPreview() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Multi__Preview.kt").readText()

        assertThat(generated).contains("@PreviewLightDark")
        assertThat(generated).contains("@PreviewFontScale")
    }
}
