package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

class ThemeWrappingTest {

    private val themeSource = SourceFile.kotlin(
        "AppTheme.kt",
        """
        package sample

        import androidx.compose.runtime.Composable

        @Composable
        fun AppTheme(content: @Composable () -> Unit) { content() }
        """.trimIndent()
    )

    @Test
    fun `wrapInTheme true plus themeFqName wraps the target call in the theme`() {
        val source = SourceFile.kotlin(
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
        val result = CompileHarness.compile(
            sources = listOf(source, themeSource),
            options = mapOf("pose.themeFqName" to "sample.AppTheme"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Screen__Preview.kt").readText()

        assertThat(generated).contains("AppTheme {")
        assertThat(generated).contains("Screen(")
    }

    @Test
    fun `wrapInTheme false omits the theme wrapper even when themeFqName is set`() {
        val source = SourceFile.kotlin(
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
            sources = listOf(source, themeSource),
            options = mapOf("pose.themeFqName" to "sample.AppTheme"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("SelfThemed__Preview.kt").readText()

        assertThat(generated).doesNotContain("AppTheme")
        assertThat(generated).contains("SelfThemed(")
    }

    @Test
    fun `no themeFqName means no wrapper regardless of wrapInTheme`() {
        val source = SourceFile.kotlin(
            "Plain.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun Plain(message: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Plain__Preview.kt").readText()

        assertThat(generated).doesNotContain("AppTheme")
        assertThat(generated).contains("Plain(")
    }
}
