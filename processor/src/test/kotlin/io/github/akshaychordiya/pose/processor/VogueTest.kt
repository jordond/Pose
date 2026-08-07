package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

/**
 * The `pose.vogue` easter egg. Tested because an undocumented feature with no
 * coverage is indistinguishable from dead code, and someone would eventually
 * delete it.
 */
class VogueTest {

    private val source = SourceFile.kotlin(
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

    @Test
    fun `banner is absent by default`() {
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Screen__Preview.kt").readText())
            .doesNotContain("pose struck")
    }

    @Test
    fun `banner appears when pose_vogue is set`() {
        val result = CompileHarness.compile(
            sources = listOf(source),
            options = mapOf("pose.vogue" to "true"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Screen__Preview.kt").readText()
        assertThat(generated).contains("1 pose struck")
        assertThat(generated).contains("0 maintained")
        // Still a comment — nothing reaches compiled output.
        assertThat(generated.lineSequence().first { it.contains("pose struck") }.trimStart())
            .startsWith("//")
    }

    @Test
    fun `banner is deterministic across runs`() {
        val opts = mapOf("pose.vogue" to "true")
        val first = CompileHarness.compile(listOf(source), opts)
            .generatedFile("Screen__Preview.kt").readText()
        val second = CompileHarness.compile(listOf(source), opts)
            .generatedFile("Screen__Preview.kt").readText()

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `pose_vogue is not flagged as an unknown option`() {
        val result = CompileHarness.compile(
            sources = listOf(source),
            options = mapOf("pose.vogue" to "true"),
        )

        assertThat(result.messages).doesNotContain("PG023")
    }
}
