package io.github.akshaychordiya.pose

import kotlin.reflect.KClass

/**
 * Marks a `@Composable` function for automatic `@Preview` generation.
 *
 * The processor scans annotated functions at build time and emits a matching
 * ```
 * @Preview @Composable fun <TargetName>__Preview()
 * ```
 *
 * into
 * `build/generated/ksp/<variant>/kotlin/`, synthesizing plausible values for every
 * parameter. Generated files are never checked in and regenerate on every build.
 *
 * ```
 * @Pose
 * @Composable
 * fun LoginContent(state: LoginUiState, onSubmit: () -> Unit) { /* … */ }
 * ```
 *
 * Applied only to `@Composable` functions returning `Unit`. Function must be
 * `public` or `internal`, top-level or a member of an `object`. Composables with
 * generic type parameters, context receivers, `ViewModel`/Hilt/`NavController`
 * parameters, or private visibility are refused with a diagnostic.
 *
 * The cap on the number of previews emitted per composable (relevant for sealed
 * fan-out) is set project-wide via the `pose.maxPreviewsPerComposable`
 * KSP option (default 8).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class Pose(
    /** Base identifier for the generated function. Empty means `<TargetName>__Preview`. */
    val name: String = "",

    /**
     * Wrap the target call in the composable resolved from the `pose.themeFqName`
     * KSP option. Set to `false` when the target composable applies its own theme.
     */
    val wrapInTheme: Boolean = true,

    /**
     * Preview annotations stamped onto the generated function. Each class must be
     * annotated (transitively) with `@Preview`. Compose's `PreviewLightDark`,
     * `PreviewFontScale`, `PreviewScreenSizes` all work; project-local multipreview
     * annotations work with no plugin support needed.
     *
     * When empty (the default), the processor stamps `PreviewLightDark`.
     */
    val previews: Array<KClass<out Annotation>> = [],
)
