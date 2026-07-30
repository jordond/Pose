package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

/** Everything the emitter needs to write one generated preview function. */
public data class PreviewPlan(
    val function: KSFunctionDeclaration,
    val annotation: PreviewAnnotationArgs,
    /**
     * Suffix appended to the generated function name. Empty for the default case;
     * `_<SubtypeName>` for sealed fan-out; project-specific for overloads.
     */
    val nameSuffix: String,
    /**
     * The `@PreviewParameter` slot — first domain-shaped parameter that has a
     * developer-provided `companion.previewSamples` sequence. `null` when the
     * value is emitted inline (no `@PreviewParameter`).
     */
    val providerSlot: ProviderSlot?,
    /**
     * Arguments for parameters NOT covered by [providerSlot]. Parameters with defaults
     * are omitted. Order matches the target composable's parameter order.
     */
    val otherArgs: List<CallArg>,
    /** One line per parameter — populates the `Resolution:` header of the generated file. */
    val resolutionNotes: List<ResolutionNote>,
) {
    public data class CallArg(val paramName: String, val expr: CodeBlock)
    public data class ResolutionNote(val paramName: String, val tier: String, val note: String)
}

/**
 * Describes the `@PreviewParameter` slot on the generated preview function AND the
 * accompanying provider class the plugin will emit alongside it.
 *
 * `valuesExpr` is either:
 *   - `com.example.MyType.previewSamples` — when the type's companion exposes
 *     a `val previewSamples: Sequence<T>`, or
 *   - `sequenceOf(v1, v2, …)` — plugin-synthesized values (one per sealed subtype
 *     for sealed types, or one structural value otherwise).
 */
public data class ProviderSlot(
    val paramName: String,
    val paramType: TypeName,
    val providerSimpleName: String,
    val valuesExpr: CodeBlock,
    val source: Source,
) {
    public enum class Source { COMPANION_SAMPLES, STRUCTURAL, SEALED_FAN_OUT }
}

/** Parsed subset of the `@Pose` annotation values. */
public data class PreviewAnnotationArgs(
    val name: String,
    val wrapInTheme: Boolean,
    /** FQNs of every KClass entry in `previews[]`. Empty falls back to `PreviewLightDark`. */
    val previewAnnotationFqns: List<String>,
) {
    public companion object {
        public fun parse(ann: KSAnnotation): PreviewAnnotationArgs {
            val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
            val previews = (args["previews"]?.value as? List<*>).orEmpty().mapNotNull(::toFqn)
            return PreviewAnnotationArgs(
                name = (args["name"]?.value as? String).orEmpty(),
                wrapInTheme = (args["wrapInTheme"]?.value as? Boolean) ?: true,
                previewAnnotationFqns = previews.ifEmpty {
                    listOf("androidx.compose.ui.tooling.preview.PreviewLightDark")
                },
            )
        }

        private fun toFqn(value: Any?): String? = when (value) {
            is KSType -> value.declaration.qualifiedName?.asString()
            is KSClassDeclaration -> value.qualifiedName?.asString()
            else -> null
        }
    }
}
