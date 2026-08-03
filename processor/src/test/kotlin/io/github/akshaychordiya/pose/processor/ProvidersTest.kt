package io.github.akshaychordiya.pose.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.akshaychordiya.pose.processor.testing.CompileHarness
import org.junit.Test

class ProvidersTest {

    @Test
    fun `providers matches a non-slot parameter by the provider's generic type`() {
        val source = SourceFile.kotlin(
            "Card.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewParameterProvider
            import io.github.akshaychordiya.pose.Pose
            import io.github.akshaychordiya.pose.PoseProvider

            data class Article(val title: String, val body: String)

            class ArticleSamples : PreviewParameterProvider<Article> {
                override val values: Sequence<Article> = sequenceOf(
                    Article(title = "Hello", body = "First"),
                )
            }

            @Composable
            @Pose(providers = [PoseProvider(ArticleSamples::class)])
            fun ArticleCard(article: Article, onOpen: () -> Unit) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Card__Preview.kt").readText()

        assertThat(generated).contains("article = ArticleSamples().values.first()")
        assertThat(generated).contains("@Pose(providers)")
        assertThat(generated).doesNotContain("Article(title")
    }

    @Test
    fun `providers wins over structural synthesis for the domain-slot parameter`() {
        // Regression test: `article` is the ONLY domain-shaped param and would
        // normally take the structural-inline path. With providers set, it should
        // take the provider inline path instead.
        val source = SourceFile.kotlin(
            "Slot.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewParameterProvider
            import io.github.akshaychordiya.pose.Pose
            import io.github.akshaychordiya.pose.PoseProvider

            data class Article(val title: String, val body: String)

            class ArticleSamples : PreviewParameterProvider<Article> {
                override val values: Sequence<Article> = sequenceOf(
                    Article(title = "Hello", body = "First"),
                )
            }

            @Composable
            @Pose(providers = [PoseProvider(ArticleSamples::class)])
            fun OnlyDomainParam(article: Article) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Slot__Preview.kt").readText()

        assertThat(generated).contains("article = ArticleSamples().values.first()")
        assertThat(generated).doesNotContain("Article(title")
        assertThat(generated).doesNotContain("@PreviewParameter")
    }

    @Test
    fun `providers with forParam binds by exact parameter name`() {
        val source = SourceFile.kotlin(
            "Named.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewParameterProvider
            import io.github.akshaychordiya.pose.Pose
            import io.github.akshaychordiya.pose.PoseProvider

            data class Article(val title: String, val body: String)

            class LeftSamples : PreviewParameterProvider<Article> {
                override val values: Sequence<Article> = sequenceOf(Article("L", "l"))
            }

            class RightSamples : PreviewParameterProvider<Article> {
                override val values: Sequence<Article> = sequenceOf(Article("R", "r"))
            }

            @Composable
            @Pose(providers = [
                PoseProvider(LeftSamples::class,  forParam = "left"),
                PoseProvider(RightSamples::class, forParam = "right"),
            ])
            fun Comparison(left: Article, right: Article) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Named__Preview.kt").readText()

        assertThat(generated).contains("left = LeftSamples().values.first()")
        assertThat(generated).contains("right = RightSamples().values.first()")
        assertThat(generated).contains("@Pose(providers.forParam)")
    }

    @Test
    fun `forParam binding wins over generic-type binding when both would apply`() {
        val source = SourceFile.kotlin(
            "Override.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewParameterProvider
            import io.github.akshaychordiya.pose.Pose
            import io.github.akshaychordiya.pose.PoseProvider

            data class Article(val title: String, val body: String)

            class GenericSamples : PreviewParameterProvider<Article> {
                override val values: Sequence<Article> = sequenceOf(Article("G", "g"))
            }

            class SpecificSamples : PreviewParameterProvider<Article> {
                override val values: Sequence<Article> = sequenceOf(Article("S", "s"))
            }

            @Composable
            @Pose(providers = [
                PoseProvider(GenericSamples::class),
                PoseProvider(SpecificSamples::class, forParam = "featured"),
            ])
            fun Feed(regular: Article, featured: Article) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Override__Preview.kt").readText()

        assertThat(generated).contains("regular = GenericSamples().values.first()")
        assertThat(generated).contains("featured = SpecificSamples().values.first()")
    }

    @Test
    fun `providers bypasses sealed fan-out when it matches the sealed parameter`() {
        // Without providers, `HomeState` would fan out to N previews. With a
        // provider bound to HomeState, we get one plan using the provider.
        val source = SourceFile.kotlin(
            "Sealed.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewParameterProvider
            import io.github.akshaychordiya.pose.Pose
            import io.github.akshaychordiya.pose.PoseProvider

            sealed interface HomeState {
                data object Loading : HomeState
                data class Success(val greeting: String) : HomeState
            }

            class HomeSamples : PreviewParameterProvider<HomeState> {
                override val values: Sequence<HomeState> = sequenceOf(
                    HomeState.Success(greeting = "Hi"),
                )
            }

            @Composable
            @Pose(providers = [PoseProvider(HomeSamples::class)])
            fun HomeContent(state: HomeState) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Sealed__Preview.kt").readText()

        // Single preview (no `_Loading` / `_Success` suffixed fan-out), provider used.
        assertThat(generated).contains("internal fun HomeContent__Preview()")
        assertThat(generated).contains("state = HomeSamples().values.first()")
        assertThat(generated).doesNotContain("HomeContent__Preview_Loading")
        assertThat(generated).doesNotContain("HomeContent__Preview_Success")
    }

    @Test
    fun `unmatched providers entry is silently ignored`() {
        // If none of the providers' T matches any parameter type, we fall through
        // to the standard tier ladder — the annotation isn't an error.
        val source = SourceFile.kotlin(
            "Unmatched.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.PreviewParameterProvider
            import io.github.akshaychordiya.pose.Pose
            import io.github.akshaychordiya.pose.PoseProvider

            data class Article(val title: String, val body: String)
            data class Comment(val text: String)

            class CommentSamples : PreviewParameterProvider<Comment> {
                override val values: Sequence<Comment> = sequenceOf(Comment("hi"))
            }

            @Composable
            @Pose(providers = [PoseProvider(CommentSamples::class)])
            fun ArticleOnly(article: Article) { }
            """.trimIndent()
        )
        val result = CompileHarness.compile(listOf(source))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.generatedFile("Unmatched__Preview.kt").readText()

        assertThat(generated).contains("article = Article(")
        assertThat(generated).doesNotContain("CommentSamples")
    }
}
