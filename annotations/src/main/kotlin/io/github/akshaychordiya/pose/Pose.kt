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
 * fan-out) is set per-module via `@PoseSetup(maxPreviewsPerComposable = …)`,
 * default 8.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class Pose(
    /** Base identifier for the generated function. Empty means `<TargetName>__Preview`. */
    val name: String = "",

    /**
     * Wrap the target call in the module's [PoseConfig.Theme]. Set to `false` when
     * this composable applies its own theme - the opt-out is per-composable, so
     * everything else in the module stays wrapped.
     */
    val wrapInTheme: Boolean = true,

    /**
     * Preview annotations stamped onto the generated function. Each class must be
     * annotated (transitively) with `@Preview`. Compose's `PreviewLightDark`,
     * `PreviewFontScale`, `PreviewScreenSizes` all work; project-local multipreview
     * annotations work with no plugin support needed.
     *
     * When empty (the default), the processor stamps its own light + dark pair
     * with `showBackground = true` on each.
     */
    val previews: Array<KClass<out Annotation>> = [],

    /**
     * `PreviewParameterProvider<T>` classes to draw sample data from. Each is
     * matched to a parameter by its generic type `T`:
     *
     * ```
     * class ArticleSamples : PreviewParameterProvider<Article> {
     *     override val values = sequenceOf(Article("Hello", "…"))
     * }
     *
     * @Pose(providers = [ArticleSamples::class])
     * @Composable
     * fun ArticleCard(article: Article, onOpen: () -> Unit) { … }
     * ```
     *
     * Scoped to this composable - never leaks into others. When two parameters
     * share a type and need different data, annotate them individually with
     * [PoseSample].
     */
    val providers: Array<KClass<*>> = [],
)
