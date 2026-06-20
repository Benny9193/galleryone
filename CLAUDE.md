# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A single-module Android photo/video gallery app built entirely with Jetpack Compose. It reads
media off the device through `MediaStore`, organizes it into a timeline, albums, and a trash, and
supports sharing, (un)trashing, and permanent deletion. App id / namespace: `com.example.myapplication`.

## Commands

The Gradle wrapper is committed. On Windows there is no `JAVA_HOME` set by default — point Gradle at
a JDK first (the bundled JetBrains Runtime works), e.g.:

```powershell
$env:JAVA_HOME = "C:\Users\conno\Desktop\New folder (2)"   # path to a JDK 17+ (JBR)
```

- Build debug APK: `./gradlew assembleDebug`
- Install on a connected device/emulator: `./gradlew installDebug`
- Unit tests (JVM, `app/src/test`): `./gradlew testDebugUnitTest`
- Instrumented / Compose UI tests (needs a device or emulator, `app/src/androidTest`):
  `./gradlew connectedDebugAndroidTest`
- Run one instrumented test class:
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.ui.GalleryScreensTest`
- Lint: `./gradlew lint`

No physical/AVD device is configured by default, so `connected…` tasks require setting one up first.

## Build configuration

- `minSdk = 33`, `targetSdk`/`compileSdk = 36`. Min SDK 33 (Android 13) is why the app uses the
  granular `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` permissions rather than the legacy storage ones.
- Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog). Add/upgrade
  libraries there and reference them as `libs.*`, not with hardcoded coordinates in `build.gradle.kts`.
- Gradle configuration cache is enabled (`gradle.properties`). AGP 9.2.1 / Kotlin 2.2.10.

## Architecture

Single-Activity, MVVM, unidirectional data flow. There is no DI framework — repositories are
constructed directly inside the ViewModel.

- **`MainActivity`** — the only Activity. Sets up `GalleryTheme` (reacting to the persisted theme +
  dynamic color) and hosts the `GalleryApp` composable.
- **`GalleryApp`** (`GalleryApp.kt`) — the app shell. Handles the runtime media-permission flow
  (rationale → request → "permanently denied → open Settings"), then renders `GalleryNavHost`.
  Owns the Navigation Compose graph, the bottom `NavigationBar`, the snackbar host, and the
  `StartIntentSenderForResult` launcher that drives system trash/delete confirmations. Also contains
  the share-intent helper. Routes: `timeline`, `albums`, `trash`, `settings`, `grid/{bucketId}/{title}`,
  `viewer/{source}/{startIndex}`.
- **`GalleryViewModel`** (`AndroidViewModel`) — single source of truth. Exposes `StateFlow`s for
  albums, the current album's media, the timeline, trash, the active `MediaFilter`, loading, a
  one-shot `GallerySnack`, and a `pendingIntent` (`IntentSender`) the UI must launch. Destructive
  operations are two-phase: `requestTrash`/`requestUntrash`/`requestPermanentDelete` build a
  `MediaStore` request and emit its `IntentSender`; the UI launches the system dialog; the result
  comes back via `onPendingHandled`, which fires the snackbar and refreshes all lists.
- **`data/MediaStoreRepository`** — all `MediaStore` access, off the main thread
  (`Dispatchers.IO`). Queries the unified `MediaStore.Files` collection to read images and videos
  together, then groups by `BUCKET_ID` into `Album`s. Trashed items are fetched with a query
  `Bundle` using `QUERY_ARG_MATCH_TRASHED = MATCH_ONLY`.
- **`data/SettingsRepository`** — persists `AppSettings` (theme, start tab, autoplay, dynamic color)
  via Jetpack DataStore Preferences. Exposed as a `Flow`.
- **`ui/`** — one Composable per screen (`TimelineScreen`, `AlbumsScreen`, `PhotoGridScreen`,
  `PhotoViewerScreen`, `TrashScreen`, `SettingsScreen`) plus shared pieces (`MediaGrid`,
  `Shimmer`) and `ui/theme/`. Screens are **stateless**: they take data + lambda callbacks and never
  touch the ViewModel or repositories directly — wiring lives in `GalleryNavHost`. This is what makes
  them testable in isolation.

### Important invariants

- **Item URIs must come from the per-type collections** (`MediaStore.Images.Media` /
  `MediaStore.Video.Media`), even though rows are *read* from the generic `Files` collection.
  `MediaStore.createDeleteRequest`/`createTrashRequest` reject generic Files URIs ("must be Media
  items"). See `cursorToItems` in `MediaStoreRepository`.
- **Trashing vs. deleting**: trashing keeps items recoverable (~30 days, `expiresAt`) so deletes can
  be undone via the snackbar "Undo". Permanent delete is only used for emptying the trash.

## Testing

UI tests (`GalleryScreensTest`) render each screen Composable with in-memory sample `GalleryItem`/
`Album` data — no `MediaStore`, no permissions — so they're deterministic. Follow this pattern when
adding screen tests: keep the Composable stateless and feed it fixtures rather than mocking the
repository or ViewModel.
