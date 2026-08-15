# Pose 🏞️✨

[![Maven Central](https://img.shields.io/maven-central/v/io.github.akshaychordiya.pose/annotations?color=4C1&label=Maven%20Central&logo=apachemaven)](https://central.sonatype.com/namespace/io.github.akshaychordiya.pose)
[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/33344?color=4C1&label=IntelliJ%20Plugin&logo=jetbrains)](https://plugins.jetbrains.com/plugin/33344-pose--auto-generate-compose-previews)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![CI](https://github.com/AkshayChordiya/Pose/actions/workflows/ci.yml/badge.svg)](https://github.com/AkshayChordiya/Pose/actions/workflows/ci.yml)

**Auto-generate Jetpack Compose `@Preview` functions from your `@Composable` code at build-time, deterministic, offline, no LLM.**

Your composable strikes a **Pose** for the preview panel. Annotate, build, done. ✨

![Pose generating a Compose preview at build time](docs/images/pose-demo.gif)

```kotlin
@Composable
@Pose
fun LoginContent(state: LoginUiState, onSubmit: () -> Unit) { /* … */ }
```

Every build regenerates into `build/generated/ksp/debug/kotlin/`:

```kotlin
@PreviewLightDark @Composable
internal fun LoginContent__Preview() {
    AppTheme {
        LoginContent(
            state = LoginUiState(email = "Email", password = "Password", isSubmitting = false, error = null),
            onSubmit = {},
        )
    }
}
```

- Never checked in - regenerates on every build 🔄
- Always in sync with the composable signature 🎯
- Zero maintenance 🧘

---

## Why 🤔

Compose's preview ecosystem is great at **consuming** previews (Showkase, Paparazzi, Roborazzi, Google's screenshot-testing plugin) but has no build-time story for **generating** them.

- Gemini's *Generate Preview* and IDE plugins write source you have to maintain 🤖
- Hand-written previews rot the moment the composable signature changes 📝
- Pose never touches your source. Files land in `build/generated/`, always fresh ✍️

## Install 🔧

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies {
    implementation("io.github.akshaychordiya.pose:annotations:0.6.3")
    kspDebug("io.github.akshaychordiya.pose:processor:0.6.3")

    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

```

Then declare one config object per module - this is where your theme goes:

```kotlin
@PoseSetup
internal object AppPose : PoseConfig {
    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        AppTheme { content() }      // ← ordinary Kotlin: type-checked, rename-safe
    }
}
```

Pose never learns your theme's name - it emits `AppPose.Theme { … }` and lets the compiler resolve the rest. Rename `AppTheme` and the IDE refactors this file with everything else.

The snippet above is the Android setup. For Compose Multiplatform, see [Platforms](#compose-multiplatform-).

**Requirements**

- Kotlin **2.0+**, KSP **2.x**
- AGP **8.2+**, Compose BOM **2024.02+**
- Compose Multiplatform **1.11+**
- JDK **17+**

Tested on Kotlin 2.4.x · KSP 2.3.x · AGP 9.2.x.

**No config object?** Pose still works - you just get no theme wrapper. Everything on `@PoseSetup` has a sensible default.

## Use ✨

Annotate any composable:

```kotlin
@Pose
@Composable
fun MyScreen(state: UiState, onEvent: (Event) -> Unit) { /* … */ }
```

### IDE plugin: gutter icons + jump-to-preview 🎨

![Pose's gutter icon in Android Studio, next to a @Pose-annotated composable](docs/images/gutter-icon.png)

Install the companion **[Pose IntelliJ plugin](https://plugins.jetbrains.com/plugin/33344-pose--auto-generate-compose-previews)** - either from the Marketplace page directly, or from inside your IDE:

1. `Settings → Plugins → Marketplace`
2. Search *Pose - Auto generate Compose Previews*
3. Click **Install**
4. Restart your IDE

You then get:

- 👁️ **Gutter icon** next to every composable Pose generates a preview for both explicit `@Pose` *and* bulk-mode composables without one
- 🖱️ **Click to jump** into the matching `<Composable>__Preview*` function inside the generated file
- 🎯 **Popup chooser** for sealed fan-outs - pick `_Loading` / `_Success` / `_Error` and land there directly

Requires Android Studio Ladybug (2024.2) or newer. K1 and K2 modes both supported. Optional but recommended - the KSP processor works standalone; the plugin just removes the need to open `build/generated/` by hand.

### Sealed states, one preview per subtype 🌿

```kotlin
sealed interface HomeState {
    data object Loading : HomeState
    data class Success(val user: User) : HomeState
    data class Error(val message: String) : HomeState
}

@Pose
@Composable
fun HomeContent(state: HomeState) { /* … */ }
```

Emits three named preview functions:

- `HomeContent__Preview_Loading`
- `HomeContent__Preview_Success`
- `HomeContent__Preview_Error`

Studio labels each render by its subtype name - the preview panel reads cleanly at a glance.

### Rich sample data with `companion.previewSamples` 🧪

For empty / error / long-text / edge-case variants, expose a sequence on the type's companion:

```kotlin
data class LoginUiState(...) {
    companion object {
        val previewSamples: Sequence<LoginUiState> get() = sequenceOf(
            LoginUiState(email = "", …),
            LoginUiState(isSubmitting = true, …),
            LoginUiState(error = "Invalid", …),
        )
    }
}
```

Pose wires it through `@PreviewParameter` automatically. **Write once, benefit everywhere** - every composable that takes `LoginUiState` now shows all variants.

#### Prefer to keep sample data out of production source entirely?
Put a `PreviewParameterProvider` in `src/debug/kotlin` and reference it with `@Pose(providers = [...])` instead 👇

### Bring your own `PreviewParameterProvider` 🧺

Don't own the type? Want composable-scoped sample data without polluting the model? Attach a `PreviewParameterProvider<T>` at the annotation:

```kotlin
class ArticleSamples : PreviewParameterProvider<Article> {
    override val values = sequenceOf(
        Article(title = "Hello world",  body = "First paragraph"),
        Article(title = "Longer post",  body = "Longer body with more text"),
    )
}

@Pose(providers = [ArticleSamples::class])
@Composable
fun ArticleCard(
    article: Article,           // ← auto-matched to ArticleSamples by generic type
    onOpen: () -> Unit,
) { /* … */ }
```

Pose walks each entry's `PreviewParameterProvider<T>` supertype, extracts `T`, and matches it against the composable's parameter types. Emits `ArticleSamples().values.first()` inline. Providers stay scoped to this composable - they never leak into others.

**Two parameters of the same type?** Annotate them individually with `@PoseSample`:

```kotlin
@Pose
@Composable
fun ArticleComparison(
    @PoseSample(ArticleSamples::class)         left: Article,
    @PoseSample(FeaturedArticleSamples::class) right: Article,
) { /* … */ }
```

The binding lives *on* the parameter, so renaming `left` carries it along - nothing to fall out of sync. `@PoseSample` wins over `providers` when both would apply.

### Preview matrix from one multipreview annotation 🎛️

Bundle every dimension your team cares about into one annotation:

```kotlin
@Preview(name = "Light",     uiMode = UI_MODE_NIGHT_NO)
@Preview(name = "Dark",      uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "RTL",       locale = "ar")
@Preview(name = "Font 1.5×", fontScale = 1.5f)
@Preview(name = "Landscape", widthDp = 640, heightDp = 360)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class AppPreviews

@Composable @Pose(previews = [AppPreviews::class])
fun MyChip(...) { /* … */ }
```

Adding a new dimension (dynamic-color, foldables, tablet-size) is a **one-line edit** that ripples through every generated preview. Default is Pose's own light + dark pair with `showBackground = true`.

### `LocalInspectionMode` and custom CompositionLocals 🔌

Generated previews are wrapped in `CompositionLocalProvider(LocalInspectionMode provides true)` automatically:

```kotlin
internal fun PhotoViewer__Preview() {
  CompositionLocalProvider(LocalInspectionMode provides true) {
    AppTheme {
      PhotoViewer(url = "Url")
    }
  }
}
```

**Why this matters** - Android Studio's preview renderer sets `LocalInspectionMode` for you, but snapshot runners (Paparazzi, Roborazzi, `com.android.compose.screenshot`) leave it `false`. A composable that branches on it:

```kotlin
@Composable
fun PhotoViewer(url: String) {
    if (LocalInspectionMode.current) {
        Image(painterResource(R.drawable.placeholder), null)   // static fallback
    } else {
        AsyncImage(model = url, contentDescription = null)      // real network call
    }
}
```

…would render correctly in Studio but fall through to the production path in a snapshot test - attempting a network fetch with a dummy URL and producing a blank image. Pose provides the local so both environments agree.

Opt out with `@PoseSetup(provideInspectionMode = false)`.

**Need other CompositionLocals?** Override `Wrapper` on your config object — fake image loaders, no-op analytics, locale providers:

```kotlin
@PoseSetup
internal object AppPose : PoseConfig {
    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        AppTheme { content() }
    }

    @Composable
    override fun Wrapper(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalImageLoader provides fakeImageLoader(),
            LocalAnalytics provides NoOpAnalytics,
        ) { content() }
    }
}
```

Nesting, outermost first: **inspection-mode provider → your wrapper → theme → target composable**. Your wrapper sits inside Pose's provider deliberately - innermost `CompositionLocalProvider` wins, so you keep the final say over any local Pose also sets.

## Snapshot testing - the multiplier 📸

Every `@Pose` composable becomes a **free visual regression test** with Paparazzi, Roborazzi, or Google's `com.android.compose.screenshot` - with **zero extra test code**.

**Google's screenshot testing plugin**

```kotlin
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.screenshotTest?.addStaticSourceDirectory(
            layout.buildDirectory.dir("generated/ksp/debug/kotlin").get().asFile.path
        )
    }
}
```

**Paparazzi / Roborazzi** (via [ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner))

```kotlin
class PoseSnapshotTest {
    @get:Rule val paparazzi = Paparazzi()
    @TestParameter lateinit var preview: ComposablePreview<AndroidPreviewInfo>

