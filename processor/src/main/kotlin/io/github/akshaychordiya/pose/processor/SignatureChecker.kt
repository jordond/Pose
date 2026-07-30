package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Visibility

/**
 * Validates a `@Pose`-annotated function against refusal rules R1–R9.
 * Returns [Result.Ok] if the function is a valid preview target, or
 * [Result.Refused] with a specific [DiagnosticCode].
 */
public object SignatureChecker {

    public sealed interface Result {
        public object Ok : Result
        public data class Refused(val code: DiagnosticCode, val detail: String) : Result
    }

    public fun check(fn: KSFunctionDeclaration): Result {
        // Must be @Composable
        val isComposable = fn.annotations.any { it.shortNameOrEmpty() == "Composable" }
        if (!isComposable) {
            return Result.Refused(DiagnosticCode.PG014, "@Pose requires @Composable")
        }

        // Must return Unit
        val returnFqn = fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
        if (returnFqn != "kotlin.Unit") {
            return Result.Refused(DiagnosticCode.PG015, "return type must be Unit, was $returnFqn")
        }

        // R8 - no private
        if (fn.getVisibility() == Visibility.PRIVATE || fn.getVisibility() == Visibility.LOCAL) {
            return Result.Refused(DiagnosticCode.PG009, "composable is private")
        }

        // R7 - top-level or member of object
        if (fn.functionKind != FunctionKind.TOP_LEVEL) {
            val parent = fn.parentDeclaration
            val insideObject = parent is com.google.devtools.ksp.symbol.KSClassDeclaration &&
                parent.classKind == com.google.devtools.ksp.symbol.ClassKind.OBJECT
            if (!insideObject) {
                return Result.Refused(DiagnosticCode.PG008, "member composable (kind=${fn.functionKind})")
            }
        }

        // R1 - generic type parameters
        if (fn.typeParameters.isNotEmpty()) {
            return Result.Refused(DiagnosticCode.PG004, "composable declares generic parameters")
        }

        // R2 - context receivers / parameters
        // KSP2's API for context params has been in flux - inspect modifiers as a proxy.
        if (fn.modifiers.any { it.name.equals("CONTEXT", ignoreCase = true) }) {
            return Result.Refused(DiagnosticCode.PG005, "composable declares context parameters")
        }

        // R3 - extension receiver must be in whitelist (or absent)
        val ext = fn.extensionReceiver
        if (ext != null && !ext.isWhitelistedScope()) {
            val fqn = ext.resolve().declaration.qualifiedName?.asString() ?: "<unresolved>"
            return Result.Refused(DiagnosticCode.PG007, "extension receiver $fqn is not whitelisted")
        }

        return Result.Ok
    }

    private val whitelistedScopes = setOf(
        "androidx.compose.foundation.layout.BoxScope",
        "androidx.compose.foundation.layout.RowScope",
        "androidx.compose.foundation.layout.ColumnScope",
        "androidx.compose.foundation.lazy.LazyListScope",
        "androidx.compose.foundation.lazy.grid.LazyGridScope",
        "androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope",
        "androidx.compose.foundation.layout.FlowRowScope",
        "androidx.compose.foundation.layout.FlowColumnScope",
        "androidx.compose.foundation.pager.PagerScope",
    )

    private fun KSTypeReference.isWhitelistedScope(): Boolean =
        resolve().declaration.qualifiedName?.asString() in whitelistedScopes
}
