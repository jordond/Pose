package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Visibility

/**
 * Validates a `@Pose`-annotated function against refusal rules R1–R9.
 * Returns [Result.Ok] if the function is a valid preview target, or
 * [Result.Refused] with a specific [DiagnosticCode] and a message that names
 * the composable and points at a concrete fix.
 */
public object SignatureChecker {

    public sealed interface Result {
        public object Ok : Result
        public data class Refused(val code: DiagnosticCode, val detail: String) : Result
    }

    public fun check(fn: KSFunctionDeclaration): Result {
        val simpleName = fn.simpleName.asString()

        // Must be @Composable
        val isComposable = fn.annotations.any { it.shortNameOrEmpty() == "Composable" }
        if (!isComposable) {
            return Result.Refused(
                DiagnosticCode.PG014,
                "`$simpleName` isn't @Composable — @Pose only applies to composable functions. " +
                    "Docs: ${DiagnosticCode.PG014.docsUrl}",
            )
        }

        // Must return Unit
        val returnFqn = fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
        if (returnFqn != "kotlin.Unit") {
            return Result.Refused(
                DiagnosticCode.PG015,
                "`$simpleName` returns `$returnFqn` — @Pose only applies to composables that return Unit. " +
                    "Docs: ${DiagnosticCode.PG015.docsUrl}",
            )
        }

        // R8 - no private
        if (fn.getVisibility() == Visibility.PRIVATE || fn.getVisibility() == Visibility.LOCAL) {
            return Result.Refused(
                DiagnosticCode.PG009,
                "`$simpleName` is private — private composables aren't previewable. " +
                    "Docs: ${DiagnosticCode.PG009.docsUrl}",
            )
        }

        // R7 - top-level or member of object
        if (fn.functionKind != FunctionKind.TOP_LEVEL) {
            val parent = fn.parentDeclaration
            val insideObject = parent is com.google.devtools.ksp.symbol.KSClassDeclaration &&
                parent.classKind == com.google.devtools.ksp.symbol.ClassKind.OBJECT
            if (!insideObject) {
                return Result.Refused(
                    DiagnosticCode.PG008,
                    "`$simpleName` is a member function (kind=${fn.functionKind}) — must be top-level or in an object. " +
                        "Fix: move to top-level in the file, or into a top-level `object` container. " +
                        "Docs: ${DiagnosticCode.PG008.docsUrl}",
                )
            }
        }

        // R1 - generic type parameters
        if (fn.typeParameters.isNotEmpty()) {
            return Result.Refused(
                DiagnosticCode.PG004,
                "`$simpleName<${fn.typeParameters.joinToString { it.name.asString() }}>` — generic composables can't be previewed (Pose can't pick a concrete type). " +
                    "Fix: create a non-generic wrapper — `@Pose @Composable fun ${simpleName}StringPreview() = $simpleName<String>(...)`. " +
                    "Docs: ${DiagnosticCode.PG004.docsUrl}",
            )
        }

        // R2 - context receivers / parameters
        if (fn.modifiers.any { it.name.equals("CONTEXT", ignoreCase = true) }) {
            return Result.Refused(
                DiagnosticCode.PG005,
                "`$simpleName` declares context parameters — not supported by Pose yet. " +
                    "Fix: move the context'd work into a helper the composable calls at runtime. " +
                    "Docs: ${DiagnosticCode.PG005.docsUrl}",
            )
        }

        // R3 - extension receiver must be in whitelist (or absent)
        val ext = fn.extensionReceiver
        if (ext != null && !ext.isWhitelistedScope()) {
            val fqn = ext.resolve().declaration.qualifiedName?.asString() ?: "<unresolved>"
            return Result.Refused(
                DiagnosticCode.PG007,
                "`$simpleName` has extension receiver `$fqn` — not in Pose's preview-scope whitelist. " +
                    "Fix: hand-write a @Preview that supplies the receiver, or open a PR to add the scope to the whitelist. " +
                    "Docs: ${DiagnosticCode.PG007.docsUrl}",
            )
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
