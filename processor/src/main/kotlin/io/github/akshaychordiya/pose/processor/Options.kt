package io.github.akshaychordiya.pose.processor

/**
 * Effective processor configuration.
 *
 * Everything that shapes *previews* comes from the module's `@PoseSetup` object,
 * where it's compiler-checked. The handful of `pose.*` KSP options that remain
 * are build-behaviour knobs you might legitimately want to differ between a
 * local build and CI — see [KnownKeys].
 */
public data class Options(
    /** `pose.strict` — whether a refusal fails the build or just warns. */
    val strict: Boolean,
    /** `pose.verboseSkips` — log every skip decision. */
    val verboseSkips: Boolean,
    /** Undocumented. See `PreviewFileEmitter.vogueBanner`. */
    val vogue: Boolean = false,

    // ---- Sourced from @PoseSetup; defaults apply when the module has no config object. ----

    val maxDepth: Int = 8,
    val collectionSize: Int = 2,
    val maxPreviewsPerComposable: Int = 8,
    /**
     * Treat every public top-level `@Composable fun … : Unit` as if it carried
     * `@Pose`. Coerces [strict] to `false` — opting a module in shouldn't let one
     * un-previewable composable fail everyone's build.
     */
    val generatePreviewsForAllPublicComposables: Boolean = false,
    /**
     * Wrap generated previews in `CompositionLocalProvider(LocalInspectionMode provides true)`.
     *
     * Skipped silently when `LocalInspectionMode` isn't on the compile classpath
     * (a module with `compose-runtime` but no `compose-ui`).
     */
    val provideInspectionMode: Boolean = true,
    /**
     * FQN of the module's `@PoseSetup` object, when one exists. The emitter calls
     * `<objectFqn>.Theme { … }` rather than resolving a theme by name — which is
     * what makes the config type-safe, since the developer's reference to their
     * theme lives in their own source where the compiler can check it.
     */
    val setupObjectFqn: String? = null,
    /** Whether that object overrode `PoseConfig.Theme` (vs inheriting the passthrough). */
    val setupOverridesTheme: Boolean = false,
    /** Whether that object overrode `PoseConfig.Wrapper`. */
    val setupOverridesWrapper: Boolean = false,
    /**
     * Whether the default light/dark pair stamps `showBackground = true`.
     *
     * Only consulted when the plan has no explicit preview annotations - once the
     * user supplies their own, those decide everything.
     */
    val showBackground: Boolean = true,
) {
    /** Layers a discovered `@PoseSetup` object over the KSP-sourced options. */
    public fun mergedWith(setup: PoseSetupOverrides): Options = copy(
        // Bulk mode forces non-strict.
        strict = if (setup.generateForAllPublicComposables) false else strict,
        generatePreviewsForAllPublicComposables = setup.generateForAllPublicComposables,
        provideInspectionMode = setup.provideInspectionMode,
        maxPreviewsPerComposable = setup.maxPreviewsPerComposable,
        maxDepth = setup.maxDepth,
        collectionSize = setup.collectionSize,
        setupObjectFqn = setup.objectFqn,
        setupOverridesTheme = setup.overridesTheme,
        setupOverridesWrapper = setup.overridesWrapper,
        showBackground = setup.showBackground,
    )

    /** Flattened view of a `@PoseSetup` object, so [Options] needn't depend on the resolver. */
    public data class PoseSetupOverrides(
        val objectFqn: String,
        val overridesTheme: Boolean,
        val overridesWrapper: Boolean,
        val generateForAllPublicComposables: Boolean,
        val provideInspectionMode: Boolean,
        val maxPreviewsPerComposable: Int,
        val maxDepth: Int,
        val collectionSize: Int,
        val showBackground: Boolean,
    )

    public companion object {
        /**
         * Every `pose.*` key Pose understands — anything else is flagged by PG023.
         *
         * Deliberately short. Preview-shaping config lives on `@PoseSetup` where
         * it's compiler-checked; these three are build-behaviour knobs with no
         * annotation equivalent.
         */
        public val KnownKeys: Set<String> = setOf(
            "pose.strict",
            "pose.verboseSkips",
            // Listed so PG023 doesn't flag it as a typo. Undocumented on purpose.
            "pose.vogue",
        )

        public fun from(raw: Map<String, String>): Options = Options(
            strict = raw["pose.strict"]?.toBooleanStrictOrNull() ?: true,
            verboseSkips = raw["pose.verboseSkips"]?.toBooleanStrictOrNull() ?: false,
            vogue = raw["pose.vogue"]?.toBooleanStrictOrNull() ?: false,
        )
    }
}
