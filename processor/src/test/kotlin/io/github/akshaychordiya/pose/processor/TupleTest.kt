package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

/**
 * `Pair` and `Triple` are both listed in `PreviewPlanner.COLLECTION_FQNS`, so
 * neither gets a `PreviewParameterProvider` slot, which means `SampleResolver`
 * has to synthesize both structurally. Their type arguments are known at the use
 * site; the raw declarations are type variables, so falling through to
 * `synthesizeClassLike` can't work for either.
 */
class TupleTest {

    @Test
    fun `Pair parameter synthesizes from its type arguments`() {
        val result = CompileHarness.compile(listOf(tupleSource("range: Pair<Int, Int>")))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Badge__Preview.kt").readText())
            .contains("range = 0 to 0")
    }

    @Test
    fun `Triple parameter synthesizes from its type arguments`() {
        val result = CompileHarness.compile(listOf(tupleSource("rgb: Triple<Int, Int, Int>")))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFile("Badge__Preview.kt").readText())
            .contains("rgb = Triple(0, 0, 0)")
    }

    @Test
    fun `Triple recurses into non-primitive type arguments`() {
        val source = SourceFile.kotlin(
            "Badge.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            data class Tag(val label: String)

            @Composable
            @Pose
            fun Badge(cell: Triple<Int, Tag, List<String>>) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // Each slot resolves through the ordinary ladder, and `.second` names the
        // path so the String inside Tag is titlecased from its own parameter.
        assertThat(result.generatedFile("Badge__Preview.kt").readText())
            .contains("""cell = Triple(0, Tag(label = "Label"), listOf("Sample", "Sample"))""")
    }

    private fun tupleSource(param: String) = SourceFile.kotlin(
        "Badge.kt",
        """
        package sample

        import androidx.compose.runtime.Composable
        import io.github.akshaychordiya.pose.Pose

        @Composable
        @Pose
        fun Badge($param) { }
        """.trimIndent()
    )
}
