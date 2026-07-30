package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName

/**
 * Emits placeholder lambda bodies for function-type parameters
 * (`() -> Unit`, `@Composable () -> Unit`, `@Composable RowScope.() -> Unit`, …).
 */
internal object FunctionTypeSynthesizer {

    /** Returns null when the type is not a function type. */
    fun tryEmit(
        decl: KSClassDeclaration,
        type: KSType,
    ): SampleExpr? {
        val fqn = decl.qualifiedName?.asString() ?: return null
        if (!fqn.startsWith("kotlin.Function") &&
            !fqn.startsWith("kotlin.coroutines.SuspendFunction") &&
            !fqn.startsWith("kotlin.reflect.KFunction")
        ) return null

        val isComposable = type.isMarkedComposable()
        val receiverScope = detectReceiverScope(type)
        val arity = extractArity(fqn) ?: (type.arguments.size - 1)

        val code = when {
            isComposable && receiverScope != null -> composableScopeLambda(receiverScope)
            isComposable -> CodeBlock.of(
                "{ %M(text = %S) }",
                MemberName("androidx.compose.material3", "Text"), "Slot content"
            )

            arity == 0 -> CodeBlock.of("{}")
            else -> {
                val underscores = List(arity) { "_" }.joinToString(", ")
                CodeBlock.of("{ %L -> }", underscores)
            }
        }
        return SampleExpr.Emit(code, tier = 2, note = "function-type")
    }

    private fun composableScopeLambda(scope: String): CodeBlock = when (scope) {
        "androidx.compose.foundation.layout.BoxScope" ->
            CodeBlock.of(
                "{ %M(text = %S) }",
                MemberName("androidx.compose.material3", "Text"), "Slot"
            )

        "androidx.compose.foundation.layout.RowScope",
        "androidx.compose.foundation.layout.ColumnScope" ->
            CodeBlock.of(
                "{ %M(text = %S); %M(text = %S) }",
                MemberName("androidx.compose.material3", "Text"), "A",
                MemberName("androidx.compose.material3", "Text"), "B"
            )

        "androidx.compose.foundation.lazy.LazyListScope",
        "androidx.compose.foundation.lazy.grid.LazyGridScope" ->
            CodeBlock.of(
                "{ items(3) { %M(text = %S) } }",
                MemberName("androidx.compose.material3", "Text"), "item"
            )

        else -> CodeBlock.of("{}")
    }

    private fun detectReceiverScope(type: KSType): String? {
        // Kotlin function types with a receiver have the receiver as the first type
        // argument annotated with @ExtensionFunctionType internally; KSP2 surfaces this
        // via KSType.annotations OR via the first type argument. Best-effort detection.
        val ann = type.annotations.firstOrNull {
            it.shortNameOrEmpty() == "ExtensionFunctionType"
        }
        if (ann == null) return null
        return type.arguments.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString()
    }

    private fun extractArity(fqn: String): Int? =
        fqn.removePrefix("kotlin.Function")
            .removePrefix("kotlin.coroutines.SuspendFunction")
            .removePrefix("kotlin.reflect.KFunction")
            .toIntOrNull()
}

internal fun KSType.isMarkedComposable(): Boolean {
    // Belt-and-braces: KSP2 has moved the annotation position between releases.
    return annotations.any { it.shortNameOrEmpty() == "Composable" }
}

internal fun KSAnnotation.shortNameOrEmpty(): String =
    shortName.asString()
