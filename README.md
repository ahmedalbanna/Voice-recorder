# صدى (Sada) — Professional Arabic Voice Recorder

> **Package:** `com.aistudio.voicerecorder.voxrec` | **Namespace:** `com.example` | **minSdk 24 / targetSdk 36.1** | **Kotlin 2.2.10 / AGP 9.1.1 / Compose BOM 2024.09.00**

Arabic-first smart voice recorder with RTL-native UI, live waveform, pause/resume, trim, folders/tags, lock-screen controls, and Gemini-powered transcription. All roadmap phases (0–4) completed and verified `BUILD SUCCESSFUL` + `testDebugUnitTest` + `lintDebug`.

View in AI Studio: https://ai.studio/apps/f6d10957-e7a5-4680-8cf9-2546b4d1429b

---

## Features

- **Recording:** `MediaRecorder` AAC/M4A 128kbps/44.1kHz `AudioRecorder.kt:9`, `isPaused` API 24+ `pause()`/`resume()` with `SystemClock.elapsedRealtime` drift-free timer `MainViewModel.kt:309` and gap marker `0f` (`MainViewModel.kt:360`, `MainScreen.kt:712`).
- **Playback:** `MediaPlayer` `AudioPlayer.kt:7` with `AudioFocusRequest` `LOSS/TRANSIENT/GAIN` and `ACTION_AUDIO_BECOMING_NOISY` pause `MainViewModel.kt:54`, drift-corrected ticker 80ms `SystemClock` (`MainViewModel.kt:289`).
- **Lifecycle:** `RecordingService.kt:1` `microphone` FGS + `FOREGROUND_SERVICE*` + `POST_NOTIFICATIONS` `AndroidManifest.xml:6`, survives backgrounding.
- **Permissions:** Rationale + permanent-denial → Settings `ACTION_APPLICATION_DETAILS_SETTINGS` + `ON_RESUME` re-check `MainScreen.kt:90`, revocation guard mid-recording.
- **UI:** Stateless `MainScreenContent` + `MainUiState` `MainScreen.kt:64`, `LocalAppColors` light/dark `Theme.kt:1`, forced RTL only for `ar/fa/he/ur` `MainScreen.kt:283`, `WaveformVisualizer` 45 bars + `MiniWaveformPreview` 20 bars hash-derived `MainScreen.kt:962`.
- **Product:** Pause/resume dual-button, trim `Dialog` dual `Slider` 5% min `MainScreen.kt:798`, search `OutlinedTextField` + `FilterChip` folders `MainScreen.kt:520`, `ActionTextButton` share/rename/trim/AI/delete with stable `hashCode` tags `MainScreen.kt:1241`.
- **Data:** Room `sada.db` v2 `AppDatabase.kt:1` `RecordingEntity` + `TranscriptEntity` + `TranscriptFts` `@Fts4`, `Dao` sync on `loadRecordings`, `AiPrefs` `DataStore` `ai_opt_in`.
- **AI (Gemini 2.5 Flash):** `GeminiService.kt:1` `BuildConfig.GEMINI_API_KEY` (`secrets` `.env`) → `generativelanguage.googleapis.com/.../generateContent` `OkHttp` `inline_data` base64 `audio/mp4`, mock fallback when `MY_GEMINI_API_KEY`; `transcribe`/`summarize`/`extractActionItems`/`suggestTitle`, auto-transcribe on `stopRecording` when opt-in, keyword search over transcripts `MainViewModel.kt:233`, `ModalBottomSheet` with copy/share `MainScreen.kt:920`.
- **Export:** `ACTION_SEND` single + `ACTION_SEND_MULTIPLE` `audio/*` `shareMultipleRecordings` `MainViewModel.kt:886`, share-all header button.
- **Quality:** `isMinifyEnabled+isShrinkResources=true` `build.gradle.kts:46` with R8 keeps `proguard-rules.pro:1`, `isCoreLibraryDesugaringEnabled` `desugar_jdk_libs:2.1.5` `java.time`, cached `Brush` `MainScreen.kt:800`, stable `testTag`s, `semantics`/`heading`/`contentDescription`, `AnalyticsHelper.kt:1` + `AudioRecorder` `OnErrorListener`.

Full phase log: `ROADMAP.md:24` (Phases 0–4 `✅ DONE 2026-08-23`), sequencing `W0–W10+`.

## Tech Stack

