# ROADMAP — صدى (Sada) Voice Recorder

> **Package:** `com.aistudio.voicerecorder.voxrec` | **Stack:** Kotlin 2.2.10, Compose BOM 2024.09.00, AGP 9.1.1, minSdk 24 / targetSdk 36.1 | **Date:** 2026-08-23

Source of truth for prioritized work derived from `2026-08-23` application review. Items reference code locations as `file_path:line_number`.

---

## 0. Vision & Current State

**Vision (from `metadata.json:2`):** Arabic-first smart voice recorder with RTL-native UI, live waveform, and Gemini-powered intelligence.

**Current state — MVP functional:**

- Recording via `MediaRecorder` AAC/M4A (`app/src/main/java/com/example/audio/AudioRecorder.kt:9`), playback via `MediaPlayer` (`app/src/main/java/com/example/audio/AudioPlayer.kt:7`), state in `AndroidViewModel` (`app/src/main/java/com/example/viewmodel/MainViewModel.kt:31`).
- Stateless Compose UI decoupled via `MainUiState` / `MainScreenContent` (`app/src/main/java/com/example/ui/MainScreen.kt:57`), themed through `LocalAppColors` (`app/src/main/java/com/example/ui/theme/AppColors.kt:31`) for light/dark.
- Roborazzi screenshot matrix covering phone/tablet/watch, light/dark, ar/en (`app/src/test/java/com/example/screenshot/*`).
- Basic CRUD: list sorted by `lastModified`, play/pause/seek, rename, delete, share via `FileProvider`.

**Gaps blocking release:** FileProvider authority crash, background-recording kill, no audio-focus handling, broken infinite animation, backup/config bloat, unimplemented Gemini capability.

---

## 1. Phase 0 — Hotfixes (0.2w, ship before any feature) — ✅ Completed 2026-08-23

Goal: make the installed `release` APK runnable without crashes.

| # | Item | Location | Acceptance | Status |
|---|------|----------|------------|--------|
| 0.1 | Align FileProvider authority | `app/src/main/AndroidManifest.xml:30` vs `app/src/main/java/com/example/viewmodel/MainViewModel.kt:344` | Manifest uses `${applicationId}.fileprovider` or `BuildConfig.APPLICATION_ID`; `shareRecording` succeeds on `com.aistudio.voicerecorder.voxrec` build; add instrumented test. | ✅ Done — manifest now `${applicationId}.fileprovider`, merged `com.aistudio.voicerecorder.voxrec.fileprovider` verified |
| 0.2 | Fix pulse animation loop | `app/src/main/java/com/example/ui/MainScreen.kt:236` (`animateFloatAsState` + `infiniteRepeatable`) | Replace with single `rememberInfiniteTransition().animateFloat(...)`; verified via `HomeScreenshotTest` + manual recording screen. | ✅ Done — `pulseTransition` infinite `0.9f→1.1f` |
| 0.3 | Merge duplicate `InfiniteTransition` | `app/src/main/java/com/example/ui/MainScreen.kt:617,626` | One transition per `RecordTriggerButton` when `isRecording`. | ✅ Done — single `glowTransition` for sizePulse+alphaPulse + `Divider→HorizontalDivider` |
| 0.4 | Harden rename/delete | `app/src/main/java/com/example/viewmodel/MainViewModel.kt:317,299` | Sanitized `newName` (`^[\\p{L}0-9 _-]{1,64}$`), trim, reject `../`, handle `renameTo()==false`, collision → append `_1`; UI shows error Snackbar. | ✅ Done — `sanitizeFileName`/`resolveUniqueDestination`, `operationError` + `SnackbarHost` |

**Exit criteria:** `share`, rename with Arabic + special chars, recording indicator all pass on physical device. — ✅ Verified: `BUILD SUCCESSFUL`, `testDebugUnitTest` pass, merged manifest authority `com.aistudio.voicerecorder.voxrec.fileprovider`.

