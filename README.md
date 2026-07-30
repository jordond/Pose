# Pose

A KSP2 processor that auto-generates Jetpack Compose `@Preview` functions from your `@Composable` code — build-time, deterministic, offline, no LLM.

Your composable strikes a **Pose** for the preview panel. Annotate, build, done.

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

Never checked in. Always in sync with the composable signature. Zero maintenance.

## Why

Compose's preview ecosystem is great at **consuming** previews (Showkase, Paparazzi, Roborazzi, Google's screenshot-testing plugin) but has no build-time story for **generating** them. IDE plugins and Gemini's "Generate Preview" write source you have to maintain. Pose never touches your source.

## Install

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies {
    implementation("io.github.akshaychordiya.pose:annotations:0.1.0")
    kspDebug      ("io.github.akshaychordiya.pose:processor:0.1.0")

    implementation     ("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

ksp {
    // Fully qualified name of your Theme
    arg("pose.themeFqName", "com.example.ui.AppTheme")
}
```

Requires Kotlin 2.0+, KSP 2.x, AGP 8.2+, Compose BOM 2024.02+, JDK 17+. Tested on Kotlin 2.4.x, KSP 2.3.x, AGP 9.2.x.

The `pose.themeFqName` composable must have the signature `fun ThemeName(content: @Composable () -> Unit)` (extras are OK if defaulted). Unset → no theme wrapper + one build-init warning.

## Use

Annotate any composable:

```kotlin
@Pose
@Composable
fun MyScreen(state: UiState, onEvent: (Event) -> Unit) { /* … */ }
```

### Sealed states → one preview per subtype, named

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

Emits `HomeContent__Preview_Loading`, `HomeContent__Preview_Success`, `HomeContent__Preview_Error`. Studio labels each render by its subtype name.

### Custom sample data → `companion.previewSamples`

For richer variants (empty/error/long-text/edge cases), expose a sequence on the type's companion:

```kotlin
data class LoginUiState(...) {
    companion object {
        val previewSamples: Sequence<LoginUiState> = sequenceOf(
            LoginUiState(email = "", …),
            LoginUiState(isSubmitting = true, …),
            LoginUiState(error = "Invalid", …),
        )
    }
}
```

Pose wires it through `@PreviewParameter` automatically. Every composable taking `LoginUiState` now shows all variants. Write once, benefit everywhere.

### Preview matrix → one project-wide multipreview annotation

Bundle every dimension your team cares about into one annotation, use it consistently:

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

Adding a new dimension (dynamic-color, foldables, etc.) is a one-line edit that ripples through every generated preview. Default is `PreviewLightDark`.

## Snapshot testing - the multiplier

Every `@Pose` composable becomes a **free visual regression test** with Paparazzi, Roborazzi, or Google's `com.android.compose.screenshot` - no extra test code.

**Google's screenshot testing plugin:**

```kotlin
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.screenshotTest?.addStaticSourceDirectory(
            layout.buildDirectory.dir("generated/ksp/debug/kotlin").get().asFile.path
        )
    }
}
```

**Paparazzi / Roborazzi** (via [ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner)):

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

Add a composable → next CI run adds a golden to review. Rename it → the golden name tracks. Delete it → the golden disappears. Visual regression coverage stops being separate work - it's the annotation you already added for previews.

## How values are synthesized

Per parameter, first match wins:

| Tier       | Rule                                                                                                                |
|------------|---------------------------------------------------------------------------------------------------------------------|
| **T0**     | Parameter has a default → omit the argument                                                                         |
| **T1**     | Well-known FQN (Compose value classes, `Flow`, `StateFlow`, `java.time`, `Uri`, `Result<T>`, …) → inline expression |
| **T2**     | Structural synthesis (primitives, enums, data classes, sealed, value classes, function types, collections)          |
| **Refuse** | No strategy → `PG-xxx` diagnostic + skip                                                                            |

Deterministic - never `Random`, never clock.

## Refused composables

Emits `PG-xxx` diagnostics for:

- Parameter types in the refuse list: `ViewModel`, Hilt, `SavedStateHandle`, `NavController`, `NavBackStackEntry`, `Context`/`Activity`/`Fragment`, `Bitmap`, `Channel`, `CoroutineScope`, `AsyncImagePainter`. Hoist state to a `Content(state, onEvent)` variant.
- Generic type parameters or context receivers on the composable.
- `private` visibility, or member composables (must be top-level or in an `object`).

Handwritten `@Preview` in the same file → Pose detects it and skips generation (info-logged with `pose.verboseSkips=true`).

## KSP options

| Option                          | Default | Behavior                                                          |
|---------------------------------|---------|-------------------------------------------------------------------|
| `pose.themeFqName`              | *unset* | Theme composable to wrap generated calls in                       |
| `pose.strict`                   | `true`  | `false` downgrades no-strategy / cycle / cap-exceeded to warnings |
| `pose.maxDepth`                 | `8`     | Cap on recursion depth for structural synthesis                   |
| `pose.collectionSize`           | `2`     | Elements emitted for `List` / `Set`                               |
| `pose.maxPreviewsPerComposable` | `8`     | Cap on total previews per composable                              |
| `pose.verboseSkips`             | `false` | Log every skip decision                                           |

## Limitations

- **No preview marker next to you're composable.** KSP can only emit new files, so generated previews live in `build/generated/` - Studio's gutter icon appears there, not next to your source. Use split-editor or "Go to Declaration" on `@Pose`. Compiler-plugin or companion IntelliJ-plugin fix is on the roadmap
- **Body-level analysis** - Pose only sees signatures. A composable that internally calls `hiltViewModel()` or reads a `LocalContext` may crash at preview render. Hand-write a `@Preview` for those (Pose skips), or refactor to stateless
- **Compose Multiplatform** - Android-only for v1
- **Other v1 non-goals:** ViewModel/Hilt params (refused by design), runtime fake-data libs, context params, default-expression forwarding ([KSP #268](https://github.com/google/ksp/issues/268))

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for project layout, the processor pipeline diagram, and how to add types or refusal rules.

## License

Apache 2.0. See [LICENSE](LICENSE).
