package io.github.akshaychordiya.pose.processor

import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test

class BasicEmissionTest {

    @Test
    fun `all-defaulted composable emits a single preview with no PreviewParameter`() {
        val source = SourceFile.kotlin(
            "Sample.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun MyButton(text: String = "OK", onClick: () -> Unit = {}) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Sample__Preview.kt").readText()
        assertThat(generated).contains("internal fun MyButton__Preview()")
        assertThat(generated).doesNotContain("@PreviewParameter")
        assertThat(generated).contains("MyButton()")
    }

    @Test
    fun `composable with only primitives+lambdas emits no PreviewParameter and structural args`() {
        val source = SourceFile.kotlin(
            "Toolbar.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun Toolbar(title: String, count: Int, onNavClick: () -> Unit) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Toolbar__Preview.kt").readText()
        assertThat(generated).doesNotContain("@PreviewParameter")
        assertThat(generated).contains("title = \"Title\"")
        assertThat(generated).contains("count = 0")
        assertThat(generated).contains("onNavClick = {}")
    }

    @Test
    fun `data class param without companion emits inline value, no PreviewParameter`() {
        val source = SourceFile.kotlin(
            "Screen.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            data class UiState(val title: String, val count: Int)

            @Composable
            @Pose
            fun Screen(state: UiState, onEvent: () -> Unit) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Screen__Preview.kt").readText()
        assertThat(generated).contains("internal fun Screen__Preview()")
        assertThat(generated).doesNotContain("@PreviewParameter")
        assertThat(generated).doesNotContain("PreviewParameterProvider")
        assertThat(generated).contains("state = UiState(")
        assertThat(generated).contains("title = \"Title\"")
        assertThat(generated).contains("count = 0")
    }

    @Test
    fun `enum param emits inline value via entries first`() {
        val source = SourceFile.kotlin(
            "Chip.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            enum class Variant { Primary, Secondary }

            @Composable
            @Pose
            fun Chip(variant: Variant) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Chip__Preview.kt").readText()
        assertThat(generated).doesNotContain("@PreviewParameter")
        assertThat(generated).contains("variant = Variant.entries.first()")
    }
}
