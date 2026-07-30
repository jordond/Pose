package io.github.akshaychordiya.pose.processor

import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test

class CompanionSamplesTest {

    @Test
    fun `companion previewSamples sequence is used as the provider values source`() {
        val source = SourceFile.kotlin(
            "Login.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            data class LoginUiState(
                val email: String,
                val password: String,
                val isSubmitting: Boolean,
                val error: String?,
            ) {
                companion object {
                    val previewSamples: Sequence<LoginUiState> = sequenceOf(
                        LoginUiState(email = "", password = "", isSubmitting = false, error = null),
                        LoginUiState(email = "a@b.com", password = "hunter", isSubmitting = true, error = null),
                        LoginUiState(email = "invalid", password = "", isSubmitting = false, error = "Invalid"),
                    )
                }
            }

            @Composable
            @Pose
            fun LoginContent(state: LoginUiState, onSubmit: () -> Unit) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Login__Preview.kt").readText()

        // Provider references the companion property, not sequenceOf(...) inline.
        assertThat(generated).contains("internal class LoginContent__Preview_Provider")
        assertThat(generated).contains("LoginUiState.previewSamples")
        assertThat(generated).doesNotContain("sequenceOf(")
        assertThat(generated).contains("@PreviewParameter(LoginContent__Preview_Provider::class)")
    }
}
