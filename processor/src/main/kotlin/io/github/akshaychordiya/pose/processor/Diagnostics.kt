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
    // PG011 ("pose.themeFqName is invalid") and PG018 ("pose.previewWrapperFqName
    // is invalid") retired in 0.6.0 with the string options they validated —
    // `PoseConfig.Theme` / `.Wrapper` are compiler-checked, so there's nothing left
    // to validate. Codes intentionally not reused so old build logs stay unambiguous.
    // PG012 ("previews array is empty") retired in 0.2.0 — an empty `previews`
    // array became the valid default when Pose started stamping its own
    // light+dark pair. Code intentionally not reused so old build logs stay
    // unambiguous.
    PG013("hand-written @Preview detected — skipping generation"),
    PG014("@Pose applied to a non-@Composable function"),
    PG015("@Pose applied to a function that does not return Unit"),
    PG017("previews[] entry is not annotated @Preview"),
    PG019("more than one @PoseSetup declaration in this module"),
    PG020("@PoseSetup applied to something that is not an object"),
    PG021("@PoseSetup object is not reachable from generated code"),
    PG022("@PoseSetup object does not implement PoseConfig"),
    PG023("unknown pose.* option"),
    PG024("previews generated without a @PoseSetup object — no theme applied");

    /**
     * Stable deep-link into the refusal-catalog docs. Anchors are lowercased
     * enum names — `PG003` → `…#pg003`. Kept next to the code so callers can
     * do `code.docsUrl` instead of hand-stitching strings.
     */
    public val docsUrl: String
        get() = "$DOCS_BASE#${name.lowercase()}"

    public companion object {
        public const val DOCS_BASE: String = "https://github.com/AkshayChordiya/Pose/blob/main/docs/refusals.md"
    }
}

/**
 * Sink for structured diagnostics. Message shape:
 * `[PGxxx] <one-line summary>. <specific detail>`
 *
 * `refusal()` respects `strict`: when `false`, every refusal is demoted to a
 * warning and the composable is skipped. Only [hardError] survives the demote
 * - reserve it for problems that would produce broken code if we tried to
 * proceed (a malformed `@PoseSetup` object, bad `previews[]` entries).
 */
public class Diagnostics(
    private val logger: KSPLogger,
    strict: Boolean,
    private val verboseSkips: Boolean,
) {
    /**
     * Settable because strictness isn't fully known at construction time: a
     * `@PoseSetup(generateForAllPublicComposables = true)` is only discovered
     * once processing starts, and bulk mode coerces strict to `false`.
     */
    public var strict: Boolean = strict
        internal set

    /** Refusal that always fails the build regardless of `strict`. */
    public fun hardError(code: DiagnosticCode, node: KSNode?, detail: String) {
        logger.error("[${code.name}] ${code.message}. $detail", node)
    }

    /**
     * Refusal that respects `strict`. `strict=false` downgrades every refusal
     * to a warning — the composable is skipped but the build proceeds.
     */
    public fun refusal(code: DiagnosticCode, node: KSNode?, detail: String) {
        if (strict) {
            logger.error("[${code.name}] ${code.message}. $detail", node)
        } else {
            logger.warn("[${code.name}] ${code.message}. $detail", node)
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
