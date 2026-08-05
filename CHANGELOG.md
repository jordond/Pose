# Changelog

All notable changes to Pose are documented here. Follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and semantic versioning.

## [0.5.0] - LocalInspectionMode + custom CompositionLocals

### Added

- **Generated previews now provide `LocalInspectionMode = true`.** Every generated preview is wrapped in `CompositionLocalProvider(LocalInspectionMode provides true)`. Studio's preview renderer already sets this, but snapshot runners (Paparazzi, Roborazzi, `com.android.compose.screenshot`) leave it `false` — so a composable branching on `LocalInspectionMode.current` took its production path there, attempting real network calls with dummy URLs and producing blank snapshots. Opt out with `pose.provideInspectionMode = false`.
- **`pose.previewWrapperFqName`** - point at your own composable to supply arbitrary `CompositionLocal`s (fake image loaders, no-op analytics, locale providers). Same trailing-lambda contract as `pose.themeFqName`. Nesting, outermost first: inspection-mode provider → your wrapper → theme → target. The wrapper sits inside Pose's provider so it keeps the final say over any local Pose also sets.
- `PG018` diagnostic + [refusal-catalog entry](docs/refusals.md#pg018) for an unresolvable `previewWrapperFqName`.

### Changed

- **Generated output changes for every user** - the extra `CompositionLocalProvider` layer appears in all generated previews. Behaviourally a no-op in Studio (which already sets the local); the difference shows up in snapshot tests, which is the point. No action needed unless you were relying on snapshot tests exercising the non-inspection path — in that case set `pose.provideInspectionMode = false`.
- Emission is skipped silently when `androidx.compose.ui.platform.LocalInspectionMode` isn't on the compile classpath (a module with `compose-runtime` but no `compose-ui`), so no build can break on the new reference.

### Tests

- New `InspectionModeTest` — 6 cases covering default-on, opt-out, nesting order vs the theme, wrapper emission, full four-layer nesting order, and the PG018 refusal.
- 4 new `OptionsTest` cases for the two new options. 60 processor tests total.

## [0.4.3] - KMP gutter icons

Plugin-only release; no KSP artifact changes.

### Fixed

- **Gutter icons now appear for KMP / CMP composables.** The IntelliJ plugin's generated-file locator only handled Android's flat `build/generated/ksp/<variant>/kotlin/…` layout. Kotlin Multiplatform nests one level deeper — `build/generated/ksp/<target>/<sourceSet>/kotlin/…` (e.g. `metadata/commonMain/kotlin/…`) — so composables declared in `commonMain` had no icon. Replaced the single-level scan with a bounded BFS that covers both layouts.
- New `PoseLineMarkerProviderTest` case locks in the `metadata/commonMain` path.

## [0.4.2] - Theme auto-skip + CMP/KMP verified

### Added

- **Auto-skip the theme composable in bulk mode.** When `pose.generatePreviewsForAllPublicComposables = true`, the composable whose FQN matches `pose.themeFqName` is now skipped automatically — no more `@PoseIgnore` boilerplate on `AppTheme`. Reason: previewing the theme just renders its empty content lambda.
- IDE plugin now available to make it easy to quickly see the generated previews
- New `BulkOptInTest` case locks in the theme-auto-skip behaviour.

### Verified

- Pose runs cleanly on **Compose Multiplatform / Kotlin Multiplatform** projects. `androidx.compose.ui.tooling.preview.Preview` is now the shared annotation FQN across Android and CMP; the emission works on both. Wire the processor into `ksp<Target>Main` in the target module's `build.gradle.kts`.

### Docs

- README's "Limitations" section replaces "Compose Multiplatform — Android-only for v1" with a new "Platforms 🌍" section listing Android and KMP/CMP as first-class.

## [0.4.1] - IntelliJ plugin polish

Plugin-only release; no KSP artifact changes. Published to JetBrains Marketplace via the new `plugin-v*` tag flow (KSP artifacts on Central stay at 0.4.0).

### Changed

- Gutter-icon tooltip now reads `Open Pose preview: <name>` instead of just `Open <name>` - the click affordance matches the icon's Pose branding.

### Added

- 4 `BasePlatformTestCase` unit tests for `PoseLineMarkerProvider` covering: no gutter without a generated file, one target when a matching preview exists, popup-count tooltip for sealed fan-outs, no gutter when the generated file exists but has no matching function name.
- New `.github/workflows/plugin-release.yml` - the plugin now releases independently of KSP artifacts (triggers on `plugin-v*` tag push or manual `workflow_dispatch` with a channel input).
- `.run/Build Plugin.run.xml` - shared Gradle run configuration; `Build Plugin` shows up in the IDE run-config dropdown.
- `PUBLISHING.md` documents the plugin release cadence, Marketplace token setup, tag convention, ad hoc dispatch, and troubleshooting.

## [0.4.0] - first Maven Central release

First release published to Maven Central under `io.github.akshaychordiya.pose`. No API changes from 0.3.0 - this cut is about getting the artifact into people's hands.

### Publishing

- POM metadata (name, description, url, licenses, developers, scm) wired on both artifacts so Central Portal accepts the upload.
- GitHub Actions release workflow triggers on `v*` tag push and runs `publishAndReleaseToMavenCentral` (auto-promotes the staging repo).
- Signing keys and Sonatype credentials sourced from repo secrets - no local key material required for CI.

## [0.3.0] - bulk opt-in + better refusal messages

### Added

- `pose.generatePreviewsForAllPublicComposables` KSP option. When `true`, every public top-level `@Composable fun … : Unit` in the module gets a generated preview without needing `@Pose`. Skips composables that already carry `@Pose` (explicit config wins), `@PoseIgnore`, or a hand-written `@Preview`.
- `@PoseIgnore` annotation for opting a single composable out of bulk mode.
- `docs/refusals.md` - catalog of every `PG-xxx` code with rewrite patterns. All diagnostic messages link back here via an anchor.

### Changed

- Bulk mode implicitly coerces `pose.strict` to `false`; a single un-fakeable composable becomes a warning-and-skip instead of failing the build.
- `Diagnostics.refusal()` now demotes every refusal code to a warning when `strict = false`. Previously only PG001/PG002/PG010 were downgradable, so `strict = false` was misleadingly lenient for signature refusals.
- SignatureChecker refusals routed through `refusal()` instead of `hardError()` so `pose.strict` actually applies to them.
- All refusal messages now name the composable, give a concrete fix (rewrite skeleton, provider snippet, or config change), and link to `docs/refusals.md#<pg-code>`.

## [0.2.0] - `@Pose(providers)` + capability showcase

### Added

- `@Pose(providers = [PoseProvider(...)])` binds a `PreviewParameterProvider<T>` at the composable level. Two matching modes: generic-type (default) and explicit `forParam` for disambiguating same-typed parameters. Named binding wins over generic-type binding.
- Default `@Preview` pair now stamps `showBackground = true` so previews read correctly against dark IDE themes.

### Fixed

- Provider-slot selection now skips parameters already bound by `@Pose(providers)`, so explicit providers beat structural / companion / sealed fan-out for their parameter.
- `SampleResolver` refuses to synthesize class-shaped types whose visibility is not `public` or `internal` - was previously emitting references to private data classes that failed to compile in downstream projects.

### Removed

- Pre-release `@PreviewSampleOf` - the surface consolidated onto `@Pose(providers)`.

## [0.1.0] - initial

- `@Pose` annotation, KSP2 processor, tier ladder (T0 default → T1 well-known FQN → T2 structural synthesis).
- Sealed fan-out (one preview per subtype), `companion.previewSamples` support, theme wrapping via `pose.themeFqName`.
- Sample app with LoginContent + HomeContent.

[0.5.0]: https://github.com/AkshayChordiya/Pose/releases/tag/v0.5.0
[0.4.3]: https://github.com/AkshayChordiya/Pose/releases/tag/plugin-v0.4.3
[0.4.2]: https://github.com/AkshayChordiya/Pose/releases/tag/v0.4.2
[0.4.1]: https://github.com/AkshayChordiya/Pose/releases/tag/plugin-v0.4.1
[0.4.0]: https://github.com/AkshayChordiya/Pose/releases/tag/v0.4.0
[0.3.0]: https://github.com/AkshayChordiya/Pose/releases/tag/v0.3.0
[0.2.0]: https://github.com/AkshayChordiya/Pose/releases/tag/v0.2.0
[0.1.0]: https://github.com/AkshayChordiya/Pose/releases/tag/v0.1.0
