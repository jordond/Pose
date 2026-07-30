package io.github.akshaychordiya.pose.processor.testing

import io.github.akshaychordiya.pose.processor.PoseProcessorProvider
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import java.io.File

/**
 * Compiles a set of source files with our processor applied via
 * `dev.zacsweers.kctfork` (a KSP2-aware fork of `kotlin-compile-testing`).
 */
internal object CompileHarness {

    data class Result(
        val exitCode: KotlinCompilation.ExitCode,
        val messages: String,
        val generatedFiles: List<File>,
    ) {
        fun generatedFile(name: String): File =
            generatedFiles.firstOrNull { it.name == name }
                ?: error("Generated file `$name` not found. Found: ${generatedFiles.map { it.name }}")
    }

    fun compile(
        sources: List<SourceFile>,
        options: Map<String, String> = emptyMap(),
    ): Result {
        val compilation = KotlinCompilation().apply {
            this.sources = sources + STUBS
            this.inheritClassPath = true
            this.messageOutputStream = System.out
            useKsp2()
            this.symbolProcessorProviders = mutableListOf<SymbolProcessorProvider>(PoseProcessorProvider())
            this.kspWithCompilation = true
            this.kspProcessorOptions = (this.kspProcessorOptions + options).toMutableMap()
        }
        val result = compilation.compile()
        val generatedDir = compilation.kspSourcesDir
        val generated = generatedDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        return Result(result.exitCode, result.messages, generated)
    }

    /** Minimal Compose annotation stubs — the processor only inspects annotation names. */
    private val STUBS = listOf(
        SourceFile.kotlin(
            "ComposeStubs.kt",
            """
            @file:Suppress("unused")

            package androidx.compose.runtime
            annotation class Composable
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "PreviewStubs.kt",
            """
            @file:Suppress("unused")

            package androidx.compose.ui.tooling.preview

            import kotlin.reflect.KClass

            @Repeatable
            annotation class Preview(val name: String = "")
            @Preview @Preview annotation class PreviewLightDark
            @Preview @Preview @Preview annotation class PreviewFontScale
            annotation class PreviewParameter(
                val provider: KClass<out PreviewParameterProvider<*>>,
                val limit: Int = Int.MAX_VALUE,
            )
            interface PreviewParameterProvider<T> {
                val values: Sequence<T>
                val count: Int get() = values.count()
            }
            """.trimIndent()
        ),
    )
}
