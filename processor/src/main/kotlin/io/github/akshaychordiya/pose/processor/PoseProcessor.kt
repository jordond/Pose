package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/** The `@Pose` processor */
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

        val symbols = resolver.getSymbolsWithAnnotation(POSE_FQN)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        val planner = PreviewPlanner(options = options, diagnostics = diagnostics)
        val emitter = PreviewFileEmitter(codeGenerator = codeGenerator, options = options)

        val plans = mutableListOf<PreviewPlan>()
        for (fn in symbols) {
            val ann = fn.annotations.firstOrNull { it.annotationTypeFqn() == POSE_FQN }
                ?: continue

            when (val check = SignatureChecker.check(fn)) {
                is SignatureChecker.Result.Refused -> {
                    diagnostics.hardError(check.code, fn, check.detail)
                    continue
                }

                SignatureChecker.Result.Ok -> Unit
            }

            val annArgs = PreviewAnnotationArgs.parse(ann)
            if (annArgs.previewAnnotationFqns.isEmpty()) {
                diagnostics.warn(DiagnosticCode.PG012, fn, "previews array is empty; skipping.")
                continue
            }

            plans += planner.plan(fn, annArgs)
        }
        emitter.emitAll(plans)
        return emptyList()
    }

    private companion object {
        private const val POSE_FQN = "io.github.akshaychordiya.pose.Pose"
    }
}

private fun KSAnnotation.annotationTypeFqn(): String? {
    return annotationType.resolve().declaration.qualifiedName?.asString()
}
