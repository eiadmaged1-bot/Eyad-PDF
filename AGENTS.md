# Agent Guidelines (AI-ready) — PDF Toolkit

This project is an Android app with multiple product flavors (Play Store / F-Droid / Open Source). Use this file as the **source of truth** for how to build, release, and keep F-Droid metadata working in CI.

## Repo overview

- **Android app**: `app/`
- **Flavors**: defined in `app/build.gradle.kts`
  - `playstore`
  - `fdroid`
  - `opensource`
- **Version source of truth (for releases)**: `gradle.properties`
  - `APP_VERSION_CODE`
  - `APP_VERSION_NAME`
- **F-Droid metadata (app repo copy)**: `metadata/com.yourname.pdftoolkit.yml`
- **F-Droid data repo checkout**: `fdroiddata/` (usually gitignored in this app repo)
  - Real CI for F-Droid runs against the `fdroiddata` repository’s metadata.

## Flavor intent and constraints

- **`playstore` flavor**
  - May include Play Services / proprietary SDKs (e.g., ML Kit).
  - Used for Play Store releases.
- **`fdroid` flavor**
  - Must be fully FOSS-compatible (no Google Play Services).
  - Uses open OCR stack (Tesseract) instead of ML Kit.
  - CI should avoid referencing Play-only APIs from this flavor.
- **`opensource` flavor**
  - FOSS-first build (no ads, no Firebase, no Play Services).
  - Similar constraints to `fdroid`, but can be distributed outside F-Droid as well.

## Building locally (developer machine)

- **Requirements**: JDK 17+, Android SDK installed, Gradle wrapper available.
- **Common commands**:
  - `./gradlew :app:assembleFdroidDebug`
  - `./gradlew :app:assemblePlaystoreDebug`
  - `./gradlew lintFdroidDebug --continue`

## Versioning rules (important for F-Droid)

F-Droid’s `checkupdates` cannot extract version info if it is dynamic. This project keeps **static** version values in `gradle.properties`:

- `APP_VERSION_CODE=<int>`
- `APP_VERSION_NAME=<string>`

`app/build.gradle.kts` reads those properties for `versionCode`/`versionName` to remain F-Droid friendly.

## F-Droid metadata + GitLab CI (fdroiddata)

The main F-Droid validation/build pipeline runs in the **`fdroiddata` repo**.

### Required metadata fields for tag-based updates

- `RepoType: git`
- `Repo: <git url>`
- `UpdateCheckMode: Tags`
- `AutoUpdateMode: Version`

### Fix for “Couldn't find any version information” in `checkupdates`

If the upstream uses Gradle properties for versions, set `UpdateCheckData` to read them from `gradle.properties`:

`UpdateCheckData: gradle.properties|APP_VERSION_CODE=(\\d+)|.|APP_VERSION_NAME=(.+)`

This tells `fdroid checkupdates --auto` how to extract version code/name for each tag.

### CI hygiene: permissions + codequality.json

- `fdroidserver` warns if `config.yml` permissions are not `0600`.
- Some jobs upload `codequality.json`; ensure it is created (write `[]`) when there are no findings.

## PDF viewer / annotation rules (don’t regress)

### UI/UX guidelines
- **Highlighter tool** must be transparent and blend with content (not opaque marker).
  - Use `BlendMode.MULTIPLY` or `PorterDuff.Mode.MULTIPLY`.
  - Use ~30% alpha.
  - Avoid stacking: render highlights in a single layer.
- **Zoom controls**: keep zoom button container hidden; preserve menu access.
- **Touch gestures**: don’t break pinch-to-zoom; allow multi-touch to reach the PDF view.

### Fragment lifecycle and overlays
- Set `documentUri` before attaching the viewer fragment.
- Use `view.post {}` for UI changes that require the PDF to be loaded.
- Overlay views should not intercept touch unless actively annotating.

## What an AI agent should do when editing

- Prefer changes that keep the `fdroid` flavor 100% FOSS.
- When updating versions:
  - Update `APP_VERSION_CODE` / `APP_VERSION_NAME` in `gradle.properties`.
  - Tag releases in git (e.g., `vX.Y.Z`).
  - Update F-Droid metadata `CurrentVersion` / `CurrentVersionCode` accordingly.
- When touching F-Droid metadata:
  - Ensure `fdroid rewritemeta` produces no diff (canonical formatting).

---

Last Updated: 2026-05-07
