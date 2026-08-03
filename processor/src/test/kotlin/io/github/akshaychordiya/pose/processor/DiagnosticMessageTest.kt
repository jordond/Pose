package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

/**
 * Locks in the *shape* of refusal messages. Not exhaustive — just enough to
 * catch a regression where someone silently drops the composable name, the
 * concrete fix suggestion, or the docs link.
 */
class DiagnosticMessageTest {

    private val nonStrict = mapOf("pose.strict" to "false")

    @Test
    fun `PG003 message names the composable, the category, and suggests Content split`() {
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
        val viewModelStub = SourceFile.kotlin(
            "VmStub.kt",
            """
            package androidx.lifecycle
            open class ViewModel
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            sources = listOf(source, viewModelStub),
            options = nonStrict,
        )

        val msg = result.messages
        assertThat(msg).contains("PG003")
        assertThat(msg).contains("`Screen`")
        assertThat(msg).contains("ViewModel")
        assertThat(msg).contains("ScreenContent")
        assertThat(msg).contains("#pg003")
    }

    @Test
    fun `PG001 message names the composable, the type, and lists concrete fix options`() {
        val source = SourceFile.kotlin(
            "NoStrategy.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            interface Opaque

            @Composable
            @Pose
            fun Card(state: Opaque) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = nonStrict)

        val msg = result.messages
        assertThat(msg).contains("PG001")
        assertThat(msg).contains("`Card`")
        assertThat(msg).contains("Opaque")
        assertThat(msg).contains("previewSamples")
        assertThat(msg).contains("PoseProvider")
        assertThat(msg).contains("#pg001")
    }

    @Test
    fun `PG004 message shows the type parameter list and suggests a non-generic wrapper`() {
        val source = SourceFile.kotlin(
            "Generic.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun <T> ListScreen(items: List<T>) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source))

        val msg = result.messages
        assertThat(msg).contains("PG004")
        assertThat(msg).contains("`ListScreen<T>`")
        assertThat(msg).contains("ListScreenStringPreview")
        assertThat(msg).contains("#pg004")
    }

    @Test
    fun `PG008 message on member composable suggests moving to top-level`() {
        val source = SourceFile.kotlin(
            "Member.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            class Container {
                @Composable
                @Pose
                fun Inside() { }
            }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source))

        val msg = result.messages
        assertThat(msg).contains("PG008")
        assertThat(msg).contains("`Inside`")
        assertThat(msg).contains("top-level")
        assertThat(msg).contains("#pg008")
    }
}