- **Language:** Kotlin 2.2.10, Java 11 (`compileOptions` `build.gradle.kts:55`)
- **UI:** Compose BOM 2024.09.00, Material3, `activity-compose` 1.10.1, `lifecycle-runtime-compose` 2.8.7, `lifecycle-viewmodel-compose` 2.8.7, `core-ktx` 1.18.0
- **Data:** Room 2.7.0 (`room-runtime`, `room-ktx`, KSP `room-compiler`), `datastore-preferences` 1.1.7, `desugar_jdk_libs:2.1.5`
- **Network/AI:** `okhttp` 4.10.0, `logging-interceptor` 4.10.0, `moshi-kotlin` 1.15.2 + `codegen`, `retrofit` 2.12.0 + `converter-moshi` (available, Gemini uses `OkHttp`+`org.json` directly), `secrets-gradle-plugin` 2.0.1 (`BuildConfig.GEMINI_API_KEY`)
- **Test:** `junit` 4.13.2, `androidx.test` `core` 1.6.1/`junit` 1.3.0/`espresso` 3.7.0/`runner` 1.6.2, `robolectric` 4.16.1, `roborazzi` 1.59.0 (`compose`+`junit-rule`), `kotlinx-coroutines-test` 1.10.2, KSP `moshi`+`room`
- **Build:** AGP 9.1.1, KSP 2.3.5, `gradle` 9.7.1, `roborazzi` `outputDir` `build/outputs/roborazzi`, custom `prepareImagesForAI` (`build.gradle.kts:141`) `inputs.dir`/`outputs.dir` config-cache compatible, `lint` `baseline.xml`

## Project Structure

```
app/src/main/java/com/example/
  MainActivity.kt:12                // enableEdgeToEdge + ViewModelProvider + MyApplicationTheme
  audio/AudioRecorder.kt:9          // MediaRecorder + pause/resume API24+ + OnError/Info + Analytics
  audio/AudioPlayer.kt:7            // MediaPlayer + completion callback
  model/RecordingItem.kt:1          // file, name, dateString, durationMs, fileSizeBytes, isPlaying/progress
  viewmodel/MainViewModel.kt:40     // AndroidViewModel, Room + AI + focus + noisy + timers + CRUD + search + trim + share
  data/
    RecordingEntity.kt:1            // filePath PK, name, folder, tags, durationMs, lastModified
    RecordingDao.kt:1               // getAll/search/getByFolder/getFolders/insert/delete
    TranscriptEntity.kt:1           // transcript + TranscriptFts @Fts4
    TranscriptDao.kt:1              // search LIKE + ftsSearch MATCH
    AppDatabase.kt:1                // v2, recordingDao + transcriptDao, fallbackToDestructiveMigration(dropAllTables=true)
    AiPrefs.kt:1                    // DataStore ai_opt_in Flow
  ai/GeminiService.kt:1             // transcribe/summarize/extractActionItems/suggestTitle, mock fallback
  service/
    RecordingService.kt:1           // FGS microphone, notification channel "recording_channel"
    PlaybackNotificationHelper.kt:1 // playback_channel, VISIBILITY_PUBLIC notification
  analytics/AnalyticsHelper.kt:1    // counters + Log.i/e
  ui/
    MainScreen.kt:64                // MainUiState + MainScreen (permission + AI state) + MainScreenContent (RTL conditional, Scaffold, AI banner, visualizer, timer, pause/resume, search/chips, list, dialogs, bottom sheet)
    theme/Color.kt:1, AppColors.kt:1, Theme.kt:1, Type.kt:1
  res/
    values/strings.xml:1 / values-en/strings.xml:1 // ar/en (app_name صدى/Sada, permission*, ai)
    xml/file_paths.xml:1, backup_rules.xml:1, data_extraction_rules.xml:1 // exclude recordings/
gradle/libs.versions.toml:1
docs/                               // additional docs
```

## Setup

**Prerequisites:** Android Studio Ladybug+ , JDK 21, Android SDK 36.1

1. **Clone & open:** `Open` project in Android Studio, let it sync (Gradle 9.7.1).
2. **API key:** `cp .env.example .env` and set `GEMINI_API_KEY` (AI Studio: Secrets panel). Placeholder `MY_GEMINI_API_KEY` works via mock; real key enables live Gemini (`GeminiService.kt:28` `isMockMode` check).
3. **Signing:** For `release`, set env `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`; for `debug` uses `debug.keystore` `android/androiddebugkey` (`build.gradle.kts:28`). README step 5 `Remove signingConfig debugConfig` only for AI Studio cloud build.
4. **Run:** Select device/emulator API 24+ → `Run 'app'`. Grant `RECORD_AUDIO` when prompted (rationale/Settings flow `MainScreen.kt:90`).

**CLI:**
```bash
./gradlew :app:assembleDebug              # APK
./gradlew :app:installDebug               # to device
./gradlew :app:testDebugUnitTest          # 34+ tests (RecordingUtils, GeminiService, screenshots)
./gradlew :app:lintDebug                  # 53 warnings baseline
./gradlew :app:verifyRoborazziDebug       # screenshot verify
./gradlew :app:recordRoborazziDebug       # (re)record
./gradlew :app:prepareImagesForAI         # downscale 50% JPG + images_manifest.json
```

