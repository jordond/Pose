package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Visibility

/**
 * The `@Pose` processor. Drives two symbol streams:
 *
 * 1. **Explicit** — every function carrying `@Pose(...)`. Its arguments (name,
 *    wrapInTheme, previews, providers) feed the planner.
 * 2. **Bulk (opt-in)** — every public top-level `@Composable fun … : Unit` when
 *    `pose.generatePreviewsForAllPublicComposables = true`, excluding ones that
 *    (a) already carry `@Pose`, (b) carry `@PoseIgnore`, or (c) already have a
 *    hand-written `@Preview` on the function itself. Uses default annotation
 *    arguments (light+dark, wrap-in-theme, no providers).
 */
public class PoseProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Options,
    logger: KSPLogger,
) : SymbolProcessor {

    private val diagnostics = Diagnostics(logger, options.strict, options.verboseSkips)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Validate `pose.themeFqName` once per round. A theme is a top-level
        // @Composable function; may also be an invokable class in rare cases.
        options.themeFqName?.let { themeFqn ->
            val fnFound = resolver.getFunctionDeclarationsByName(
                resolver.getKSNameFromString(themeFqn),
                includeTopLevel = true,
            ).any()
            val classFound = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(themeFqn)
            ) != null
            if (!fnFound && !classFound) {
                diagnostics.hardError(
                    DiagnosticCode.PG011, node = null,
                    "themeFqName `$themeFqn` does not resolve to a top-level composable or class on the compile classpath.",
                )
                return emptyList()
            }
        }

        val explicitSymbols = resolver.getSymbolsWithAnnotation(POSE_FQN)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()
        val explicitFqns = explicitSymbols.mapNotNull { it.qualifiedName?.asString() }.toSet()

        val bulkSymbols = if (options.generatePreviewsForAllPublicComposables) {
            resolver.getSymbolsWithAnnotation(COMPOSABLE_FQN)
                .filterIsInstance<KSFunctionDeclaration>()
                .filter { it.containingFile != null }
                .filter { fn -> looksLikeBulkCandidate(fn) }
                .filterNot { fn -> fn.qualifiedName?.asString() in explicitFqns }
                .filterNot { fn -> fn.hasAnnotationByFqn(POSE_IGNORE_FQN) }
                .filterNot { fn -> fn.hasAnnotationByShortName("Preview") }
                .toList()
        } else {
            emptyList()
        }

        val planner = PreviewPlanner(resolver = resolver, options = options, diagnostics = diagnostics)
        val emitter = PreviewFileEmitter(codeGenerator = codeGenerator, options = options)

        val plans = mutableListOf<PreviewPlan>()
        for (fn in explicitSymbols) {
            val ann = fn.annotations.firstOrNull { it.annotationTypeFqn() == POSE_FQN } ?: continue
            plans += process(fn, PreviewAnnotationArgs.parse(ann), planner)
        }
        for (fn in bulkSymbols) {
            plans += process(fn, defaultAnnotationArgs, planner)
        }
        emitter.emitAll(plans)
        return emptyList()
    }

    private fun process(
        fn: KSFunctionDeclaration,
        annArgs: PreviewAnnotationArgs,
        planner: PreviewPlanner,
    ): List<PreviewPlan> = when (val check = SignatureChecker.check(fn)) {
        is SignatureChecker.Result.Refused -> {
            // `refusal` respects `pose.strict` — hard-errors under strict, warns
            // otherwise. Bulk mode auto-coerces strict to false via Options.from.
            diagnostics.refusal(check.code, fn, check.detail)
            emptyList()
        }
        SignatureChecker.Result.Ok -> planner.plan(fn, annArgs)
    }

    /**
     * Cheap pre-filter to keep bulk mode from spamming warnings on shapes that
     * were never plausible @Pose targets. Deep validation still happens in
     * [SignatureChecker] for surviving candidates.
     */
    private fun looksLikeBulkCandidate(fn: KSFunctionDeclaration): Boolean {
        if (fn.getVisibility() != Visibility.PUBLIC) return false
        val returnFqn = fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
        if (returnFqn != "kotlin.Unit") return false
        if (fn.typeParameters.isNotEmpty()) return false
        if (fn.functionKind == FunctionKind.TOP_LEVEL) return true
        val parent = fn.parentDeclaration as? KSClassDeclaration ?: return false
        return parent.classKind == ClassKind.OBJECT
    }

    private companion object {
        private const val POSE_FQN = "io.github.akshaychordiya.pose.Pose"
        private const val POSE_IGNORE_FQN = "io.github.akshaychordiya.pose.PoseIgnore"
        private const val COMPOSABLE_FQN = "androidx.compose.runtime.Composable"

        private val defaultAnnotationArgs = PreviewAnnotationArgs(
            name = "",
            wrapInTheme = true,
            previewAnnotationFqns = emptyList(),
            providers = emptyList(),
        )
    }
}

private fun KSAnnotation.annotationTypeFqn(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()

private fun KSFunctionDeclaration.hasAnnotationByFqn(fqn: String): Boolean =
    annotations.any { it.annotationTypeFqn() == fqn }

private fun KSFunctionDeclaration.hasAnnotationByShortName(shortName: String): Boolean =
    annotations.any { it.shortName.asString() == shortName }
