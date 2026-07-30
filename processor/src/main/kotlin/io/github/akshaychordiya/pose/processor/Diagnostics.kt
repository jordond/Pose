package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

/** Stable, greppable diagnostic codes. Grouped by refusal rule (R*). */
public enum class DiagnosticCode(public val message: String) {
    PG001("no fake-data strategy for parameter"),
    PG002("cycle or depth exceeded during structural synthesis"),
    PG003("parameter type is in the refuse category (ViewModel, Hilt, NavController, etc.)"),
    PG004("composable declares generic type parameters"),
    PG005("composable declares context receivers or parameters"),
    PG006("sealed subtype declared in downstream module is invisible to this compilation"),
    PG007("extension receiver is not in the whitelist"),
    PG008("composable is a member function; must be top-level or in an object"),
    PG009("composable is private"),
    PG010("total preview count exceeds the configured cap"),
    PG011("pose.themeFqName is invalid"),
    PG012("previews array is empty"),
    PG013("hand-written @Preview detected — skipping generation"),
    PG014("@Pose applied to a non-@Composable function"),
    PG015("@Pose applied to a function that does not return Unit"),
    PG017("previews[] entry is not annotated @Preview"),
}

/** Sink for structured diagnostics. */
public class Diagnostics(
    private val logger: KSPLogger,
    private val strict: Boolean,
    private val verboseSkips: Boolean,
) {
    /** Refusal that always fails the build regardless of `strict`. */
    public fun hardError(code: DiagnosticCode, node: KSNode?, detail: String) {
        logger.error("[${code.name}] ${code.message}. $detail", node)
    }

    /**
     * Refusal that respects `strict`. `strict=false` downgrades PG001/PG002/PG010
     * to warnings; other codes remain hard errors even in non-strict mode.
     */
    public fun refusal(code: DiagnosticCode, node: KSNode?, detail: String) {
        val downgradable = code == DiagnosticCode.PG001 ||
            code == DiagnosticCode.PG002 ||
            code == DiagnosticCode.PG010
        if (downgradable && !strict) {
            logger.warn("[${code.name}] ${code.message}. $detail", node)
        } else {
            logger.error("[${code.name}] ${code.message}. $detail", node)
        }
    }

    public fun info(code: DiagnosticCode, node: KSNode?, detail: String = "") {
        if (verboseSkips) {
            logger.info("[${code.name}] ${code.message}. $detail", node)
        }
    }

    public fun warn(code: DiagnosticCode, node: KSNode?, detail: String = "") {
        logger.warn("[${code.name}] ${code.message}. $detail", node)
    }
}
