package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

class BulkOptInTest {

    private val bulkOn = mapOf("pose.generatePreviewsForAllPublicComposables" to "true")

    @Test
    fun `bulk mode generates a preview for a public unannotated composable`() {
        val source = SourceFile.kotlin(
            "Screen.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun Screen(title: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Screen__Preview.kt").readText()
        assertThat(generated).contains("internal fun Screen__Preview()")
        assertThat(generated).contains("title = \"Title\"")
    }

    @Test
    fun `bulk mode is inert without the flag`() {
        val source = SourceFile.kotlin(
            "Screen.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun Screen(title: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFiles.any { it.name == "Screen__Preview.kt" }).isFalse()
    }

    @Test
    fun `bulk mode skips private composables`() {
        val source = SourceFile.kotlin(
            "Priv.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            private fun Priv() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFiles.any { it.name == "Priv__Preview.kt" }).isFalse()
        // Should not warn/error about the private one — the pre-filter drops it silently.
        assertThat(result.messages).doesNotContain("PG009")
    }

    @Test
    fun `bulk mode skips internal composables`() {
        val source = SourceFile.kotlin(
            "Internal.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            internal fun InternalScreen() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFiles.any { it.name == "Internal__Preview.kt" }).isFalse()
    }

    @Test
    fun `bulk mode skips non-Unit returning composables`() {
        val source = SourceFile.kotlin(
            "Str.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun ReturnsString(): String = "x"
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFiles.any { it.name == "Str__Preview.kt" }).isFalse()
    }

    @Test
    fun `bulk mode skips composables annotated PoseIgnore`() {
        val source = SourceFile.kotlin(
            "Ignored.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.PoseIgnore

            @PoseIgnore
            @Composable
            fun DebugOverlay(state: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFiles.any { it.name == "Ignored__Preview.kt" }).isFalse()
    }

    @Test
    fun `bulk mode skips composables that already carry a Preview annotation`() {
        val source = SourceFile.kotlin(
            "HandRolled.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun HandRolled() { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.generatedFiles.any { it.name == "HandRolled__Preview.kt" }).isFalse()
    }

    @Test
    fun `explicit @Pose args are honored when the composable is also bulk-eligible`() {
        val source = SourceFile.kotlin(
            "Explicit.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import io.github.akshaychordiya.pose.Pose

            @Pose(name = "CustomName")
            @Composable
            fun Explicit(title: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Explicit__Preview.kt").readText()
        // Explicit name wins — bulk didn't produce a second `Explicit__Preview`.
        assertThat(generated).contains("internal fun CustomName()")
        val count = "internal fun ".toRegex().findAll(generated).count()
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `bulk mode warns instead of failing when a param has no strategy`() {
        // In bulk mode, strict is auto-coerced to false so unfakeable composables
        // become warnings and the build proceeds.
        val source = SourceFile.kotlin(
            "Vm.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.lifecycle.ViewModel

            class MyViewModel : ViewModel()

            @Composable
            fun ViewModelScreen(vm: MyViewModel) { }
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
            options = bulkOn,
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("PG003")
        // Message surfaces at warning level, not error, so build proceeds.
        assertThat(result.generatedFiles.any { it.name == "Vm__Preview.kt" }).isFalse()
    }

    @Test
    fun `bulk mode auto-skips the theme composable identified by pose_themeFqName`() {
        // In bulk mode, a project's theme composable (say `AppTheme(content: … -> Unit)`)
        // would otherwise get picked up because it's public + top-level + Unit.
        // Previewing it renders an empty scope. Auto-skip when its FQN matches
        // `pose.themeFqName`.
        val theme = SourceFile.kotlin(
            "AppTheme.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun AppTheme(content: @Composable () -> Unit) { content() }
            """.trimIndent()
        )
        val screen = SourceFile.kotlin(
            "Screen.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun Screen(title: String) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(
            sources = listOf(screen, theme),
            options = bulkOn + mapOf("pose.themeFqName" to "sample.AppTheme"),
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // The screen still gets a preview.
        val screenGen = result.generatedFile("Screen__Preview.kt").readText()
        assertThat(screenGen).contains("internal fun Screen__Preview()")
        // The theme does NOT — it was auto-skipped by FQN match.
        assertThat(result.generatedFiles.any { it.name == "AppTheme__Preview.kt" }).isFalse()
    }

    @Test
    fun `bulk mode picks up multiple composables in one file`() {
        val source = SourceFile.kotlin(
            "Multi.kt",
            """
            package sample

            import androidx.compose.runtime.Composable

            @Composable
            fun A(title: String) { }

            @Composable
            fun B(count: Int) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(sources = listOf(source), options = bulkOn)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Multi__Preview.kt").readText()
        assertThat(generated).contains("internal fun A__Preview()")
        assertThat(generated).contains("internal fun B__Preview()")
    }
}
