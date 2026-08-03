package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Builds [PreviewPlan]s for a target composable.
 *
 * Emission rules:
 *  - **No domain-shaped parameter** → one plan, all args inline.
 *  - **Domain param with `companion.previewSamples`** → one plan with
 *    `@PreviewParameter` referencing a generated provider that delegates to the
 *    developer's sequence.
 *  - **Sealed parameter without a companion sequence** → **N plans**, one per
 *    subtype, each with a `_<SubtypeName>` name suffix and the subtype value inline.
 *    Reads nicely in the preview panel.
 *  - **Non-sealed domain param without a companion sequence** → one plan with the
 *    structural value inline.
 *
 * Refusals are emitted to [Diagnostics] and produce an empty result list.
 */
public class PreviewPlanner(
    private val resolver: Resolver,
    private val options: Options,
    private val diagnostics: Diagnostics,
) {

    private val sampleResolver = SampleResolver(options = options)

    public fun plan(fn: KSFunctionDeclaration, ann: PreviewAnnotationArgs): List<PreviewPlan> {
        val providerSlotParam = findProviderSlotParam(fn, ann.providers) ?:
        // No domain-shaped parameter — inline everything.
        return buildSinglePlan(fn, ann, sealedInline = null, provider = null)?.let(::listOf)
            .orEmpty()

        val paramType = providerSlotParam.type.resolve()
        val decl = paramType.declaration as? KSClassDeclaration
            ?: return buildSinglePlan(fn, ann, sealedInline = null, provider = null)?.let(::listOf)
                .orEmpty()

        // Dev-provided companion sequence wins.
        if (hasCompanionPreviewSamples(decl)) {
            val slot = buildCompanionSlot(providerSlotParam, decl, fn.baseName(ann))
            return buildSinglePlan(fn, ann, sealedInline = null, provider = slot)?.let(::listOf)
                .orEmpty()
        }

        // Sealed without a companion — fan out to N plans, one per subtype.
        if (decl.modifiers.contains(Modifier.SEALED)) {
            return buildSealedFanOutPlans(fn, ann, providerSlotParam, decl)
        }

        // Non-sealed no companion — inline single structural value.
        val paramName = providerSlotParam.name?.asString().orEmpty()
        val structural = when (val r =
            sampleResolver.resolveType(paramType, SampleResolver.Context(paramName))) {
            is SampleExpr.Emit -> r.code
            is SampleExpr.Refuse -> {
                emitRefusalDiagnostic(fn, r.reason)
                return emptyList()
            }

            SampleExpr.Omit -> return emptyList()
        }
        return buildSinglePlan(
            fn = fn,
            ann = ann,
            sealedInline = InlineOverride(providerSlotParam, structural, tierNote = "structural"),
            provider = null,
        )?.let(::listOf).orEmpty()
    }

    private data class InlineOverride(
        val param: KSValueParameter,
        val expr: CodeBlock,
        val tierNote: String,
    )

    /**
     * Build one plan. When [sealedInline] is non-null its parameter is emitted as an
     * inline `otherArgs` entry (no `@PreviewParameter`). When [provider] is non-null
     * that parameter is emitted via `@PreviewParameter`.
     */
    private fun buildSinglePlan(
        fn: KSFunctionDeclaration,
        ann: PreviewAnnotationArgs,
        sealedInline: InlineOverride?,
        provider: ProviderSlot?,
        nameSuffix: String = "",
    ): PreviewPlan? {
        val otherArgs = mutableListOf<PreviewPlan.CallArg>()
        val notes = mutableListOf<PreviewPlan.ResolutionNote>()
        for (param in fn.parameters) {
            val name = param.name?.asString().orEmpty()
            if (provider != null && param.name?.asString() == provider.paramName) {
                notes += PreviewPlan.ResolutionNote(
                    paramName = name,
                    tier = "T1",
                    note = "@PreviewParameter (${provider.source.name.lowercase()})"
                )
                continue
            }
            if (sealedInline != null && param == sealedInline.param) {
                otherArgs += PreviewPlan.CallArg(name, sealedInline.expr)
                notes += PreviewPlan.ResolutionNote(name, "T2", sealedInline.tierNote)
                continue
            }
            // @Pose(providers = [PoseProvider(...)]) — named binding wins,
            // then fall through to generic-type binding.
            val poseProviderMatch = resolveProviderForParam(param, ann.providers)
            if (poseProviderMatch != null) {
                otherArgs += PreviewPlan.CallArg(
                    name,
                    CodeBlock.of("%T().values.first()", fqnToClassName(poseProviderMatch.providerFqn)),
                )
                notes += PreviewPlan.ResolutionNote(name, "T1", "@Pose(${poseProviderMatch.kind})")
                continue
            }
            when (val r = sampleResolver.resolveParameter(name, param.type, param.hasDefault)) {
                SampleExpr.Omit -> notes += PreviewPlan.ResolutionNote(name, "T0", "default")
                is SampleExpr.Emit -> {
                    otherArgs += PreviewPlan.CallArg(name, r.code)
                    notes += PreviewPlan.ResolutionNote(name, "T${r.tier}", r.note)
                }

                is SampleExpr.Refuse -> {
                    emitRefusalDiagnostic(fn, r.reason)
                    return null
                }
            }
        }
        return PreviewPlan(
            function = fn,
            annotation = ann,
            nameSuffix = nameSuffix,
            providerSlot = provider,
            otherArgs = otherArgs,
            resolutionNotes = notes,
        )
    }

    private fun buildSealedFanOutPlans(
        fn: KSFunctionDeclaration,
        ann: PreviewAnnotationArgs,
        param: KSValueParameter,
        sealedDecl: KSClassDeclaration,
    ): List<PreviewPlan> {
        val subtypes = sealedDecl.getSealedSubclassesOrEmpty()
        if (subtypes.isEmpty()) {
            diagnostics.refusal(
                DiagnosticCode.PG006, fn,
                "sealed type `${sealedDecl.qualifiedName?.asString()}` has no visible subclasses.",
            )
            return emptyList()
        }
        val cap = options.maxPreviewsPerComposable
        val sortedSubtypes = subtypes.sortedBy { it.simpleName.asString() }
        if (sortedSubtypes.size > cap) {
            diagnostics.refusal(
                DiagnosticCode.PG010, fn,
                "sealed type has ${sortedSubtypes.size} subtypes, exceeds cap $cap. " +
                    "Add `companion object { val previewSamples: Sequence<${sealedDecl.simpleName.asString()}> = … }` " +
                    "to narrow, or raise pose.maxPreviewsPerComposable.",
            )
            if (options.strict) return emptyList()
        }
        val paramName = param.name?.asString().orEmpty()
        val plans = mutableListOf<PreviewPlan>()
        for (sub in sortedSubtypes.take(cap)) {
            val subType = sub.asStarProjectedType()
            val subExpr = when (val r =
                sampleResolver.resolveType(subType, SampleResolver.Context(paramName))) {
                is SampleExpr.Emit -> r.code
                is SampleExpr.Refuse -> {
                    diagnostics.info(
                        DiagnosticCode.PG001, fn,
                        "sealed subtype ${sub.simpleName.asString()} unresolvable; skipping."
                    )
                    continue
                }

                SampleExpr.Omit -> continue
            }
            plans += buildSinglePlan(
                fn = fn,
                ann = ann,
                sealedInline = InlineOverride(
                    param,
                    subExpr,
                    tierNote = "sealed subtype: ${sub.simpleName.asString()}"
                ),
                provider = null,
                nameSuffix = "_${sub.simpleName.asString()}",
            ) ?: continue
        }
        if (plans.isEmpty()) {
            diagnostics.refusal(
                DiagnosticCode.PG001, fn,
                "no sealed subtypes of `${sealedDecl.qualifiedName?.asString()}` could be synthesized.",
            )
        }
        return plans
    }

    private fun buildCompanionSlot(
        param: KSValueParameter,
        decl: KSClassDeclaration,
        baseName: String,
    ): ProviderSlot {
        val paramName = param.name?.asString().orEmpty()
        val paramType: TypeName = try {
            param.type.resolve().toTypeName()
        } catch (_: Throwable) {
            decl.toClassName()
        }
        return ProviderSlot(
            paramName = paramName,
            paramType = paramType,
            providerSimpleName = "${baseName}_Provider",
            valuesExpr = CodeBlock.of("%T.previewSamples", decl.toClassName()),
            source = ProviderSlot.Source.COMPANION_SAMPLES,
        )
    }

    private fun KSFunctionDeclaration.baseName(ann: PreviewAnnotationArgs): String =
        ann.name.ifEmpty { simpleName.asString() + "__Preview" }

    /**
     * A parameter qualifies for the `@PreviewParameter` slot when its type is
     * "domain-shaped" — enum, sealed, data class, value class, or a class with a
     * public primary constructor. Objects, primitives, function types, and refuse
     * categories are excluded. Params already bound by `@Pose(providers = [...])`
     * are also excluded — an explicit provider makes the param a normal inline
     * arg, not the slot.
     */
    private fun findProviderSlotParam(
        fn: KSFunctionDeclaration,
        poseProviders: List<PoseProviderEntry>,
    ): KSValueParameter? {
        for (param in fn.parameters) {
            if (param.hasDefault) continue
            if (resolveProviderForParam(param, poseProviders) != null) continue
            val decl = param.type.resolve().declaration as? KSClassDeclaration ?: continue
            val fqn = decl.qualifiedName?.asString() ?: continue

            if (FqnTable.RefuseFqns.containsKey(fqn)) continue
            val superFqns = decl.allSuperTypeFqns()
            if (FqnTable.refuseCategoryFor(fqn, superFqns) != null) continue

            if (FqnTable.SimpleEmitters.containsKey(fqn)) continue
            if (FqnTable.GenericEmitters.containsKey(fqn)) continue

            if (fqn.startsWith("kotlin.") && decl.classKind == ClassKind.CLASS && decl.isPrimitiveLike()) continue
            if (fqn.startsWith("kotlin.Function") ||
                fqn.startsWith("kotlin.coroutines.SuspendFunction") ||
                fqn.startsWith("kotlin.reflect.KFunction")
            ) continue
            if (fqn in COLLECTION_FQNS) continue
            if (fqn in TRIVIAL_FQNS) continue

            if (decl.classKind == ClassKind.OBJECT) continue

            val isSealedInterface =
                decl.classKind == ClassKind.INTERFACE && decl.modifiers.contains(Modifier.SEALED)
            if (decl.classKind == ClassKind.INTERFACE && !isSealedInterface) continue

            return param
        }
        return null
    }

    /** True if the type's companion exposes `val previewSamples: Sequence<T>`. */
    private fun hasCompanionPreviewSamples(decl: KSClassDeclaration): Boolean {
        val companion = decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject } ?: return false
        val prop: KSPropertyDeclaration = companion.getAllProperties()
            .firstOrNull { it.simpleName.asString() == "previewSamples" } ?: return false
        val propTypeFqn = prop.type.resolve().declaration.qualifiedName?.asString()
        return propTypeFqn == "kotlin.sequences.Sequence"
    }

    private data class ProviderMatch(val providerFqn: String, val kind: String)

    /**
     * Resolves a `@Pose(providers = [...])` entry for [param]. Order:
     *  1. Named entry (`forParam = "<paramName>"`) wins outright.
     *  2. Unnamed entry whose `PreviewParameterProvider<T>` generic `T` matches
     *     [param]'s type FQN (nullability ignored).
     */
    private fun resolveProviderForParam(
        param: KSValueParameter,
        providers: List<PoseProviderEntry>,
    ): ProviderMatch? {
        if (providers.isEmpty()) return null
        val paramName = param.name?.asString().orEmpty()
        providers.firstOrNull { it.forParam.isNotEmpty() && it.forParam == paramName }
            ?.let { return ProviderMatch(it.providerFqn, "providers.forParam") }

        val paramTypeFqn = param.type.resolve().declaration.qualifiedName?.asString() ?: return null
        for (entry in providers) {
            if (entry.forParam.isNotEmpty()) continue
            val providerDecl = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(entry.providerFqn),
            ) ?: continue
            val ppp = providerDecl.superTypes
                .map { it.resolve() }
                .firstOrNull {
                    it.declaration.qualifiedName?.asString() == PREVIEW_PARAMETER_PROVIDER_FQN
                } ?: continue
            val targetFqn = ppp.arguments.firstOrNull()
                ?.type?.resolve()
                ?.declaration?.qualifiedName?.asString() ?: continue
            if (targetFqn == paramTypeFqn) return ProviderMatch(entry.providerFqn, "providers")
        }
        return null
    }

    private fun fqnToClassName(fqn: String): ClassName {
        val idx = fqn.lastIndexOf('.')
        return ClassName(fqn.substring(0, idx), fqn.substring(idx + 1))
    }

    private fun emitRefusalDiagnostic(fn: KSFunctionDeclaration, reason: RefusalReason) {
        when (reason) {
            is RefusalReason.RefuseCategory -> diagnostics.refusal(
                DiagnosticCode.PG003, fn,
                "parameter `${reason.paramPath}` type `${reason.typeFqn}` is in the ${reason.category} refuse category. " +
                    "Hoist state (Screen(vm) / ScreenContent(state, onEvent))."
            )
            is RefusalReason.NoStrategy -> diagnostics.refusal(
                DiagnosticCode.PG001, fn,
                "parameter `${reason.paramPath}` (type `${reason.typeFqn}`) has no fake strategy. " +
                    "Tried: ${reason.triedTiers.joinToString()}. " +
                    "Fix: hand-write a @Preview for this composable, or PR this type into the plugin's FQN table."
            )
            is RefusalReason.Cycle -> diagnostics.refusal(
                DiagnosticCode.PG002, fn,
                "cycle in structural synthesis at `${reason.paramPath}`: ${reason.chain.joinToString(" -> ")}"
            )
            is RefusalReason.InvisibleSealedSubtype -> diagnostics.refusal(
                DiagnosticCode.PG006, fn,
                "sealed type `${reason.sealedFqn}` has no subclasses visible in this compilation."
            )
        }
    }

    private companion object {
        private const val PREVIEW_PARAMETER_PROVIDER_FQN = "androidx.compose.ui.tooling.preview.PreviewParameterProvider"

        private val COLLECTION_FQNS = setOf(
            "kotlin.collections.List",
            "kotlin.collections.MutableList",
            "kotlin.collections.Collection",
            "kotlin.collections.Set",
            "kotlin.collections.MutableSet",
            "kotlin.collections.Map",
            "kotlin.collections.MutableMap",
            "kotlin.Pair",
            "kotlin.Triple",
        )
        private val TRIVIAL_FQNS = setOf(
            "kotlin.String", "kotlin.CharSequence", "kotlin.Boolean",
            "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long",
            "kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong",
            "kotlin.Float", "kotlin.Double", "kotlin.Char", "kotlin.Unit",
        )
    }
}

private fun KSClassDeclaration.isPrimitiveLike(): Boolean =
    qualifiedName?.asString() in setOf(
        "kotlin.Boolean", "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long",
        "kotlin.Float", "kotlin.Double", "kotlin.Char", "kotlin.String",
    )
