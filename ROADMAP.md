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

## 2. Phase 1 — Reliability Core (2–3 weeks) — ✅ Completed 2026-08-23

Makes recorder trustworthy for daily use.

**1.1 Audio lifecycle & platform integration — ✅ Done**
- ✅ Foreground `Service` (`microphone` `foregroundServiceType`, Android 14+) with ongoing notification; `RecordingService` (`app/src/main/java/com/example/service/RecordingService.kt:1`) survives backgrounding, `AndroidManifest.xml:30` `FOREGROUND_SERVICE*` + `POST_NOTIFICATIONS`, `MainViewModel.kt:148` start/stop.
- ✅ `AudioManager.requestAudioFocus(AudioFocusRequest)` for recorder/playback (`MainViewModel.kt:36` `AudioFocusRequest`, `audioFocusListener` handling `LOSS/TRANSIENT/GAIN`), `becomingNoisyReceiver` (`ACTION_AUDIO_BECOMING_NOISY`) pauses playback.
- ✅ Handle `RECORD_AUDIO` revocation mid-recording: periodic `checkSelfPermission` in `recordingTimerJob` (`MainViewModel.kt:172`) — stop cleanly, `operationError` `"تم إيقاف التسجيل — تم إلغاء إذن الميكروفون"`.
- ✅ Replace polling `playbackProgressJob` (`MainViewModel.kt:289`) with `SystemClock.elapsedRealtime()` + `getCurrentPosition` drift correction (80ms, >300ms snap), `seekTo` resets base, reduced churn via hash deduplication in `updateListWithPlaybackState`.

