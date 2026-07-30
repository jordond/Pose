package io.github.akshaychordiya.pose.processor

import com.squareup.kotlinpoet.CodeBlock

/** Result of resolving a fake value for a parameter or nested property. */
public sealed interface SampleExpr {
    /** Omit the argument at the call site; caller's default applies. */
    public data object Omit : SampleExpr

    /** Emit this expression at the call site. */
    public data class Emit(val code: CodeBlock, val tier: Int, val note: String) : SampleExpr

    /** Cannot resolve; halt generation for the enclosing composable. */
    public data class Refuse(val reason: RefusalReason) : SampleExpr
}

/** Why the resolver could not produce a value. Feeds the PG-code diagnostic. */
public sealed interface RefusalReason {
    public val paramPath: String

    public data class RefuseCategory(
        override val paramPath: String,
        val typeFqn: String,
        val category: String,
    ) : RefusalReason

    public data class NoStrategy(
        override val paramPath: String,
        val typeFqn: String,
        val triedTiers: List<String>,
    ) : RefusalReason

    public data class Cycle(
        override val paramPath: String,
        val chain: List<String>,
    ) : RefusalReason

    public data class InvisibleSealedSubtype(
        override val paramPath: String,
        val sealedFqn: String,
    ) : RefusalReason
}
