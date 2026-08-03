package io.github.akshaychordiya.pose

/**
 * Opts a composable out of Pose preview generation when the module has bulk
 * opt-in turned on (`pose.generatePreviewsForAllPublicComposables = true`).
 *
 * Meaningless without bulk mode — with explicit opt-in, just omit `@Pose`.
 *
 * ```
 * @PoseIgnore
 * @Composable
 * fun DebugOverlay(state: DebugState) { … }   // public but unwanted in preview panel
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class PoseIgnore