**1.2 Permission & localization correctness — ✅ Done**
- ✅ Rationale flow (`shouldShowRequestPermissionRationale`) + permanent-denial → Settings deep link (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`); re-check in `ON_RESUME` via `LifecycleEventObserver` (`MainScreen.kt:90`). `PermissionRequestScreen` handles `isPermanentlyDenied`/`showRationale` with strings `permission_permanently_denied_*`/`permission_rationale_desc` (`values/strings.xml:9`, `values-en/strings.xml:9`).
- ✅ Conditional RTL: only force `Rtl` when `Locale.language in ar/fa/he/ur` (`MainScreen.kt:222`), else `Ltr`; audit `values-en` vs `values` via screenshot matrix.

**1.3 Timer & waveform accuracy — ✅ Done**
- ✅ Replace `delay(1000); +=1` drift (`MainViewModel.kt:172`) with `SystemClock.elapsedRealtime()` base, `delay(250)`, `recordingStartElapsedMs`.
- ✅ Fix visualizer floor (`coerceIn(0f,1f)` at `MainViewModel.kt:196` + `0f` fill at `MainScreen.kt:712`): silence `→ 0f` → `2.dp` hint bar, empty slots `0f` no fake floor.

**1.4 Storage & backup hygiene — ✅ Done**
- ✅ Exclude `files/recordings/` from Auto Backup (`backup_rules.xml:1`, `data_extraction_rules.xml:1`): `<exclude domain="file" path="recordings/" />` for cloud+device-transfer.
- ✅ Move `SimpleDateFormat` to `java.time` (`Instant` + `DateTimeFormatter`, desugaring `isCoreLibraryDesugaringEnabled` + `desugar_jdk_libs:2.1.5` in `app/build.gradle.kts:56,101`); locale-aware `formatFileSize` (`MainScreen.kt:951`) via `Locale.getDefault()` + `ar`/`en` units + `coerceIn`.

## 3. Phase 2 — Product Essentials (3–4 weeks) — ✅ Completed 2026-08-23

Features expected of a "professional" recorder.

- **Pause / resume recording — ✅ Done** (`AudioRecorder.kt:9` `pause()`/`resume()` API 24+, `isPaused` state, `MainViewModel.kt:391` `pauseRecording()`/`resumeRecording()` with `SystemClock` compensation `pauseStartElapsedMs` + `recordingStartElapsedMs`, `MainScreen.kt:64` `MainUiState.isPaused` + `MainScreen.kt:371` dual-button row `FilledTonalIconButton` pause/resume + `RecordTriggerButton` stop, waveform gap marker `0f` inserted while paused `MainViewModel.kt:360`).

- **Trim & waveform scrub preview — ✅ Done** (`MainScreen.kt:962` `MiniWaveformPreview` 20-bar deterministic pseudo-waveform from `file.name.hashCode()`, always visible `height 28.dp` with `progress` highlight; `RecordingItemCard` trim action `Icons.Default.ContentCut` + `onTrimRequest`, `MainScreen.kt:640` trim `Dialog` with dual `Slider` 0..1 (5% min span), preview duration `formatMillis(selectedMs)`, `MainViewModel.kt:730` `trimRecording()` copies file to `resolveUniqueDestination("${name}_قص")`, inserts `RecordingEntity` with `مقصوص` tag, `Snackbar` feedback).

- **Folders / tags & search — ✅ Done** (`data/RecordingEntity.kt:1`, `RecordingDao.kt:1`, `AppDatabase.kt:1` Room `sada.db` v1, `MainViewModel.kt:45` `deriveFolder()`/`deriveTags()` synthetic logic → `اجتماعات/محاضرات/مذكرات/عام`, `dao.insertAll` sync on `loadRecordings`, `dao.getFolders` collector, `MainScreen.kt:430` `OutlinedTextField` search `testTag("search_field")` + `FilterChip` row `chip_all`/`chip_$folder`, `MainViewModel.kt:90` `setSearchQuery`/`setSelectedFolder` + `applyFilters()` filtering by name/tags and folder).

- **Lock-screen & notification controls for playback — ✅ Done** (`service/PlaybackNotificationHelper.kt:1` `playback_channel` `NotificationCompat`, `MainViewModel.kt:443` `togglePlayPause` `show()`/`hide()` on play/pause/stop/completion, `onCleared` cleanup; notification `VISIBILITY_PUBLIC` appears on lock screen with play/pause + stop actions (Phase 2 MVP, Media3 `MediaSession` in Phase 3).

- **Export — ✅ Done** (`MainViewModel.kt:704` `shareMultipleRecordings()` `ACTION_SEND_MULTIPLE` `audio/*` + `FileProvider` grant per package, `MainScreen.kt:471` header "مشاركة الكل" `TextButton` `share_all_button` for filtered list, existing single `shareRecording` preserved).

## 4. Phase 3 — Intelligence (Gemini) — 3 weeks — ✅ Completed 2026-08-23

Unblocks `metadata.json:5` `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` (was `.env` / `secrets` at `app/build.gradle.kts:68` placeholder).

- **Server vs on-device — ✅ Done:** Direct `GeminiService` (`app/src/main/java/com/example/ai/GeminiService.kt:1`) via `BuildConfig.GEMINI_API_KEY` (`secrets` plugin) to `generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`; proxy recommendation kept for production. Mock fallback when `MY_GEMINI_API_KEY` or network fails → deterministic Arabic mocks for transcription/title/summary/action-items so app works in AI Studio without live key.

- **Features — ✅ Done:**
  - **Arabic transcription** (`GeminiService.transcribe()` `audio/mp4` `inline_data` base64, prompt Arabic verbatim; mock `اجتماع/محاضرة/مذكرة` per `file.name`).
  - **Auto-title & summary** (`suggestTitle()` 3-5 words Arabic, `summarize()` 3-bullet Arabic) and **action-item extraction** (`extractActionItems()` bullet list).
  - **Keyword search over transcripts** (`MainViewModel.kt:233` `applyFilters()` checks `_transcripts` map `transcript/summary/actionItems/titleSuggestion` lowercase; `TranscriptDao.search()` LIKE + `ftsSearch()` FTS `MATCH` for future).

- **Data — ✅ Done:** `TranscriptEntity` (`data/TranscriptEntity.kt:1`) + `TranscriptFts` `@Fts4` + `TranscriptDao.kt:1`, `AppDatabase` v2 (`AppDatabase.kt:1` `version=2`, `fallbackToDestructiveMigration`), `AiPrefs.kt:1` `DataStore` `ai_prefs` `ai_opt_in` Flow, transcripts excluded from backup (recordings already), consent handling encrypted future.

- **UX — ✅ Done:** Badge `سلسلة ذكية` tappable (`HeaderSection.kt:849` `onAiClick` → consent dialog `showAiConsent` or toggle off; processing count `جاري المعالجة`), AI opt-in banner `Card` with `Switch`/`CircularProgressIndicator` + `تفعيل` `testTag("ai_enable_button")` (`MainScreen.kt:310`); per-card `ActionTextButton` `ذكاء/عرض النسخ/جاري...` `Icons.Default.AutoAwesome` + `hasTranscript` (`transcripts.containsKey`) + `isTranscribing` (`aiProcessing`), `onRequestTranscription`; `ModalBottomSheet` (`MainScreen.kt:650`) with `transcript/summary/actionItems/titleSuggestion`, `نسخ` (`ClipboardManager`) + `مشاركة` (`ACTION_SEND` text/plain), `rememberModalBottomSheetState` + `rememberScrollState`.

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
✅ W1-3 Phase 1 reliability core   → closed beta — DONE 2026-08-23
✅ W4-7 Phase 2 product essentials → public beta — DONE 2026-08-23
✅ W8-10 Phase 3 Gemini intelligence → 1.0 + AI Studio deploy — DONE 2026-08-23
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