## Usage

- **Record:** Tap red mic `RecordTriggerButton` (`MainScreen.kt:702`); when recording, pause/resume via tonal button left of stop, timer `tnum` `MainScreen.kt:429` + pulse. Revocation mid-record stops cleanly.
- **Search/filter:** `OutlinedTextField` `search_field` filters name/tags/transcript; `FilterChip` `chip_all`/`chip_$folder` (derived `اجتماعات/محاضرات/...` `MainViewModel.kt:309`).
- **Play:** Tap play circle `play_pause_${hash}`; slider `شريط تقدم`; becoming-noisy pauses.
- **Card actions:** `share` (FileProvider `.fileprovider` `AndroidManifest.xml:30`), `rename` (sanitized `^[\\p{L}0-9 _-]{1,64}$`), `قص` (trim dialog dual Slider → `trimRecording` copy), `ذكاء`/`عرض النسخ` (transcribe via Gemini or show sheet), `delete`.
- **AI:** Badge `سلسلة ذكية` tappable → consent `AlertDialog` → switch `ai_enable_button`; banner shows `جاري المعالجة`; per-card `ai_*`; `ModalBottomSheet` transcript with `نسخ` (Clipboard) + `مشاركة` (ACTION_SEND text/plain), keyword search over transcripts live.
- **Export all:** Header `مشاركة الكل` `share_all_button` → `ACTION_SEND_MULTIPLE`.

## Testing & Quality

- **Unit:** `RecordingUtilsTest.kt:1` (format* locale ar/en), `GeminiServiceTest.kt:1` (mock `runTest` temp file), `ExampleUnitTest.kt:6`, `ExampleRobolectricTest.kt:13`
- **Screenshot (Roborazzi, `ar-...-420dpi`, sdk 36, `NATIVE`):** `HomeScreenshotTest.kt:20` (empty/recordings/recording/paused, light/dark, en/ar), `PermissionScreenshotTest.kt:18`, `DialogsScreenshotTest.kt:22` (stable hash tags), `DeviceMatrixScreenshotTest.kt:18` (tablet 1280x800, watch round), `SearchScreenshotTest.kt:1`, `AiScreenshotTest.kt:1` (enabled/processing/sheet)
- **Lint:** `lint { abortOnError false }` + `lint-baseline.xml:1`, `HorizontalDivider` fix, `isCoreLibraryDesugaringEnabled` compatible `prepareImagesForAI` `inputs/outputs` (`build.gradle.kts:141`), `fallbackToDestructiveMigration(dropAllTables=true)` fix.
- **R8:** `isMinifyEnabled+isShrinkResources=true` `build.gradle.kts:44` + keeps `proguard-rules.pro:1`, verified `assembleDebug`.
- **A11y/perf:** `Brush` cached `remember` `MainScreen.kt:800`, stable `testTag` hash, `semantics` `contentDescription`/`heading` for timer/waveform/slider/section.

## Permissions & Backup

- **Manifest:** `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (`AndroidManifest.xml:6`), `provider` `${applicationId}.fileprovider` + `file_paths.xml:3` `recordings/`, `service` `RecordingService` `microphone`.
- **Backup:** `backup_rules.xml:1` / `data_extraction_rules.xml:1` `<exclude domain="file" path="recordings/" />`; transcripts via Room (future encrypted).

## Troubleshooting

- **Share crash `IllegalArgumentException`:** Ensure manifest `${applicationId}.fileprovider` matches `packageName` (fixed Phase 0); clean `rm -rf app/build`.
- **Pause not working < API24:** Device < N shows Snackbar `"غير مدعوم"` (`MainViewModel.kt:391`).
- **Gemini mock vs live:** Placeholder key → deterministic mocks per file name; set real `GEMINI_API_KEY` and rebuild; network errors fallback to mock + `Log.w`.
- **KSP NPE in daemon:** Known `FileDocumentManager` NPE, harmless; `BUILD SUCCESSFUL`.
- **R8 missing class:** Check `proguard-rules.pro` keeps; disable minify with `-PdisableMinify` if needed.

## Contributing

See `ROADMAP.md:119` and `docs/`:
- Branch from `main`, `./gradlew testDebugUnitTest verifyRoborazziDebug lintDebug`
- UI change → add/update `@Test` in `screenshot/*` → `./gradlew recordRoborazziDebug && prepareImagesForAI`
- Keep `metadata.json:2` description in sync.

## License

No license file — treat as internal. Add `LICENSE` before public release.