    @Test fun snapshot() = paparazzi.snapshot { preview() }

    companion object {
        @JvmStatic fun previews() =
            AndroidComposablePreviewScanner().scanPackageTrees("com.example").getPreviews()
    }
}
```

The lifecycle takes care of itself:

- Add a composable - next CI run adds a golden to review ➕
- Rename it - the golden name tracks automatically 🔤
- Delete it - the golden disappears 🗑️

Visual regression coverage stops being separate work. It's the annotation you already added for previews.

## How values are synthesized 🎨

Per parameter, first match wins (top-down):

| Tier         | Rule                                                                                                                |
|--------------|---------------------------------------------------------------------------------------------------------------------|
| **Explicit** | `@PoseSample` on the parameter, else a `@Pose(providers = [...])` generic-type match                |
| **T0**       | Parameter has a default value - omit the argument                                                                   |
| **T1**       | Well-known FQN (Compose value classes, `Flow`, `StateFlow`, `java.time`, `Uri`, `Result<T>`, …) - inline expression |
| **T2**       | Structural synthesis (primitives, enums, data classes, sealed, value classes, function types, collections)          |
| **Refuse**   | No strategy - emits a `PG-xxx` diagnostic and skips                                                                 |

Deterministic - never `Random`, never clock, never classpath-iteration order.

## Refused composables 🛑

Pose emits actionable `PG-xxx` diagnostics for:

- **Refuse-list parameter types**: `ViewModel`, Hilt, `SavedStateHandle`, `NavController`, `NavBackStackEntry`, `Context`/`Activity`/`Fragment`, `Bitmap`, `Channel`, `CoroutineScope`, `AsyncImagePainter`. Hoist state to a `Content(state, onEvent)` variant.
- **Generic type parameters** or **context receivers** on the composable.
- **`private` visibility**, or **member composables** must be top-level or in an `object`.

Every refusal message names the composable, gives a concrete fix, and links to the [refusal catalog](docs/refusals.md).

### Bulk opt-in for whole-module coverage 🚀

For teams that want previews everywhere without annotating each composable, flip one flag on the config object:

```kotlin
@PoseSetup(generateForAllPublicComposables = true)
internal object AppPose : PoseConfig { /* … */ }
```

Every public top-level `@Composable fun … : Unit` in the module now gets a generated preview. Bulk mode implicitly sets `pose.strict = false` - a single un-fakeable composable turns into a warning-and-skip instead of failing the build.

Opt individual composables back out with `@PoseIgnore`:

```kotlin
@PoseIgnore
@Composable
fun DebugOverlay(state: DebugState) { /* … */ }   // public but noisy in the preview panel
```

Pose also skips any composable that already carries `@Preview`, and honors explicit `@Pose(...)` arguments (name / wrapInTheme / previews / providers) when both are present.

## Configuration 📋

Everything lives on the module's `@PoseSetup` object:

| Setting | Default | Behavior |
|---|---|---|
| `Theme` override | passthrough | Wraps every generated preview - your theme goes here |
| `Wrapper` override | passthrough | Supplies `CompositionLocal`s (fake image loaders, no-op analytics) |
| `generateForAllPublicComposables` | `false` | Bulk opt-in. Implies non-strict diagnostics |
| `previews` | *unset* | Multipreview annotations for the whole module |
| `provideInspectionMode` | `true` | Wrap in `CompositionLocalProvider(LocalInspectionMode provides true)` |
| `maxPreviewsPerComposable` | `8` | Cap on previews per composable |
| `maxDepth` | `8` | Cap on structural-synthesis recursion |
| `collectionSize` | `2` | Elements emitted for `List` / `Set` |
| `showBackground` | `true` | `showBackground = true` on the default light/dark pair. Ignored when `previews` is set |

### KSP options

Only two, both build-behaviour knobs you might want to differ between a local build and CI. Everything that shapes previews lives on `@PoseSetup` above, where the compiler checks it.

| Option | Default | Behavior |
|---|---|---|
| `pose.strict` | `true` | `false` demotes refusals to warnings instead of failing the build |
| `pose.verboseSkips` | `false` | Log every skip decision |

Anything else starting with `pose.` warns via `PG023` with a "did you mean" suggestion — including options removed in 0.6.0, so an old config tells you rather than silently doing nothing.

## Platforms 🌍

[`sample-app`](sample-app) is a Compose Multiplatform module whose `commonMain`
composables are processed once and compiled for Android, desktop (JVM), iOS and wasm.
[`sample-android`](sample-android) wraps it in a runnable Android app.

- **Android** - first-class.
- **Compose Multiplatform** - `commonMain` composables, all targets.

### Compose Multiplatform 🧩

There is no multiplatform dialect to opt into - a generated preview is byte-identical
whether it lands in an Android variant or in `commonMain`. Since Compose Multiplatform
**1.11**, `org.jetbrains.compose.ui:ui-tooling-preview` publishes `@Preview`,
`@PreviewParameter`, `PreviewParameterProvider` and the `@PreviewLightDark` family into
`commonMain` under the same **`androidx.compose.ui.tooling.preview`** names Jetpack Compose
uses, and `LocalInspectionMode` is common too.

> The older `org.jetbrains.compose.ui.tooling.preview.*` package (from
> `compose.components.uiToolingPreview`) is deprecated upstream in favour of the androidx
> names. Pose targets the current ones.

Put `@Pose` on composables in `commonMain`, then run KSP once over the common metadata
compilation:

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain {
            // KSP writes here; it is not a source root by default.
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")

            dependencies {
                implementation("io.github.akshaychordiya.pose:annotations:0.6.3")
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.akshaychordiya.pose:processor:0.6.3")
}

// Every other compilation, and every per-target KSP task, reads the generated sources.
// Without this they race the metadata KSP task and see an empty source dir.
tasks.matching {
    it.name != "kspCommonMainKotlinMetadata" &&
        (it is org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*> || it.name.startsWith("ksp"))
}.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}
```