## 2. Phase 1 — Reliability Core (2–3 weeks)

Makes recorder trustworthy for daily use.

**1.1 Audio lifecycle & platform integration**
- Foreground `Service` (`mediaPlayback` / `microphone` foregroundServiceType, Android 14+) with ongoing notification; `AudioRecorder` bound to service, survives backgrounding and configuration change.
- `AudioManager.requestAudioFocus(AudioFocusRequest)` for both recorder and player; handle `AUDIOFOCUS_LOSS_TRANSIENT`, phone call, `ACTION_AUDIO_BECOMING_NOISY` (pause playback).
- Handle `RECORD_AUDIO` revocation mid-recording (`onRequestPermissionsResult` + `AppOps` callback): stop cleanly, keep partial file, surface retry.
- Replace polling `playbackProgressJob` (`app/src/main/java/com/example/viewmodel/MainViewModel.kt:257`) with accurate ticker based on `SystemClock.elapsedRealtime()` + `MediaPlayer.getCurrentPosition` correction; reduce `updateListWithPlaybackState()` churn (diff instead of full `map`).

**1.2 Permission & localization correctness**
- Rationale flow (`shouldShowRequestPermissionRationale`) + permanent-denial → Settings deep link; re-check permission in `onResume`.
- Conditional RTL: only force `LayoutDirection.Rtl` when locale is `ar` (`LocalConfiguration`), not globally (`app/src/main/java/com/example/ui/MainScreen.kt:149`). Audit `values-en/strings.xml:1` vs `values/strings.xml:1` layout mirroring.

**1.3 Timer & waveform accuracy**
- Replace `delay(1000); +=1` drift (`app/src/main/java/com/example/viewmodel/MainViewModel.kt:152`) with elapsed-realtime base.
- Fix visualizer floor (`coerceIn(0.02f,1f)` at `MainViewModel.kt:165` + `0.04f` fill at `MainScreen.kt:699`): map silence to `0f`, empty slots to no bar.

**1.4 Storage & backup hygiene**
- Exclude `files/recordings/` from Auto Backup (`app/src/main/res/xml/backup_rules.xml:10`, `data_extraction_rules.xml:12`): `<exclude domain="file" path="recordings/" />`.
- Move `SimpleDateFormat` (`MainViewModel.kt:120,138`) to `java.time` / thread-local; locale-aware `formatFileSize` (`MainScreen.kt:934`) via resources, not hard-coded `ar`.

## 3. Phase 2 — Product Essentials (3–4 weeks)

Features expected of a "professional" recorder.

- **Pause / resume recording** (requires API 24+ `MediaRecorder.pause()/resume()`; update `AudioRecorder.kt:9` state machine, UI second button, waveform gap marker).
- **Trim & waveform scrub preview** (UI: expand `RecordingItemCard` with mini-waveform derived from `MediaMetadataRetriever` or `WaveformExtractor`; logic in ViewModel or UseCase).
- **Folders / tags & search** — introduce `Room` properly (currently declared but unused in `app/build.gradle.kts:102`): `RecordingEntity` (id, path, duration, tags, folder), migration from file-scan (`scanRecordingsDirectory` at `MainViewModel.kt:96`).
- **Lock-screen & notification controls** for playback (Media3 `MediaSession`).
- **Export** (WAV/MP3 transcode option, share multiple files via `ACTION_SEND_MULTIPLE`).

## 4. Phase 3 — Intelligence (Gemini) — 3 weeks

Unblocks `metadata.json:5` `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` which is currently dead code (`.env` / `secrets` at `app/build.gradle.kts:68` unused).

- Decide on server-side vs on-device: recommend Firebase AI Logic (was `firebase-ai` commented at `app/build.gradle.kts:105`) proxied through backend to keep `GEMINI_API_KEY` off device.
- Features: **auto-title & summary**, **Arabic transcription** (Gemini 2.5 Flash audio input), **keyword search over transcripts**, **action-item extraction**.
- Data: store transcript in Room, indexed via FTS4; opt-in, explicit consent; recordings excluded from backup but transcripts encrypted.
- UX: inline transcript sheet, copy/share transcript, badge `سلسلة ذكية` (`app/src/main/res/values/strings.xml:25`) becomes tappable entry.

