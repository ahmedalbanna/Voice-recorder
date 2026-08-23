# Architecture — صدى (Sada)

> Package `com.aistudio.voicerecorder.voxrec` / namespace `com.example` — Compose + AndroidViewModel + Room + Gemini.

## Layers

```
UI (Compose)  ──collectAsState──>  ViewModel  ──calls──>  Domain/Data
MainScreen.kt:64  MainUiState      MainViewModel.kt:40   AudioRecorder.kt:9 / AudioPlayer.kt:7
HeaderSection     hasMicPermission  recordings:Flow      MediaRecorder / MediaPlayer
WaveformVisualizer isRecording      isPaused              SystemClock elapsed
RecordingItemCard isPaused          transcripts Map       Room AppDatabase.kt:1
PermissionScreen  searchQuery       aiOptIn / aiProcessing  AiPrefs DataStore
                                     RecordingService.kt:1  GeminiService.kt:1
```

- **Unidirectional data flow:** `ViewModel` exposes `StateFlow` (`recordings`, `isRecording`, `amplitudes`, `transcripts` etc.) `MainScreen.kt:90` `collectAsState()` → `MainUiState` → `MainScreenContent` pure renderer (screenshot-testable). Events up via lambdas `onRecordToggle`, `onPauseToggle`, `onRequestTranscription` etc. `MainScreen.kt:162`.

- **Separation:** `AudioRecorder`/`AudioPlayer` pure wrappers (no Android ViewModel), `service/` for FGS, `data/` for Room/DataStore, `ai/` for Gemini, `analytics/` for observability, `ui/theme/` for `LocalAppColors` light/dark `Theme.kt:1`.

## State Management

- **Recording:** `_isRecording`, `_isPaused`, `_recordingDurationSeconds`, `_amplitudes` `MainViewModel.kt:198`, `recordingStartElapsedMs` + `pauseStartElapsedMs` `SystemClock` compensation, `recordingTimerJob` 250ms + revocation check, `recordingVisualizerJob` 70ms `getMaxAmplitude` → `0f..1f`.
- **Playback:** `_currentlyPlayingFile`, `_isPlaying`, `_playbackProgress/Position/Duration` `MainViewModel.kt:250`, `playbackStartElapsedMs`/`playbackStartPosMs` drift-corrected ticker 80ms `>300ms` snap `MainViewModel.kt:289`, `AudioFocusRequest` `LOSS/TRANSIENT/GAIN` + `becomingNoisyReceiver` `MainViewModel.kt:160`.
- **Data:** `_recordings` derived from `allRecordingsCache` + `applyFilters()` (name/tags/transcript + folder `deriveFolder()` `MainViewModel.kt:233`), `_folders`, `_searchQuery`/`_selectedFolder`, `_operationError` `Snackbar` `MainScreen.kt:274`, `aiOptIn` `AiPrefs` + `transcripts` `transcriptDao.getAll` collectors `MainViewModel.kt:285`.
- **Files:** `recordingsDir` `filesDir/recordings` `MainViewModel.kt:48` `mkdirs()`, scan `listFiles{*.m4a/*.mp3}` sorted `lastModified` DESC `scanRecordingsDirectory()` `MediaMetadataRetriever` duration + `Instant`/`DateTimeFormatter` `ar` date `MainViewModel.kt:257`.

## Data Flow — Recording

1. `startRecording()` `MainViewModel.kt:303` checks `isRecording`, `stopPlaying()`, `requestAudioFocusForRecording()`, timestamp `yyyyMMdd_HHmmss` `LocalDateTime`, `File("تسجيل_$ts.m4a")`, `audioRecorder.start(file)` `MediaRecorder` `MIC/MPEG_4/AAC/128k/44.1k` + `OnErrorListener` `AnalyticsHelper` `AudioRecorder.kt:23`, `RecordingService.start()` FGS.
2. Timer/visualizer jobs launch. On `pause` `audioRecorder.pause()` API24+ `isPaused=true` `pauseStartElapsedMs`; on `resume` `resume()` + `recordingStartElapsedMs+=pausedMs`.
3. `stopRecording()` `audioRecorder.stop()` `isRecording=false`, `RecordingService.stop()`, `abandonAudioFocus()`, `AnalyticsHelper.logRecordingStopped`, `loadRecordings()` → scan → `deriveFolder/tags` → `dao.insertAll` → `applyFilters()`, auto `requestTranscription` if `aiOptIn`.

## Data Flow — Playback & AI

