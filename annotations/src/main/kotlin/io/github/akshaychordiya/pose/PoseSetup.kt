package io.github.akshaychordiya.pose

import kotlin.reflect.KClass

/**
 * Marks the module's [PoseConfig] object so Pose can find it.
 *
 * ```
 * @PoseSetup(generateForAllPublicComposables = true)
 * internal object FeaturePose : PoseConfig {
 *     @Composable
 *     override fun Theme(content: @Composable () -> Unit) = AppTheme(content)
 * }
 * ```
 *
 * Exactly one per module. KSP can't enumerate annotated symbols across compiled
 * dependencies, so the object must live in the module being processed — share
 * config through ordinary inheritance instead:
 *
 * ```
 * // :design-system
 * interface AppPoseDefaults : PoseConfig {
 *     @Composable
 *     override fun Theme(content: @Composable () -> Unit) = AppTheme(content)
 * }
 *
 * // :feature:checkout
 * @PoseSetup(generateForAllPublicComposables = true)
 * internal object CheckoutPose : AppPoseDefaults
 * ```
 *
 * Settings here override the equivalent `pose.*` Gradle options; a `@Pose(...)`
 * argument on an individual composable overrides both.
 *
 * Scalars live on this annotation rather than as properties on [PoseConfig]
 * because KSP can read annotation arguments but not property initializers.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class PoseSetup(
    /**
     * Generate previews for the whole module without annotating anything.
     *
     * Every public top-level `@Composable` returning `Unit` is treated as if it
     * carried `@Pose`. Skips anything already annotated `@Pose` (its own
     * arguments win), marked [PoseIgnore], or carrying a hand-written
     * `@Preview` — and wrapper-shaped composables like themes, whose only
     * required parameter is a `@Composable` content lambda.
     *
     * Turning this on also demotes refusals to warnings: opting a whole module
     * in shouldn't mean one un-previewable composable fails everyone's build.
     */
    val generateForAllPublicComposables: Boolean = false,

    /**
     * Which `@Preview` annotations get stamped onto every generated function —
     * i.e. what variants you see in the preview panel.
     *
     * Leave empty and Pose stamps its own light + dark pair with
     * `showBackground = true`. Set it to bundle whatever dimensions your team
     * cares about into one place:
     *
     * ```
     * @Preview(name = "Light",     uiMode = UI_MODE_NIGHT_NO)
     * @Preview(name = "Dark",      uiMode = UI_MODE_NIGHT_YES)
     * @Preview(name = "Font 1.5×", fontScale = 1.5f)
     * @Preview(name = "RTL",       locale = "ar")
     * annotation class AppPreviews
     *
     * @PoseSetup(previews = [AppPreviews::class])
     * ```
     *
     * Adding a dimension later is then a one-line edit that ripples through
     * every generated preview in the module. Compose's own multipreview
     * annotations (`PreviewLightDark`, `PreviewFontScale`, `PreviewScreenSizes`)
     * work here too.
     */
    val previews: Array<KClass<out Annotation>> = [],

    /**
     * Whether generated previews declare themselves as previews at runtime, by
     * providing `LocalInspectionMode = true`.
     *
     * Matters because composables often branch on it to avoid real work:
     *
     * ```
     * if (LocalInspectionMode.current) {
     *     Image(painterResource(R.drawable.placeholder), null)
     * } else {
     *     AsyncImage(model = url, contentDescription = null)   // real network call
     * }
     * ```
     *
     * Android Studio's preview renderer sets this for you, but snapshot runners
     * (Paparazzi, Roborazzi, Google's screenshot plugin) don't — so without this
     * the composable above would try to fetch a dummy URL during a screenshot
     * test and render an empty box. Pose provides it so both environments agree.
     *
     * Turn it off if you deliberately want snapshot tests to exercise the
     * production path — e.g. you inject a fake image loader and want the real
     * layout measured.
     */
    val provideInspectionMode: Boolean = true,

    /**
     * How many previews one composable may produce before Pose gives up on it.
     *
     * Usually one composable means one preview, but a `sealed` parameter fans
     * out to one preview per subtype — so a state with six subtypes is six
     * previews, and each is a screenshot test if you're using one. The cap stops
     * a large hierarchy from quietly generating dozens.
     *
     * Exceeding it emits `PG010`. Raise it, or narrow the input by giving the
     * type a `companion object { val previewSamples: Sequence<T> }`.
     */
    val maxPreviewsPerComposable: Int = 8,

    /**
     * How deep Pose will walk into nested types while inventing sample data.
     *
     * When a parameter is a type Pose doesn't recognise, it builds one by
     * calling the constructor and inventing each argument — recursing when those
     * are themselves custom types. `Order(customer = Customer(address =
     * Address(...)))` is three levels.
     *
     * The cap exists so a self-referential type (`Node(parent: Node)`) can't
     * recurse forever. Raise it if you have genuinely deep state and see `PG002`;
     * lower it to force earlier refusals rather than large synthesized objects.
     */
    val maxDepth: Int = 8,

    /**
     * How many elements Pose puts in a `List`, `Set` or `Map` parameter.
     *
     * Two is enough to prove a list renders as a list rather than a single item,
     * without bloating every generated file. Raise it if your composable only
     * looks right with more rows — pagination, grids, "and N more" affordances.
     */
    val collectionSize: Int = 2,
)
