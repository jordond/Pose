package io.github.akshaychordiya.pose

import androidx.compose.runtime.Composable

/**
 * Per-module Pose configuration, declared in Kotlin rather than as Gradle strings.
 *
 * Implement this on an `object` annotated with [PoseSetup]:
 *
 * ```
 * @PoseSetup(generateForAllPublicComposables = true)
 * internal object FeaturePose : PoseConfig {
 *     @Composable
 *     override fun Theme(content: @Composable () -> Unit) {
 *         AppTheme { content() }
 *     }
 * }
 * ```
 *
 * Pose never reads your theme's name — it emits a call to `FeaturePose.Theme { … }`.
 * The reference to `AppTheme` lives in your own source, so the compiler checks it and
 * the IDE refactors it.
 *
 * Scalar settings live on [PoseSetup] rather than here, because KSP can read
 * annotation arguments but not property initializers.
 */
public interface PoseConfig {

    /**
     * Wraps every generated preview in this module. Override to apply your theme.
     *
     * Default is a passthrough — no theme wrapper.
     */
    @Composable
    public fun Theme(content: @Composable () -> Unit) {
        content()
    }

    /**
     * Wraps every generated preview *inside* Pose's `LocalInspectionMode` provider
     * and *outside* [Theme]. Use it to supply `CompositionLocal`s — fake image
     * loaders, no-op analytics, locale providers.
     *
     * Sits inside Pose's provider deliberately: the innermost
     * `CompositionLocalProvider` wins, so you keep the final say over anything
     * Pose also sets.
     *
     * Default is a passthrough.
     */
    @Composable
    public fun Wrapper(content: @Composable () -> Unit) {
        content()
    }
}
