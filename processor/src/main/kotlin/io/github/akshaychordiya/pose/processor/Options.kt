package io.github.akshaychordiya.pose.processor

/** Parsed KSP processor options; see the plugin README for the full list. */
public data class Options(
    val themeFqName: String?,
    val strict: Boolean,
    val maxDepth: Int,
    val collectionSize: Int,
    val maxPreviewsPerComposable: Int,
    val verboseSkips: Boolean,
) {
    public companion object {
        public fun from(raw: Map<String, String>): Options = Options(
            themeFqName = raw["pose.themeFqName"]?.takeIf(String::isNotBlank),
            strict = raw["pose.strict"]?.toBooleanStrictOrNull() ?: true,
            maxDepth = raw["pose.maxDepth"]?.toIntOrNull() ?: 8,
            collectionSize = raw["pose.collectionSize"]?.toIntOrNull() ?: 2,
            maxPreviewsPerComposable = raw["pose.maxPreviewsPerComposable"]?.toIntOrNull() ?: 8,
            verboseSkips = raw["pose.verboseSkips"]?.toBooleanStrictOrNull() ?: false,
        )
    }
}
