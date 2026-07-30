package io.github.akshaychordiya.pose.processor

import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test

class RefusalTest {

    @Test
    fun `PG009 refuses private composable`() {
        val source = SourceFile.kotlin(
            "Priv.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            private fun Priv() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG009")
    }

    @Test
    fun `PG004 refuses generic composable`() {
        val source = SourceFile.kotlin(
            "Gen.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun <T> Gen(items: List<T>) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG004")
    }

    @Test
    fun `PG014 refuses non-composable function`() {
        val source = SourceFile.kotlin(
            "NotComp.kt",
            """
            package sample

            import io.github.akshaychordiya.pose.Pose

            @Pose
            fun NotComposable() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG014")
    }

    @Test
    fun `PG015 refuses non-Unit-returning composable`() {
        val source = SourceFile.kotlin(
            "Str.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun ReturnsString(): String = "x"
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG015")
    }
}