- **Playback:** `togglePlayPause()` `MainViewModel.kt:398` handles same-file pause/resume (re-request focus, `playbackStartElapsedMs/Pos`) vs new file `stopPlaying()` → `requestAudioFocusForPlayback()` → `audioPlayer.play(file){onCompletion}` `MediaPlayer` `prepare/start/onCompletion`, `startPlaybackProgressTimer()` `MediaStyle` `PlaybackNotificationHelper.show()`.
- **AI:** `GeminiService.kt:1` `transcribe(file)` base64 `inline_data` `audio/mp4` + Arabic prompt → `generateContent` `gemini-2.5-flash` or mock per `file.name` (`اجتماع` etc.), `summarize`/`extractActionItems`/`suggestTitle` similarly; `MainViewModel.requestTranscription()` `MainViewModel.kt:750` guards `aiOptIn`/`aiProcessing` set, `transcriptDao.insert(TranscriptEntity)` `filePath PK`, FTS `TranscriptFts` `@Fts4`, `search` LIKE + `ftsSearch` `MATCH`; keyword search `applyFilters` checks `_transcripts` map.

## UI Composition

- **Root:** `MainActivity.kt:12` `enableEdgeToEdge` `ViewModelProvider` + `MyApplicationTheme` `isSystemInDarkTheme()` `LocalAppColors` → `MainScreen` `MainScreen.kt:80` permission `LifecycleEventObserver` `ON_RESUME` re-check, `rememberLauncherForActivityResult` `RequestPermission`, `hasRequestedBefore` → `isPermanentlyDenied`/`showRationale`.
- **Content:** `MainScreenContent.kt:234` `isRtl` `Locale.language in ar/fa/he/ur` without `remember`, `Scaffold` `snackbarHost` `topBar HeaderSection(aiOptIn, processingCount, onAiClick)`, `Box` permission vs `Column` (AI banner `Card` `Switch`/`Progress` `ai_enable_button`, visualizer `160dp` `WaveformVisualizer`, timer `tnum` + pulse `rememberInfiniteTransition`, pause/resume `FilledTonalIconButton` + `RecordTriggerButton` FGS glow cached `Brush`, search `OutlinedTextField` `search_field` + `FilterChip` `chip_all`, section `heading()` + `share_all_button`, `LazyColumn` `recordings_list` `key=absolutePath` `RecordingItemCard`).
- **Card:** `RecordingItemCard.kt:998` play circle `play_pause_${hash}` `semantics`, name/date/size/duration, `MiniWaveformPreview` 20 bars hash-derived `progress` highlight `cardBorder`/`recordingRed`, `AnimatedVisibility` `Slider` `contentDescription="شريط تقدم"`, `HorizontalDivider`, footer `Row` `Share/Edit/ContentCut/AutoAwesome` `rename_*`/`trim_*`/`ai_*`/`delete_*` stable hash tags, `share` via `FileProvider`.
- **Dialogs/sheets:** `rename` `Dialog` `OutlinedTextField` `rename_input`, `delete` `Dialog`, `trim` `Dialog` dual `Slider` + `MiniWaveformPreview` 40dp + `formatMillis` range, `AI consent` `AlertDialog`, `ModalBottomSheet` `skipPartiallyExpanded` `transcript/summary/actionItems` `Card`s + `نسخ` `Clipboard` + `مشاركة` `ACTION_SEND`.

## Threading

- `viewModelScope` `Dispatchers.IO` for file scan/Room, `Dispatchers.Default` for visualizer, `Main` for `updateListWithPlaybackState` (hash dedup). Timers use `isActive` + `delay`. `SystemClock.elapsedRealtime` for drift-free.

## Dependencies Direction

`ui` → `viewmodel` → `data`/`audio`/`ai`/`service`/`analytics`; no reverse. `data` and `ai` are pure, testable via `runTest` + temp files / in-memory Room.

## Configuration

- `compileSdk 36.1`, `minSdk 24`, `targetSdk 36`, `isCoreLibraryDesugaringEnabled` `desugar_jdk_libs:2.1.5` for `java.time`, `buildConfig true` for `GEMINI_API_KEY`, `isMinifyEnabled+isShrinkResources true` release with `proguard-rules.pro` keeps, `roborazzi` `outputDir` + `prepareImagesForAI` `inputs/outputs` cache-compatible, `lint` baseline.

## Future

- Media3 `MediaSession` for lock-screen (currently `PlaybackNotificationHelper` `VISIBILITY_PUBLIC`), `WorkManager` for crash finalize, `FTS` triggers auto-populated, transcript encryption, `detekt`/`ktlint`.
