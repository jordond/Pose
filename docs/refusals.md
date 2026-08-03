# Pose refusal catalog

Each `PG-xxx` diagnostic emitted by the processor links here for a longer explanation and remediation pattern. Sections are ordered by code.

Under `pose.strict = true` (the default) every refusal is a compile error. Under `pose.strict = false` — including implicitly, when `pose.generatePreviewsForAllPublicComposables = true` — refusals are warnings and Pose skips the composable.

---

## PG001 — no fake-data strategy for parameter

Structural synthesis exhausted every tier (T0 default → T1 well-known FQN → T2 structural walk) and still could not produce a value for the parameter. Usually because the type is domain-specific and Pose has no built-in strategy.

**Fix options, in order of ergonomics:**

1. Add a `companion.previewSamples` sequence on the type:
   ```kotlin
   data class Article(val title: String, val body: String) {
       companion object {
           val previewSamples: Sequence<Article> = sequenceOf(Article("Hello", "…"))
       }
   }
   ```
2. Attach a `PreviewParameterProvider<T>` via `@Pose(providers = [...])`:
   ```kotlin
   class ArticleSamples : PreviewParameterProvider<Article> {
       override val values = sequenceOf(Article("Hello", "…"))
   }
   @Pose(providers = [PoseProvider(ArticleSamples::class)])
   ```
3. Hand-write a `@Preview` for the composable — Pose detects it and skips generation.

---

## PG002 — cycle or depth exceeded during structural synthesis

Two types refer to each other (or a type refers to itself) and structural synth would recurse forever. Pose stops at `pose.maxDepth` (default 8).

**Fix:** break the cycle by providing one of the types via `@Pose(providers = [...])` or `companion.previewSamples`.

---

## PG003 — parameter type is in the refuse category

The parameter's type is on the refuse list — types that inject runtime state (`ViewModel`, Hilt, `SavedStateHandle`, `NavController`, `NavBackStackEntry`, `Context`/`Activity`/`Fragment`, `Bitmap`, `Channel`, `CoroutineScope`, `AsyncImagePainter`). These can't be faked at preview time without materially changing the composable's behavior.

**Fix — the "Content" split pattern:**

```kotlin
@Composable
fun Screen(vm: MyViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    ScreenContent(state, onEvent = vm::onEvent)
}

@Composable
@Pose
fun ScreenContent(state: MyState, onEvent: (MyEvent) -> Unit) { /* … */ }
```

The stateful wrapper stays lean; the previewable `Content` variant takes plain state.

---

## PG004 — composable declares generic type parameters

Pose can't decide which concrete type argument to fake.

**Fix:** create a non-generic wrapper for the preview:

```kotlin
@Composable @Pose
fun ListScreenStringPreview() = ListScreen<String>(items = listOf("a", "b"))
```

---

## PG005 — composable declares context receivers or parameters

Kotlin's context-parameter feature is not yet supported by Pose.

**Fix:** move the context'd work behind a helper that the composable calls at runtime, or wait for a follow-up release.

---

## PG006 — sealed subtype invisible in this compilation

A sealed hierarchy defined in a downstream/library module can't be walked at compile time from this module.

**Fix:** provide the values via `@Pose(providers = [...])` or `companion.previewSamples` at the type's declaration site.

---

## PG007 — extension receiver not in the whitelist

Only well-known Compose scopes (`BoxScope`, `RowScope`, `ColumnScope`, `LazyListScope`, etc.) can be materialized in previews.

**Fix:** hand-write a `@Preview` that supplies the receiver, or open a PR adding the scope to the whitelist.

---

## PG008 — composable is a member function

`@Pose` only supports top-level composables or members of a top-level `object`.

**Fix:** move the composable to top-level, or into a top-level `object` container.

---

## PG009 — composable is private

Private composables aren't referenceable from the generated file's package.

**Fix:** raise visibility to `internal` or `public`.

---

## PG010 — total preview count exceeds the configured cap

A sealed fan-out (or nested combination) would emit more previews than `pose.maxPreviewsPerComposable` (default 8).

**Fix — pick one:**

1. Narrow with a `companion.previewSamples` sequence
2. Raise the cap with `arg("pose.maxPreviewsPerComposable", "16")`
3. Split the sealed hierarchy across multiple composables

---

## PG011 — `pose.themeFqName` is invalid

The FQN passed in the KSP option doesn't resolve to a top-level composable or class on the compile classpath.

**Fix:** confirm the FQN is correct and the theme module is on the current module's classpath.

---

## PG013 — hand-written `@Preview` detected

Informational — Pose noticed an existing `@Preview` on the same composable and skipped generation.

---

## PG014 — `@Pose` applied to a non-`@Composable` function

`@Pose` only decorates composable functions.

**Fix:** add `@Composable`, or remove `@Pose`.

---

## PG015 — `@Pose` applied to a non-Unit-returning composable

Previews render `Unit`-returning composables.

**Fix:** if the return value isn't needed for the preview, refactor to a `Unit`-returning variant.

---

## PG017 — `previews[]` entry is not annotated `@Preview`

Every class listed in `@Pose(previews = [...])` must itself be annotated with `@Preview` (directly or transitively via multipreview annotations).

**Fix:** either annotate the referenced class with `@Preview`, or point `previews[]` at a valid multipreview annotation like `PreviewLightDark`.
