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

    /** A `@PoseSetup` object with the given body, alongside a theme + wrapper to call. */
    private fun setup(body: String) = SourceFile.kotlin(
        "SamplePose.kt",
        """
        package sample

        import androidx.compose.runtime.Composable
        import io.github.akshaychordiya.pose.PoseConfig
        import io.github.akshaychordiya.pose.PoseSetup

        @Composable
        fun AppTheme(content: @Composable () -> Unit) { content() }

        @Composable
        fun FakeLocals(content: @Composable () -> Unit) { content() }

        $body
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
    fun `inspection mode can be disabled on the setup object`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setup(
                    """
                    @PoseSetup(provideInspectionMode = false)
                    internal object SamplePose : PoseConfig
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()
        assertThat(generated).doesNotContain("LocalInspectionMode")
        assertThat(generated).contains("PhotoViewer(")
    }

    @Test
    fun `inspection mode provider wraps outside the theme`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setup(
                    """
                    @PoseSetup
                    internal object SamplePose : PoseConfig {
                        @Composable
                        override fun Theme(content: @Composable () -> Unit) {
                            AppTheme { content() }
                        }
                    }
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()

        // Provider must come first so the theme itself can read the local.
        val providerIdx = generated.indexOf("CompositionLocalProvider")
        val themeIdx = generated.indexOf("SamplePose.Theme")
        assertThat(providerIdx).isGreaterThan(-1)
        assertThat(themeIdx).isGreaterThan(providerIdx)
    }

    @Test
    fun `Wrapper override wraps every generated preview`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setup(
                    """
                    @PoseSetup
                    internal object SamplePose : PoseConfig {
                        @Composable
                        override fun Wrapper(content: @Composable () -> Unit) {
                            FakeLocals { content() }
                        }
                    }
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Viewer__Preview.kt").readText())
            .contains("SamplePose.Wrapper {")
    }

    @Test
    fun `wrapper nests inside the inspection-mode provider and outside the theme`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setup(
                    """
                    @PoseSetup
                    internal object SamplePose : PoseConfig {
                        @Composable
                        override fun Theme(content: @Composable () -> Unit) {
                            AppTheme { content() }
                        }

                        @Composable
                        override fun Wrapper(content: @Composable () -> Unit) {
                            FakeLocals { content() }
                        }
                    }
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Viewer__Preview.kt").readText()

        // Expected order, outermost first: provider → wrapper → theme → target.
        val providerIdx = generated.indexOf("CompositionLocalProvider")
        val wrapperIdx = generated.indexOf("SamplePose.Wrapper")
        val themeIdx = generated.indexOf("SamplePose.Theme")
        val targetIdx = generated.indexOf("PhotoViewer(")

        assertThat(providerIdx).isGreaterThan(-1)
        assertThat(wrapperIdx).isGreaterThan(providerIdx)
        assertThat(themeIdx).isGreaterThan(wrapperIdx)
        assertThat(targetIdx).isGreaterThan(themeIdx)
    }
}
