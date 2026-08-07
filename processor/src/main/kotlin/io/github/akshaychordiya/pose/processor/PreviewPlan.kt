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
    /**
     * FQNs of every KClass entry in `previews[]`. When empty, the emitter stamps
     * Pose's default pair: `@Preview(uiMode = UI_MODE_NIGHT_NO,  showBackground = true)`
     * and `@Preview(uiMode = UI_MODE_NIGHT_YES, showBackground = true)`.
     * We use `showBackground = true` because the default transparent preview reads
     * as broken against Studio's dark IDE theme.
     */
    val previewAnnotationFqns: List<String>,
    /**
     * FQNs from `@Pose(providers = [...])`. Each is matched to a parameter by the
     * provider's `PreviewParameterProvider<T>` generic type. For per-parameter
     * binding — two parameters of the same type — use `@PoseSample` on the
     * parameter instead; it survives renames.
     */
    val providers: List<String>,
) {
    public companion object {
        public fun parse(ann: KSAnnotation): PreviewAnnotationArgs = PreviewAnnotationArgs(
            name = (ann.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String).orEmpty(),
            wrapInTheme = (ann.arguments.firstOrNull { it.name?.asString() == "wrapInTheme" }?.value as? Boolean) ?: true,
            previewAnnotationFqns = (ann.arguments.firstOrNull { it.name?.asString() == "previews" }?.value as? List<*>)
                .orEmpty()
                .mapNotNull(::toFqn),
            providers = (ann.arguments.firstOrNull { it.name?.asString() == "providers" }?.value as? List<*>)
                .orEmpty()
                .mapNotNull(::toFqn),
        )

        private fun toFqn(value: Any?): String? = when (value) {
            is KSType -> value.declaration.qualifiedName?.asString()
            is KSClassDeclaration -> value.qualifiedName?.asString()
            else -> null
        }
    }
}