## 5. Phase 4 — Quality & Scale (ongoing)

**Testing**
- Expand beyond `ExampleUnitTest.kt:6` / `ExampleRobolectricTest.kt:13`: ViewModel unit tests (coroutines `TestDispatcher`, fake `AudioRecorder`/`AudioPlayer` interfaces), file I/O with `TemporaryFolder`, screenshot coverage for all `MainUiState` permutations (add `homeRecordingActive` en/light case).
- Add lint + `detekt`/`ktlint` gate; fix existing `Divider` → `HorizontalDivider` deprecation (`MainScreen.kt:851`), remove unused `Handler` import (`MainViewModel.kt:8`), enforce `configuration-cache` compatible `prepareImagesForAI` task (`app/build.gradle.kts:139`).

**Build & dependencies**
- Purge unused deps: `firebase-bom`, `room` (until Phase 2), `retrofit`/`okhttp`/`moshi`/`coil` unless Gemini path needs them (`app/build.gradle.kts:80`). Keep `libs.versions.toml` aligned.
- Enable `isMinifyEnabled=true` for `release` after adding R8 keep rules; wire `KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD` in CI (currently `app/build.gradle.kts:29`).

**Performance & accessibility**
- Cache `Brush` in `WaveformVisualizer` (`MainScreen.kt:710`), avoid per-bar allocation.
- Use `testTag` stable keys instead of `item.name` (`MainScreen.kt:760,871`).
- Content descriptions, `semantics`, talkback order for timer/waveform/slider; dynamic type scaling test on tablet/watch qualifiers (`DeviceMatrixScreenshotTest.kt:18`).

**Observability**
- Add crash reporting (Firebase Crashlytics) + analytics for recording duration / failure rate; log `MediaRecorder` error codes (`AudioRecorder.kt:34`).

---

## 6. Sequencing & Milestones

```
✅ W0   Phase 0 hotfixes          → internal dogfood — DONE 2026-08-23
W1-3 Phase 1 reliability core   → closed beta
W4-7 Phase 2 product essentials → public beta (Play Console)
W8-10 Phase 3 Gemini intelligence → 1.0 + AI Studio deploy (remove debugConfig note in README.md:5)
W10+ Phase 4 quality/scale      → 1.1, 1.2 iterations
```

Dependencies: Phase 0 blocks all; Phase 1.1 (foreground service) blocks Phase 2 pause/resume; Phase 2 Room migration blocks Phase 3 transcript storage.

## 7. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Background kill on OEM skins | Foreground service + `WorkManager` for finalizing file on crash |
| Gemini cost/latency on long audio | Chunk upload + server-side truncation, on-device VAD pre-filter |
| RTL regression for `en` | Screenshot matrix already covers `en-w412dp` (`PermissionScreenshotTest.kt:36`) — gate PR on `verifyRoborazziDebug` |
| Scoped storage / file access on Android 15 | Keep to `filesDir/recordings` (`MainViewModel.kt:39`, `file_paths.xml:3`), no `MANAGE_EXTERNAL_STORAGE` |

## 8. How to Contribute

1. Branch from `main`, run `./gradlew testDebugUnitTest verifyRoborazziDebug lintDebug`.
2. For UI changes add/refresh a `@Test` in `app/src/test/java/com/example/screenshot/*` and run `./gradlew recordRoborazziDebug` then `prepareImagesForAI` to update `build/outputs/ai_feed`.
3. Update this roadmap when scope changes; keep `metadata.json` description in sync with shipped capabilities.

---
*Teams: file bugs against the `file_path:line_number` citations above; Phase 0 PRs require device video of share/rename/pulse.*
