package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

/**
 * Spot-checks the T1 FQN-table emitters against the exact literals each entry
 * advertises in [FqnTable]. If someone changes an emitter, this suite catches
 * it — behavior of a table-driven feature deserves table-driven tests.
 */
class FqnTableTest {

    @Test
    fun `Color parameter emits the neutral grey literal`() {
        val generated = compileOne(
            paramSignature = "tint: androidx.compose.ui.graphics.Color",
            stubs = listOf(colorStub),
        )
        assertThat(generated).contains("tint = Color(0xFFCCCCCC.toInt())")
    }

    @Test
    fun `Dp parameter emits 16 dp`() {
        val generated = compileOne(
            paramSignature = "corner: androidx.compose.ui.unit.Dp",
            stubs = listOf(dpStub),
        )
        assertThat(generated).contains("corner = 16.dp")
    }

    @Test
    fun `StateFlow of String emits MutableStateFlow with a synthesized inner value`() {
        val generated = compileOne(
            paramSignature = "titleStream: kotlinx.coroutines.flow.StateFlow<String>",
            stubs = listOf(stateFlowStub),
        )
        assertThat(generated).contains("titleStream = MutableStateFlow(")
        // The inner String is filled by the parameter-name placeholder rule.
        assertThat(generated).contains("MutableStateFlow(\"Inner\")")
    }

    @Test
    fun `Result of Int emits Result_success wrapping the inner sample`() {
        val generated = compileOne(
            paramSignature = "lastRun: kotlin.Result<Int>",
            stubs = emptyList(),
        )
        assertThat(generated).contains("lastRun = Result.success(0)")
    }

    private fun compileOne(paramSignature: String, stubs: List<SourceFile>): String {
        val source = SourceFile.kotlin(
            "Target.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Composable
            @Pose
            fun Target($paramSignature) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source) + stubs)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        return result.generatedFile("Target__Preview.kt").readText()
    }

    // ---- Minimal stubs for the Compose / coroutines FQNs the emitters reference. ----

    private val colorStub = SourceFile.kotlin(
        "ColorStub.kt",
        """
        package androidx.compose.ui.graphics
        class Color(val value: Int)
        """.trimIndent()
    )

    private val dpStub = SourceFile.kotlin(
        "DpStub.kt",
        """
        package androidx.compose.ui.unit
        class Dp(val value: Float)
        val Int.dp: Dp get() = Dp(this.toFloat())
        """.trimIndent()
    )

    private val stateFlowStub = SourceFile.kotlin(
        "StateFlowStub.kt",
        """
        package kotlinx.coroutines.flow

        interface StateFlow<out T> { val value: T }
        interface MutableStateFlow<T> : StateFlow<T>
        @Suppress("FunctionName")
        fun <T> MutableStateFlow(value: T): MutableStateFlow<T> = object : MutableStateFlow<T> {
            override val value: T = value
        }
        fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this
        """.trimIndent()
    )
}
