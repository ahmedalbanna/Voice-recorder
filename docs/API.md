# API Reference — صدى (Sada) internal

> Code refs `file:line`, JetBrains `KDoc` in source; no public REST — internal `ViewModel` + `ai` + `data`.

## ViewModel — `viewmodel/MainViewModel.kt:40`

- **`class MainViewModel(application: Application) : AndroidViewModel`**
- **Flows:** `recordings:Flow<List<RecordingItem>>` (`_recordings`), `isRecording/isPaused:Boolean`, `recordingDurationSeconds:Int`, `amplitudes:List<Float>`, `currentlyPlayingFile:File?`, `isPlaying:Boolean`, `playbackProgress:Float`, `playbackPositionMs/PlaybackDurationMs:Int`, `searchQuery:String`, `selectedFolder:String?`, `folders:List<String>`, `operationError:String?`, `aiOptIn:Boolean`, `aiProcessing:Set<String>`, `transcripts:Map<String,TranscriptEntity>`, `selectedTranscript:TranscriptEntity?` — all `asStateFlow` `MainViewModel.kt:194`.
- **CRUD:** `loadRecordings():Unit` `scanRecordingsDirectory()` `MediaMetadataRetriever` + `Instant/DateTimeFormatter` `ar` + Room sync `MainViewModel.kt:305`; `deleteRecording(item)` `MainViewModel.kt:748` + `renameRecording(item, newName)` `sanitizeFileName` `^[\\p{L}0-9 _-]{1,64}$` + `resolveUniqueDestination` `MainViewModel.kt:794`; `shareRecording(item, context)` `FileProvider` `${packageName}.fileprovider` + `ACTION_SEND` `audio/m4a` + `shareMultipleRecordings(list, context)` `ACTION_SEND_MULTIPLE` `audio/*` grant per package.
- **Recording:** `startRecording():Unit` `requestAudioFocusForRecording()` `RecordingService.start()` + timer `SystemClock.elapsedRealtime` 250ms + `_amplitudes` 70ms, `pauseRecording()/resumeRecording()` API24+ `pauseStartElapsedMs` compensation `MainViewModel.kt:391`, `stopRecording()` `RecordingService.stop()` + `abandonAudioFocus()` + `logRecordingStopped` + auto `requestTranscription` if `aiOptIn` `MainViewModel.kt:420`.
- **Playback:** `togglePlayPause(item)`, `stopPlaying()`, `seekTo(progress:Float)` resets `playbackStartElapsedMs/Pos`, `startPlaybackProgressTimer()` 80ms predicted vs actual `>300ms` snap + hash dedup `lastPlaybackHash` `MainViewModel.kt:730`.
- **Search:** `setSearchQuery(q)`, `setSelectedFolder(f)`, `deriveFolder(name)->اجتماعات/.../عام` + `deriveTags` `MainViewModel.kt:309`, `applyFilters()` checks name/tags + `transcripts` map `MainViewModel.kt:233`.
- **Trim:** `trimRecording(item, startFraction:Float, endFraction:Float)` `selectedMs=(end-start)*duration` `>500ms`, `copyTo` `resolveUniqueDestination("${name}_قص")` + `RecordingEntity` `مقصوص` tag `MainViewModel.kt:913`.
- **AI:** `setAiOptIn(Boolean)` `AiPrefs`, `requestTranscription(item)` `aiProcessing` dedup + cached `transcripts[path]` → `selectedTranscript`, else `gemini.transcribe`→`summarize`→`extractActionItems`→`suggestTitle` → `TranscriptEntity` `transcriptDao.insert` + `AnalyticsHelper.logAiTranscription` `MainViewModel.kt:750`, `deleteTranscript(path)`, `selectTranscriptFor(path)`, `clearSelectedTranscript()`.
- **Helpers:** `sanitizeFileName(raw:String):String?` `trim` + `length<=64` + reject `/\` `..` etc. + regex, `resolveUniqueDestination(base, ext):File` `_1.._99`, `updateListWithPlaybackState()` hash dedup, `audioFocusListener` `LOSS/TRANSIENT/GAIN`, `becomingNoisyReceiver` `ACTION_AUDIO_BECOMING_NOISY`.

## Audio — `audio/`

- **`AudioRecorder.kt:9` `class AudioRecorder(context)`** `recorder:MediaRecorder?`, `isRecording/isPaused:Boolean`, `start(outputFile:File):Boolean` `MediaRecorder(context)` API S else deprecated + `MIC/MPEG_4/AAC/128k/44.1k` + `setOnErrorListener` `AnalyticsHelper.logRecordingError` + `prepare/start`, `pause():Boolean` API24+ `recorder.pause()`, `resume():Boolean` `recorder.resume()`, `stop()` `stop/release`, `isRecording()/isPaused()`, `getMaxAmplitude():Int`.
- **`AudioPlayer.kt:7` `class AudioPlayer`** `player:MediaPlayer?`, `currentFile:File?`, `play(file, onCompletion):Boolean` `MediaPlayer` `setDataSource/prepare/start/onCompletion` → `stop()`, `pause()`/`resume()` `isPlaying` check, `stop()` `stop/release`, `isPlaying():Boolean`, `getCurrentPosition/duration():Int`, `seekTo(Int)`, `getCurrentPlayingFile():File?`.

## Data — `data/`

- **`RecordingEntity.kt:1`** `@Entity table recordings` `filePath PK, name, folder, tags, durationMs, fileSizeBytes, lastModified, createdAt`.
- **`RecordingDao.kt:1`** `@Dao` `getAll():Flow<List> ORDER BY lastModified DESC`, `search(query) LIKE`, `getByFolder`, `getFolders DISTINCT folder`, `getByPath`, `insert`, `insertAll`, `deleteByPath`, `clearAll`.
- **`TranscriptEntity.kt:1`** `@Entity transcripts` `filePath PK, transcript, summary, actionItems, titleSuggestion, language ar, createdAt` + `@Fts4(contentEntity=TranscriptEntity)` `TranscriptFts` `transcripts_fts` `transcript/summary/actionItems/titleSuggestion`.
- **`TranscriptDao.kt:1`** `getByPath`, `getAll:Flow`, `search LIKE`, `ftsSearch MATCH` `JOIN transcripts_fts`, `insert`, `deleteByPath`.
- **`AppDatabase.kt:1`** `@Database(entities=[RecordingEntity, TranscriptEntity, TranscriptFts], version=2)` `recordingDao()` `transcriptDao()` `get(context) synchronized fallbackToDestructiveMigration(dropAllTables=true)`.
- **`AiPrefs.kt:1`** `Context.aiDataStore preferencesDataStore("ai_prefs")` `booleanPreferencesKey ai_opt_in` `aiOptIn:Flow<Boolean>` `setAiOptIn(Boolean)`.

## AI — `ai/GeminiService.kt:1`

- **`class GeminiService(client:OkHttpClient)`** `apiKey via BuildConfig.GEMINI_API_KEY` `isMockMode = blank||MY_GEMINI_API_KEY||length<20`.
- `suspend fun transcribe(file:File):String` `Dispatchers.IO` mock `mockTranscript(file)` per name (`اجتماع` etc.) else `file.readBytes` cap 750KB + `Base64` + prompt Arabic verbatim + `generateContent(prompt,b64,mimeFor)` `audio/mp4/mpeg/wav`, `ifBlank` mock, `catch` mock.
- `suspend fun summarize(transcript:String):String` 3-bullet Arabic prompt → `generateContent` or `mockSummary`.
- `suspend fun extractActionItems(transcript:String):List<String>` prompt bullet `- ` → parse `startsWith("-"/"•")` or `mockActionItems`.
- `suspend fun suggestTitle(seed:String):String` 3-5 words Arabic → first line `60 chars` or `mockTitle`.
- `private fun generateContent(prompt, audioB64?, mime):String` `JSONArray` `parts[text, inline_data]` + `generationConfig{temperature0.2,maxOutputTokens1024}` → `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey` `application/json` → parse `candidates[0].content.parts[0].text`.
- `private fun mimeFor(file):String`, `mockTranscript/Summary/ActionItems/Title` deterministic Arabic.

## Services — `service/`

- **`RecordingService.kt:1`** `Service` `onCreate createChannel("recording_channel" LOW)`, `onStartCommand START/STOP` `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MICROPHONE)` API Q+, `NotificationCompat` `ongoing` `contentTitle recording_state_recording` `smallIcon ic_launcher` `addAction pause` `FOREGROUND_SERVICE_IMMEDIATE`, `companion start(context) startForegroundService/startService` + `stop`.
- **`PlaybackNotificationHelper.kt:1`** `object` `playback_channel LOW`, `show(context,title,isPlaying)` `NotificationCompat` `smallIcon ic_launcher` `ongoing` `VISIBILITY_PUBLIC` + `MediaStyle` via `NotificationCompat` (Phase 2 MVP actions open app), `hide`.

## Analytics — `analytics/AnalyticsHelper.kt:1`

- `object AnalyticsHelper` `TAG Analytics` `counters ConcurrentHashMap`, `logRecordingStarted()`, `logRecordingStopped(durationSec, success)`, `logRecordingError(code,message)`, `logPlaybackStarted(durationMs)`, `logPlaybackError`, `logAiTranscription(durationMs, success)`, `getCounters()`.

## UI — `ui/`

- **`MainScreen.kt:64` `data class MainUiState`** `hasMicPermission, recordings, isRecording, isPaused, recordingDurationSeconds, amplitudes, searchQuery, selectedFolder`.
- **`MainScreen.kt:80` `MainScreen(viewModel)`** `collectAsState` all flows incl. `ai*`, permission `hasMicPermission/isPermanentlyDenied/showRationale/hasRequestedBefore` `refreshPermissionState()` `shouldShowRequestPermissionRationale` + `LifecycleEventObserver ON_RESUME`, `launcher RequestPermission`, `MainScreenContent` pass `uiState` + `operationError` + `folders` + `isPermanentlyDenied` + `ai*` + lambdas `onRecordToggle/onPauseToggle/onSearch/onSelectFolder/onShareMultiple/onPlay/onSeek/onDelete/onRename/onTrim/onShare + ai onToggle/onRequest/onDismiss/onCopy/onShareTranscript` (clipboard/share `ACTION_SEND`).
- **`MainScreen.kt:234` `MainScreenContent`** params as above `aiOptIn/Processing/transcripts/selectedTranscript`, `snackbarHostState`, `showAiConsent`, `LaunchedEffect(operationError) Snackbar`, `isRtl = Locale.language in ar/fa/he/ur` (no stale remember), `Scaffold` `HeaderSection(aiOptIn, processingCount, onAiClick)` + `Box` permission vs `Column` (AI banner `Card` `Switch/Progress`, visualizer `160dp` `WaveformVisualizer` cached `Brush`, timer `tnum` + pulse, pause/resume `FilledTonalIconButton` + `RecordTriggerButton`, search `OutlinedTextField` `search_field` + `FilterChip`, section `heading()` + `share_all_button`, `LazyColumn` `recordings_list` `RecordingItemCard` + `rename/delete/trim/AI consent/ModalBottomSheet` dialogs/sheets).
- **`HeaderSection.kt:849` `HeaderSection(aiOptIn, processingCount, onAiClick)`** `Row` app name + subtitle + `Box` badge `clip/border/clickable` `accentActivePill` when enabled `processingCount` text + `CheckCircle`.
- **`PermissionRequestScreen`** `onRequestPermission/onOpenSettings/isPermanentlyDenied/showRationale` title/desc `permission_*` strings, button `Mic/Settings` icon.
- **`RecordTriggerButton.kt:702`** `isPressed` `animateFloatAsState 0.9/1.05`, `size 140dp` `clickable/scale`, `if isRecording` `glowTransition` single `InfiniteTransition` `135→175dp` + `alpha 1→0` `Box` `recordingRed 0.35f`, main shell `100dp` `recordingRed` `shadow` + `stop square` `30dp` `recordingRedDeep` else `Mic` icon.
- **`WaveformVisualizer.kt:800`** `remember(recordingRed, playbackGold){Brush}` cached, `Canvas` `semantics` `مخطط الموجة`, 45 bars `sampleList` `0f` empty, `barHeight 2dp` silence else `coerceIn 4dp..height`, `drawRoundRect` `verticalGradient`.
- **`MiniWaveformPreview.kt:962`** `remember(file.absolutePath)` 20 bars `hashCode` `sin` `0.25..0.95`, `Canvas` `cardBorder`/`recordingRed` per `progress`.
- **`RecordingItemCard.kt:998`** `onPlayToggle/onSeek/onDelete/onRename/onTrim/onAiRequest` `hasTranscript/isTranscribing/onShare` `hashCode` tags `play_pause_*` etc., `Semantics`, `MiniWaveformPreview` `28dp`, `Slider` `contentDescription`, `HorizontalDivider`.
- **Utils:** `formatSeconds(Int)->mm:ss` `Locale.getDefault`, `formatMillis(Long)`, `formatFileSize(Long)` `Locale.getDefault` `ar` `ب/ك.ب/م.ب/ج.ب` else `B/KB/MB/GB` `coerceIn` `MainScreen.kt:1025`.
- **Theme:** `Color.kt:1` `DeepDarkBackground` `CardSurface` etc. + `Light*`, `AppColors.kt:1` `AppColors` `darkAppColors()/lightAppColors()` `LocalAppColors`, `Theme.kt:1` `MyApplicationTheme(darkTheme=isSystemInDarkTheme())` `MaterialTheme` `darkColorScheme/lightColorScheme` + `CompositionLocalProvider`.

## Resources

- `strings.xml:1` `ar` `app_name صدى` `permission_*` `recording_state_*` `smart_badge_label` `recordings_section_title`; `values-en/strings.xml:1` `Sada` `Grant permission` etc.
- `file_paths.xml:3` `files-path recordings/`, `backup_rules.xml`/`data_extraction_rules` exclude `recordings/`.
- `themes.xml` `Theme.MyApplication` `DeviceDefault.NoActionBar`.

## External

- No public REST; Gemini `POST .../generateContent` internal via `GeminiService`.
- `FileProvider` `${applicationId}.fileprovider` `MainViewModel.kt:868` + `ACTION_SEND` `audio/m4a` / `ACTION_SEND_MULTIPLE` `audio/*` grant per package.