**Use `kspCommonMainMetadata` only.** Adding per-target configurations (`kspJvm`,
`kspAndroid`, `kspIosArm64`, …) alongside it re-emits the same previews once per target and
fails the build with duplicate declarations. Previews generated from `commonMain` are
already compiled into every target.

Your `@PoseSetup` config object goes in `commonMain` like any other common code -
`PoseConfig` is published as Kotlin Multiplatform metadata, so it resolves there:

```kotlin
@PoseSetup
internal object AppPose : PoseConfig {
    @Composable
    override fun Theme(content: @Composable () -> Unit) = AppTheme(content)
}
```

Composables that live in a platform source set rather than `commonMain` are supported the
same way - point the processor at that source set's KSP configuration (`kspAndroid`,
`kspJvm`, …) instead.

## Limitations 🚧

- **Body-level analysis** - Pose only sees signatures. A composable that internally calls `hiltViewModel()` or reads a `LocalContext` may crash at preview render. Hand-write a `@Preview` for those (Pose skips), or refactor to stateless.
- **Other non-goals** - ViewModel/Hilt params (refused by design - split into `Screen(vm) / ScreenContent(state, onEvent)` per [PG003](docs/refusals.md#pg003)), runtime fake-data libs, context params, default-expression forwarding ([KSP #268](https://github.com/google/ksp/issues/268)).

## Contributing 🤝

See [CONTRIBUTING.md](CONTRIBUTING.md) for project layout, the processor pipeline diagram, and how to add types or refusal rules.

Also see the [refusal catalog](docs/refusals.md) and the [changelog](CHANGELOG.md).

## License 📄

Apache 2.0. See [LICENSE](LICENSE).
