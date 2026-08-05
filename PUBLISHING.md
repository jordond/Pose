# Publishing to Maven Central

Pose publishes to Maven Central via the [Sonatype Central Portal](https://portal.central.sonatype.com/) using the [vanniktech maven-publish](https://vanniktech.github.io/gradle-maven-publish-plugin/) plugin.

## Current status

- vanniktech plugin wired on both `annotations` and `processor` modules.
- POM metadata (name/description/url/license/developer/scm) configured centrally in the root `build.gradle.kts`, with per-module overrides for `name` and `description`.
- `signAllPublications()` enabled — signing tasks skip cleanly when no key is present on the local dev machine, and pick up the in-memory GPG key from GitHub Actions secrets during release.
- `publishToMavenCentral(automaticRelease = true)` — the release workflow uploads and auto-promotes the staging repository in one shot.
- `.github/workflows/release.yml` triggers on any `v*` tag push and runs `publishAndReleaseToMavenCentral`.

## First-time setup (one-off, human-in-the-loop)

### 1. Claim the `io.github.akshaychordiya` namespace

1. Sign in to <https://central.sonatype.com> with the GitHub account whose username matches the namespace suffix (`akshaychordiya` — GitHub is case-insensitive, so `AkshayChordiya` is fine).
2. **Namespaces → Add Namespace**, request `io.github.akshaychordiya`.
3. Central auto-verifies by asking you to create a public repo whose name matches a one-time key — the portal walks you through it. Verification usually completes in a few minutes.

### 2. Generate a signing key

```bash
gpg --full-generate-key                   # RSA, 4096 bits, no expiry (or your choice)
gpg --list-secret-keys --keyid-format=long
# → note the long key ID (16 hex chars)
gpg --export-secret-keys --armor <KEY_ID> | pbcopy   # for macOS; use `xclip -selection clipboard` on Linux
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

Publish the public key to `keyserver.ubuntu.com`, `keys.openpgp.org`, and `pgp.mit.edu` so Central can verify signatures.

### 3. GitHub Actions secrets

Under `Settings → Secrets and variables → Actions`, add:

| Secret                           | Value                                                                                  |
|----------------------------------|----------------------------------------------------------------------------------------|
| `MAVEN_CENTRAL_USERNAME`         | User token username from `central.sonatype.com` → *View Account → Generate User Token* |
| `MAVEN_CENTRAL_PASSWORD`         | User token password from the same page                                                 |
| `SIGNING_IN_MEMORY_KEY_ID`       | Last 8 chars of the GPG key ID                                                         |
| `SIGNING_IN_MEMORY_KEY`          | Full ASCII-armored private key (the `-----BEGIN PGP PRIVATE KEY BLOCK-----` payload)   |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | The GPG passphrase                                                                     |

The release workflow reads these as `ORG_GRADLE_PROJECT_*` env vars — Gradle picks them up as project properties automatically.

## Release flow

Once the setup above is complete, cutting a release is:

```bash
# 1. Bump version in root build.gradle.kts (drop the -SNAPSHOT)
#    e.g. "0.4.0-SNAPSHOT" → "0.4.0"
# 2. Update CHANGELOG.md with a `## [<version>]` section — REQUIRED, the
#    release workflow fails without it (see GitHub Releases below).
# 3. Commit + tag + push.
git commit -am "Release 0.4.0"
git tag v0.4.0
git push origin main --tags
```

GitHub Actions picks up the tag, runs `publishAndReleaseToMavenCentral`, and the artifacts appear on `search.maven.org` within ~30 minutes.

### GitHub Releases are automatic

Both release workflows create the GitHub Release for you, with notes lifted straight out of `CHANGELOG.md`:

- **Title** — the section's subtitle, e.g. `0.5.0 — LocalInspectionMode + custom CompositionLocals`
- **Body** — everything under that `## [<version>]` heading, up to the next one
- **Assets** — the plugin workflow attaches `intellij-plugin-<version>.zip`, so the README's install-from-disk path has something to point at

The extraction runs *before* the publish step and **fails the build if `CHANGELOG.md` has no section for the version being tagged** — a red build beats a release with empty notes. Preview exactly what a release will say before you tag:

```bash
.github/scripts/extract-changelog.sh 0.5.0 --title
.github/scripts/extract-changelog.sh 0.5.0
```

For the KSP workflow the Release is created *after* Central accepts the upload, so a Release never announces artifacts that failed to publish. Manual `workflow_dispatch` runs of the plugin workflow skip the Release step — there's no tag to attach one to.

After the release cuts:

```bash
# Bump to next snapshot for continued development.
# root build.gradle.kts: version = "0.5.0-SNAPSHOT"
git commit -am "Bump to 0.5.0-SNAPSHOT"
git push origin main
```

## Local publishing (dry runs)

```bash
# Publish to ~/.m2/repository — no credentials required; signing tasks skip.
./gradlew publishToMavenLocal

# Verify the generated POM has the required Central metadata:
ls ~/.m2/repository/io/github/akshaychordiya/pose/annotations/0.4.0-SNAPSHOT/*.pom
```

Every Central-required field (name, description, url, license, developer, scm) should be present in the generated POM.

## Coordinates published

| Artifact    | Coordinate                                              |
|-------------|---------------------------------------------------------|
| Annotations | `io.github.akshaychordiya.pose:annotations:<version>`   |
| Processor   | `io.github.akshaychordiya.pose:processor:<version>`     |

Sources and Javadoc JARs are published alongside each artifact.

## Troubleshooting

- **"POM missing required fields"** — inspect the generated `.pom` after `publishToMavenLocal`. Central requires `<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>`. Missing anything → adjust `pom { … }` in the root `build.gradle.kts`.
- **Signing task fails locally** — expected when no GPG key is set. Signing is skipped automatically for `publishToMavenLocal` when signing properties are absent.
- **"401 Unauthorized" from Central** — user token has expired. Regenerate at `central.sonatype.com → View Account → Generate User Token` and update the GitHub secrets.
- **Namespace not verified** — check `central.sonatype.com → Namespaces`. If pending, the verification repo must exist publicly under your GitHub account.

---

# Publishing the IntelliJ plugin to JetBrains Marketplace

The plugin releases **independently** from the KSP artifacts. The processor iterates faster (new emitters, bug fixes, refuse-list additions); the plugin's UX story is more stable and republishing it on every processor patch would be noise. Two separate cadences, two separate tag conventions.

## Cadences

| Change lands on… | Tag | Workflow file | Publishes |
|---|---|---|---|
| KSP annotations / processor | `v0.5.0`, `v0.5.1`, … | `release.yml` | Maven Central |
| IntelliJ plugin | `plugin-v0.4.0`, `plugin-v0.5.0`, … | `plugin-release.yml` | JetBrains Marketplace |

`plugin-release.yml` also accepts a **manual `workflow_dispatch`** — trigger it from the Actions tab UI when you want to push a plugin update without minting a tag.

## One-time setup

### 1. Create a Marketplace publisher account

Sign in to <https://plugins.jetbrains.com> with any JetBrains ID. **`My Plugins → Upload Plugin`** for the first submission — JetBrains reviews first-time uploads by hand (~1–3 business days) before automated flows are allowed.

### 2. Generate a Marketplace token

After first-plugin approval, go to <https://plugins.jetbrains.com/author/me/tokens> → **Generate token** → copy the value.

### 3. Add the token to GitHub secrets

`Settings → Secrets and variables → Actions` → **New repository secret**:

| Secret | Value |
|---|---|
| `MARKETPLACE_TOKEN` | The token from step 2 |

## Release flow

### Standard tag-based release

```bash
# 1. Update change notes in intellij-plugin/build.gradle.kts (pluginConfiguration.changeNotes)
# 2. Bump the plugin version if it lives on its own — otherwise the root version is used.
# 3. Commit, tag, push.
git commit -am "Plugin release 0.5.0"
git tag plugin-v0.5.0
git push origin main --tags
```

GitHub Actions picks up the `plugin-v*` tag, runs `:intellij-plugin:buildPlugin` + `:intellij-plugin:publishPlugin`. The updated plugin appears in Marketplace search within minutes and IDE users get a plugin-update notification on their next start.

### Ad hoc release from the Actions UI

`GitHub repo → Actions → Plugin release → Run workflow` → pick a channel:

- **default** — public stable (what most users have selected)
- **beta** — opt-in beta channel; users must add the beta URL in their IDE settings
- **eap** — opt-in early-access channel; same setup as beta

## Local dry runs

```bash
# Just build the zip — no upload, no credentials required.
./gradlew :intellij-plugin:buildPlugin
ls intellij-plugin/build/distributions/intellij-plugin-<version>.zip

# Verify against multiple IDE builds before publishing (downloads ~1 GB of IDE distributions).
./gradlew :intellij-plugin:verifyPlugin

# Publish locally (rarely needed — CI is the normal path).
MARKETPLACE_TOKEN=xxx ./gradlew :intellij-plugin:publishPlugin
```

## Troubleshooting

- **"Unauthorized" from Marketplace** — token has expired (they age out after a year). Regenerate at `plugins.jetbrains.com/author/me/tokens` and update the `MARKETPLACE_TOKEN` secret.
- **Plugin update not visible in the IDE** — Marketplace caches for ~15 minutes after publish. IDE users get notified on their next start; force-check via `Settings → Plugins → ⚙️ → Check for Updates`.
- **`verifyPlugin` reports incompatibilities** — usually means an API we use has been removed in a newer IDE. Either bump `sinceBuild` past the removal, or refactor. The report lives at `intellij-plugin/build/reports/pluginVerifier/`.
- **First submission rejected** — JetBrains sends specific feedback (missing screenshots, unclear description, plugin.xml issue). Fix, re-upload via the web UI. The tag-based flow only unlocks once the first version is approved.
