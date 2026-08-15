package io.github.akshaychordiya.pose

import kotlin.reflect.KClass

/**
 * Binds a `PreviewParameterProvider<T>` to *this specific parameter*.
 *
 * Use it when `@Pose(providers = [...])` can't disambiguate — most often because
 * two parameters share a type and want different sample data:
 *
 * ```
 * @Pose
 * @Composable
 * fun ArticleComparison(
 *     @PoseSample(ArticleSamples::class)         left: Article,
 *     @PoseSample(FeaturedArticleSamples::class) right: Article,
 * ) { … }
 * ```
 *
 * Because the binding lives *on* the parameter, renaming `left` carries the
 * annotation with it — there's nothing to fall out of sync. That's the whole
 * reason this exists rather than a `forParam = "left"` string on `@Pose`.
 *
 * Wins over `@Pose(providers = [...])` when both would apply. Emits
 * `<Provider>().values.first()` at the call site, bypassing structural synthesis
 * for that parameter.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class PoseSample(val provider: KClass<*>)
