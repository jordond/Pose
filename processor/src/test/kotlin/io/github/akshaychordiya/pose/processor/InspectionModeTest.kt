package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

/**
 * Generated previews must behave like previews in *every* environment, not just
 * Studio's preview renderer. Snapshot runners (Paparazzi, Roborazzi) leave
 * `LocalInspectionMode` at `false`, so a composable branching on it would take
 * its production path — hitting the network for images, etc.
 */
class InspectionModeTest {

    private val target = SourceFile.kotlin(
        "Viewer.kt",
        """
        package sample

        import androidx.compose.runtime.Composable
        import io.github.akshaychordiya.pose.Pose

        @Composable
        @Pose
        fun PhotoViewer(url: String) { }
        """.trimIndent()
    )

    @Test
    fun `inspection mode is provided by default`() {
        val result = CompileHarness.compile(listOf(target))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()
        assertThat(generated).contains("CompositionLocalProvider(LocalInspectionMode provides true)")
        assertThat(generated).contains("PhotoViewer(")
    }

    @Test
    fun `inspection mode can be disabled via pose_provideInspectionMode`() {
        val result = CompileHarness.compile(
            sources = listOf(target),
            options = mapOf("pose.provideInspectionMode" to "false"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()
        assertThat(generated).doesNotContain("LocalInspectionMode")
        assertThat(generated).contains("PhotoViewer(")
    }

    @Test
    fun `inspection mode provider wraps outside the theme`() {
        val theme = SourceFile.kotlin(
            "AppTheme.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun AppTheme(content: @Composable () -> Unit) { content() }
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            sources = listOf(target, theme),
            options = mapOf("pose.themeFqName" to "sample.AppTheme"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()

        // Provider must appear BEFORE the theme so the theme itself can read the local.
        val providerIdx = generated.indexOf("CompositionLocalProvider")
        val themeIdx = generated.indexOf("AppTheme {")
        assertThat(providerIdx).isGreaterThan(-1)
        assertThat(themeIdx).isGreaterThan(providerIdx)
    }

    @Test
    fun `previewWrapperFqName wraps every generated preview`() {
        val wrapper = SourceFile.kotlin(
            "PreviewWrapper.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun PreviewWrapper(content: @Composable () -> Unit) { content() }
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            sources = listOf(target, wrapper),
            options = mapOf("pose.previewWrapperFqName" to "sample.PreviewWrapper"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()
        assertThat(generated).contains("PreviewWrapper {")
    }

    @Test
    fun `wrapper nests inside the inspection-mode provider and outside the theme`() {
        val wrapper = SourceFile.kotlin(
            "PreviewWrapper.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun PreviewWrapper(content: @Composable () -> Unit) { content() }
            """.trimIndent()
        )
        val theme = SourceFile.kotlin(
            "AppTheme.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun AppTheme(content: @Composable () -> Unit) { content() }
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            sources = listOf(target, wrapper, theme),
            options = mapOf(
                "pose.themeFqName" to "sample.AppTheme",
                "pose.previewWrapperFqName" to "sample.PreviewWrapper",
            ),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()

        // Expected order, outermost first: provider → wrapper → theme → target.
        val providerIdx = generated.indexOf("CompositionLocalProvider")
        val wrapperIdx = generated.indexOf("PreviewWrapper {")
        val themeIdx = generated.indexOf("AppTheme {")
        val targetIdx = generated.indexOf("PhotoViewer(")

        assertThat(providerIdx).isGreaterThan(-1)
        assertThat(wrapperIdx).isGreaterThan(providerIdx)
        assertThat(themeIdx).isGreaterThan(wrapperIdx)
        assertThat(targetIdx).isGreaterThan(themeIdx)
    }

    @Test
    fun `PG018 refuses an unresolvable previewWrapperFqName`() {
        val result = CompileHarness.compile(
            sources = listOf(target),
            options = mapOf("pose.previewWrapperFqName" to "sample.DoesNotExist"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG018")
        assertThat(result.messages).contains("sample.DoesNotExist")
    }
}
