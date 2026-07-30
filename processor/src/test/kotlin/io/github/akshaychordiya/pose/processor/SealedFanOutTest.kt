package io.github.akshaychordiya.pose.processor

import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test

class SealedFanOutTest {

    @Test
    fun `sealed without companion emits one named preview per subtype`() {
        val source = SourceFile.kotlin(
            "Login.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            sealed interface LoginState {
                data object Loading : LoginState
                data class Success(val user: String) : LoginState
                data class Error(val message: String) : LoginState
            }

            @Composable
            @Pose
            fun LoginScreen(state: LoginState) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Login__Preview.kt").readText()

        // One preview function per subtype, named after the subtype.
        assertThat(generated).contains("internal fun LoginScreen__Preview_Loading()")
        assertThat(generated).contains("internal fun LoginScreen__Preview_Success()")
        assertThat(generated).contains("internal fun LoginScreen__Preview_Error()")

        // No @PreviewParameter and no Provider class — subtype values are inline.
        assertThat(generated).doesNotContain("@PreviewParameter")
        assertThat(generated).doesNotContain("PreviewParameterProvider")

        // Each subtype's value is inline in its own preview.
        assertThat(generated).contains("LoginState.Loading")
        assertThat(generated).contains("LoginState.Success(")
        assertThat(generated).contains("LoginState.Error(")
    }
}
