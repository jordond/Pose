package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

class PoseSetupTest {

    private val target = SourceFile.kotlin(
        "Screen.kt",
        """
        package sample

        import androidx.compose.runtime.Composable
        import io.github.akshaychordiya.pose.Pose

        @Composable
        @Pose
        fun Screen(title: String) { }
        """.trimIndent()
    )

    private fun setupObject(body: String) = SourceFile.kotlin(
        "SamplePose.kt",
        """
        package sample

        import androidx.compose.runtime.Composable
        import io.github.akshaychordiya.pose.PoseConfig
        import io.github.akshaychordiya.pose.PoseSetup

        @Composable
        fun AppTheme(content: @Composable () -> Unit) { content() }

        $body
        """.trimIndent()
    )

    @Test
    fun `theme override is emitted as a call on the setup object`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setupObject(
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
        val generated = result.generatedFile("Screen__Preview.kt").readText()
        assertThat(generated).contains("SamplePose.Theme {")
        // The theme's own name never appears — that's the point of the design.
        assertThat(generated).doesNotContain("AppTheme")
    }

    @Test
    fun `no theme wrapper when the setup object inherits the passthrough default`() {
        val result = CompileHarness.compile(
            listOf(target, setupObject("@PoseSetup\ninternal object SamplePose : PoseConfig"))
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Screen__Preview.kt").readText()
        assertThat(generated).doesNotContain("SamplePose.Theme")
        assertThat(generated).contains("Screen(")
    }

    @Test
    fun `theme inherited from a shared interface is still detected as overridden`() {
        // The documented way to share config across modules. `overridesConfigFunction`
        // walks the whole supertype chain, so the override being one level up must
        // still count — otherwise the theme wrapper is silently dropped.
        val result = CompileHarness.compile(
            listOf(
                target,
                setupObject(
                    """
                    interface AppPoseDefaults : PoseConfig {
                        @Composable
                        override fun Theme(content: @Composable () -> Unit) {
                            AppTheme { content() }
                        }
                    }

                    @PoseSetup
                    internal object SamplePose : AppPoseDefaults
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Screen__Preview.kt").readText())
            .contains("SamplePose.Theme {")
    }

    @Test
    fun `theme inherited from a shared abstract class is still detected as overridden`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setupObject(
                    """
                    abstract class AppPoseDefaults : PoseConfig {
                        @Composable
                        override fun Theme(content: @Composable () -> Unit) {
                            AppTheme { content() }
                        }
                    }

                    @PoseSetup
                    internal object SamplePose : AppPoseDefaults()
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Screen__Preview.kt").readText())
            .contains("SamplePose.Theme {")
    }

    @Test
    fun `annotation arguments drive bulk mode`() {
        val bare = SourceFile.kotlin(
            "Bare.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun Unannotated(title: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            listOf(
                bare,
                setupObject(
                    """
                    @PoseSetup(generateForAllPublicComposables = true)
                    internal object SamplePose : PoseConfig
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Bare__Preview.kt").readText())
            .contains("internal fun Unannotated__Preview()")
    }

    @Test
    fun `bulk mode does not preview the setup object's own Theme`() {
        val result = CompileHarness.compile(
            listOf(
                setupObject(
                    """
                    @PoseSetup(generateForAllPublicComposables = true)
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
        val generated = result.generatedFiles.firstOrNull { it.name == "SamplePose__Preview.kt" }
            ?.readText().orEmpty()
        assertThat(generated).doesNotContain("Theme__Preview")
        // AppTheme is a bare top-level composable and is a legitimate bulk target;
        // only the config object's members are excluded.
    }

    @Test
    fun `wrapInTheme false skips the theme for that composable only`() {
        val selfThemed = SourceFile.kotlin(
            "SelfThemed.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose(wrapInTheme = false)
            fun SelfThemed(message: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            listOf(
                target,
                selfThemed,
                setupObject(
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
        assertThat(result.generatedFile("SelfThemed__Preview.kt").readText())
            .doesNotContain("SamplePose.Theme")
        // The opt-out is per-composable — Screen still gets wrapped.
        assertThat(result.generatedFile("Screen__Preview.kt").readText())
            .contains("SamplePose.Theme {")
    }

    @Test
    fun `PG019 rejects two setup objects`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setupObject(
                    """
                    @PoseSetup
                    internal object FirstPose : PoseConfig

                    @PoseSetup
                    internal object SecondPose : PoseConfig
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG019")
        assertThat(result.messages).contains("FirstPose")
        assertThat(result.messages).contains("SecondPose")
    }

    @Test
    fun `PG020 rejects PoseSetup on a class`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setupObject(
                    """
                    @PoseSetup
                    internal class SamplePose : PoseConfig
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG020")
        assertThat(result.messages).contains("must be `object")
    }

    @Test
    fun `PG022 rejects a setup object that does not implement PoseConfig`() {
        val result = CompileHarness.compile(
            listOf(
                target,
                setupObject(
                    """
                    @PoseSetup
                    internal object SamplePose
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG022")
        assertThat(result.messages).contains("PoseConfig")
    }

    @Test
    fun `PG023 warns on a misspelled option key and suggests the right one`() {
        val result = CompileHarness.compile(
            sources = listOf(target),
            options = mapOf("pose.stict" to "false"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("PG023")
        assertThat(result.messages).contains("did you mean `pose.strict`")
    }

    @Test
    fun `PG023 flags options removed in 0_6_0`() {
        // themeFqName / previewWrapperFqName were deleted, not deprecated — anyone
        // still passing them should be told rather than silently ignored.
        val result = CompileHarness.compile(
            sources = listOf(target),
            options = mapOf("pose.themeFqName" to "sample.AppTheme"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("PG023")
        assertThat(result.messages).contains("pose.themeFqName")
    }
}
