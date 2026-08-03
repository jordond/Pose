package io.github.akshaychordiya.pose.processor

/** Parsed KSP processor options; see the plugin README for the full list. */
public data class Options(
    val themeFqName: String?,
    val strict: Boolean,
    val maxDepth: Int,
    val collectionSize: Int,
    val maxPreviewsPerComposable: Int,
    val verboseSkips: Boolean,
    /**
     * When `true`, every public top-level `@Composable fun … : Unit` in the
     * module is treated as if it carried `@Pose` (with default arguments).
     * A composable can opt out with `@PoseIgnore`.
     *
     * Bulk mode implicitly coerces [strict] to `false` — refusals become
     * warnings so one unpreviewable composable doesn't fail the whole build.
     */
    val generatePreviewsForAllPublicComposables: Boolean,
) {
    public companion object {
        public fun from(raw: Map<String, String>): Options {
            val bulk = raw["pose.generatePreviewsForAllPublicComposables"]?.toBooleanStrictOrNull() ?: false
            val rawStrict = raw["pose.strict"]?.toBooleanStrictOrNull() ?: true
            return Options(
                themeFqName = raw["pose.themeFqName"]?.takeIf(String::isNotBlank),
                // Bulk mode forces non-strict — devs opting into every-composable coverage
                // don't want a single un-fakeable type to fail the build.
                strict = if (bulk) false else rawStrict,
                maxDepth = raw["pose.maxDepth"]?.toIntOrNull() ?: 8,
                collectionSize = raw["pose.collectionSize"]?.toIntOrNull() ?: 2,
                maxPreviewsPerComposable = raw["pose.maxPreviewsPerComposable"]?.toIntOrNull() ?: 8,
                verboseSkips = raw["pose.verboseSkips"]?.toBooleanStrictOrNull() ?: false,
                generatePreviewsForAllPublicComposables = bulk,
            )
        }
    }
}
