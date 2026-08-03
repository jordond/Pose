package io.github.akshaychordiya.pose

import kotlin.reflect.KClass

/**
 * A single entry in `@Pose(providers = [...])`. Each entry binds a
 * `PreviewParameterProvider<T>` to one (or more) of the composable's parameters.
 *
 * Two matching modes:
 *
 * **Generic-type match** (default). Pose finds any parameter whose type is `T`
 * from `PreviewParameterProvider<T>`.
 *
 * ```
 * class ArticleSamples : PreviewParameterProvider<Article> {
 *     override val values = sequenceOf(Article(title = "Hello", body = "…"))
 * }
 *
 * @Pose(providers = [PoseProvider(ArticleSamples::class)])
 * @Composable
 * fun ArticleCard(article: Article, onOpen: () -> Unit) { … }
 * ```
 *
 * **Named match** ([forParam]). Required when two parameters share a type and
 * you want distinct sample data for each.
 *
 * ```
 * @Pose(providers = [
 *     PoseProvider(ArticleSamples::class,       forParam = "left"),
 *     PoseProvider(FeaturedArticleSamples::class, forParam = "right"),
 * ])
 * @Composable
 * fun ArticleComparison(left: Article, right: Article) { … }
 * ```
 *
 * At the parameter's call site Pose emits `<Provider>().values.first()` inline,
 * bypassing structural synthesis for that parameter.
 */
@Retention(AnnotationRetention.BINARY)
public annotation class PoseProvider(
    val provider: KClass<*>,

    /**
     * Bind this provider to the parameter with this exact name. Empty (default)
     * means bind by the provider's `PreviewParameterProvider<T>` generic type —
     * Pose picks up any parameter of type `T`.
     *
     * Named binding wins over generic-type binding when both would apply.
     */
    val forParam: String = "",
)
