package io.github.akshaychordiya.pose.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.github.akshaychordiya.pose.Pose
import io.github.akshaychordiya.pose.PoseSample

/**
 * Showcase — supplying your own `PreviewParameterProvider`.
 *
 * Two ways in:
 *  - `@Pose(providers = [...])` — matched to any parameter of the provider's `T`.
 *  - `@PoseSample(...)` on a parameter — for when two share a type. Because it
 *    sits on the parameter, renaming carries it along.
 */
data class Article(val title: String, val body: String)

class ArticleSamples : PreviewParameterProvider<Article> {
    override val values: Sequence<Article> = sequenceOf(
        Article(title = "Hello world", body = "First paragraph"),
        Article(title = "Longer post", body = "Longer body with more text"),
    )
}

class FeaturedArticleSamples : PreviewParameterProvider<Article> {
    override val values: Sequence<Article> = sequenceOf(
        Article(title = "★ Editor's pick",   body = "Curated read"),
    )
}

@Pose(providers = [ArticleSamples::class])
@Composable
fun ArticleCard(
    article: Article,           // ← auto-matched to ArticleSamples by generic type
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(article.title)
            Text(article.body)
        }
    }
}

@Pose
@Composable
fun ArticleComparison(
    @PoseSample(ArticleSamples::class)         left: Article,
    @PoseSample(FeaturedArticleSamples::class) right: Article,
) {
    Row(Modifier.padding(8.dp)) {
        ArticleCard(article = left, onOpen = {}, modifier = Modifier.padding(end = 8.dp))
        ArticleCard(article = right, onOpen = {})
    }
}
