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

    @Test
    fun `sealed fan-out silently skips private subtypes`() {
        // Real-world case from the Marshmallow pilot: a public sealed hierarchy
        // where one subtype is private-in-file. Structural synth cannot emit a
        // reference to the private subtype from the generated file, so fan-out
        // skips it and only emits previews for the accessible ones.
        val source = SourceFile.kotlin(
            "Sealed.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            sealed class TextResource {
                data class Simple(val text: String) : TextResource()
                private data class Plural(val count: Int, val text: String) : TextResource()
            }

            @Composable
            @Pose
            fun Label(resource: TextResource) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Sealed__Preview.kt").readText()

        // Public subtype makes it into the generated file; private one does not.
        assertThat(generated).contains("TextResource.Simple(")
        assertThat(generated).doesNotContain("TextResource.Plural")
        assertThat(generated).contains("Label__Preview_Simple")
        assertThat(generated).doesNotContain("Label__Preview_Plural")
    }

    @Test
    fun `refuses class with private primary constructor`() {
        val source = SourceFile.kotlin(
            "Ctor.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            class OpaqueState private constructor(val label: String) {
                companion object { fun default() = OpaqueState("x") }
            }

            @Composable
            @Pose
            fun Screen(state: OpaqueState) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("T2-non-public-ctor")
    }

    @Test
    fun `refuses ViewModel parameter with PG003 refuse-category`() {
        val source = SourceFile.kotlin(
            "Vm.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.lifecycle.ViewModel
            import io.github.akshaychordiya.pose.Pose

            class MyViewModel : ViewModel()

            @Composable
            @Pose
            fun Screen(vm: MyViewModel) { }
            """.trimIndent()
        )
        // ViewModel needs to be on the compile classpath — provide a minimal stub.
        val viewModelStub = SourceFile.kotlin(
            "VmStub.kt",
            """
            package androidx.lifecycle
            open class ViewModel
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source, viewModelStub))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("PG003")
        assertThat(result.messages).contains("ViewModel")
    }
}
