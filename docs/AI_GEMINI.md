# AI & Gemini — صدى (Sada)

> Phase 3 — `GeminiService.kt:1`, `TranscriptEntity.kt:1`, `AiPrefs.kt:1`, `MainViewModel.kt:233` AI state.

## Overview

- **Model:** `gemini-2.5-flash` `generativelanguage.googleapis.com/v1beta/...:generateContent?key=GEMINI_API_KEY`
- **Key:** `BuildConfig.GEMINI_API_KEY` via `secrets-gradle-plugin` 2.0.1 `app/build.gradle.kts:69` reading `.env` / `.env.example` (`GEMINI_API_KEY=MY_GEMINI_API_KEY` mock). Server-side proxy recommended for production (`ROADMAP.md:73`), direct `OkHttp` for MVP.
- **Mock fallback:** When `apiKey == "MY_GEMINI_API_KEY"` or `length<20` or network fails → deterministic Arabic mocks `GeminiService.kt:120` per `file.name` (`اجتماع` → meeting transcript, `محاضرة` → lecture, else generic). Enables AI Studio demo without live key; `ROADMAP.md:77`.

## Data

- **Room v2** `AppDatabase.kt:1` `version=2` `fallbackToDestructiveMigration(dropAllTables=true)`:
  - `RecordingEntity.kt:1` `filePath PK, name, folder, tags, durationMs, fileSizeBytes, lastModified`
  - `TranscriptEntity.kt:1` `filePath PK, transcript, summary, actionItems, titleSuggestion, language ar, createdAt` + `TranscriptFts` `@Fts4(contentEntity=TranscriptEntity)` `transcripts_fts`
  - `TranscriptDao.kt:1` `getByPath`, `getAll():Flow`, `search(query) LIKE`, `ftsSearch(query) MATCH`, `insert`, `deleteByPath`
- **Prefs:** `AiPrefs.kt:1` `DataStore` `ai_prefs` `ai_opt_in:Flow<Boolean>` `booleanPreferencesKey`, opt-in `MainViewModel.kt:285` (`aiPrefs.aiOptIn.collect` → `_aiOptIn`).
- **Backup:** `recordings/` excluded `backup_rules.xml:1` + `data_extraction_rules.xml:1`; transcripts Room DB remains (future encrypted per `ROADMAP.md:84`).
- **Consent:** `MainScreen.kt:310` banner + `HeaderSection` badge tap → `AlertDialog` `showAiConsent` with Arabic consent text, `onToggleAiOptIn` sets `AiPrefs`. Revoke via switch/badge toggle off → `AnalyticsHelper` + Snackbar.

## Service

`GeminiService.kt:1` `OkHttpClient` 15/30s timeouts, `apiKey` getter try `BuildConfig` else mock, `isMockMode`.

- `suspend fun transcribe(file: File): String` `Dispatchers.IO` — if mock → `mockTranscript(file)` deterministic per name/category; else read `file.readBytes` cap 750KB (45s @128kbps), `Base64.NO_WRAP`, prompt Arabic verbatim `[غير واضح]` fallback, `mimeFor(file)` `audio/mp4/mpeg/wav`, `generateContent(prompt, b64, mime)`, `ifBlank` mock.
- `summarize(transcript:String):String` — 3-bullet Arabic prompt, mock `mockSummary`.
- `extractActionItems(transcript):List<String>` — prompt bullet `- `, parse lines `startsWith("-"/"•")`, mock per category.
- `suggestTitle(transcriptOrName:String):String` — 3-5 words Arabic, take first line `60 chars`, mock `clean` mapping.
- `private fun generateContent(prompt, audioB64?, mime):String` builds `JSONObject` `contents[parts[text, inline_data]]` + `generationConfig{temperature 0.2, maxOutputTokens 1024}` → `POST` `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey` `application/json`, parses `candidates[0].content.parts[0].text`.

**Logging:** `Log.w` on HTTP `!isSuccessful` or exception, fallback mock.

## ViewModel Integration — `MainViewModel.kt:233`

- **State:** `_aiOptIn:Flow`, `_aiProcessing:Set<String>`, `_transcripts:Map<String,TranscriptEntity>`, `_selectedTranscript:TranscriptEntity?` `MainViewModel.kt:285`.
- **Collector `init`:** `aiPrefs.aiOptIn.collect → _aiOptIn`, `transcriptDao.getAll.collect → _transcripts` `associateBy filePath` + `applyFilters()` if search non-empty.
- **`requestTranscription(recording)`** `MainViewModel.kt:750` guards `aiOptIn` (Snackbar if false), `aiProcessing.contains(path)` dedup, cached `transcripts[path]` → `selectedTranscript` + Snackbar, else `viewModelScope.launch(IO)` ` _aiProcessing+path`, `startMs=SystemClock.elapsedRealtime()`, `gemini.transcribe` → `summarize` → `extractActionItems` → `suggestTitle` → `TranscriptEntity` → `transcriptDao.insert` → `AnalyticsHelper.logAiTranscription(duration, true)` → `Main` `selectedTranscript` + Snackbar `تم النسخ والتلخيص: title`; `catch` → `logAiTranscription(..., false)` + Snackbar; `finally` `aiProcessing - path`.
- **Auto-transcribe on `stopRecording`:** captures `activeRecordingFile` before `null`, after `loadRecordings()` if `justRecordedFile.exists && _aiOptIn.value` → `delay(600)` → `RecordingItem(justRecordedFile...)` → `requestTranscription` `MainViewModel.kt:420`.
- **Search:** `applyFilters()` checks `_transcripts[filePath]` `transcript/summary/actionItems/titleSuggestion` lowercase `contains(query)` `MainViewModel.kt:233`.
- **Delete/rename:** `deleteRecording` also `transcriptDao.deleteByPath`, `rename` deletes old transcript.

## UI — `MainScreen.kt:310`

- **Opt-in banner:** `Card` `accentActivePill` when `aiOptIn` else `cardSurface`, `Row` text `الذكاء مفعّل ✓`/`فعّل الذكاء` + subtitle, `Switch` or `CircularProgressIndicator` when `aiProcessing` non-empty, `Button` `تفعيل` `testTag("ai_enable_button")` → `showAiConsent`.
- **Badge:** `HeaderSection.kt:849` `Box` `clickable` `onAiClick` → `if (!aiOptIn) showAiConsent=true else onToggleAiOptIn(false)`, background `accentActivePill` when enabled + `processingCount` text `جاري المعالجة ($n)` / `سلسلة ذكية`.
- **Per-card AI:** `RecordingItemCard.kt:1215` `ActionTextButton` `AutoAwesome/Description` `ذكاء/عرض النسخ/جاري...` color `accentActivePill/textHint/playbackGold`, `hasTranscript` `transcripts.containsKey(path)` `isTranscribing` `aiProcessing.contains(path)` → `onRequestTranscription` (handles cached).
- **Consent dialog:** `if (showAiConsent) AlertDialog` Arabic consent, `موافق، فعّل` `onToggleAiOptIn(true)`.
- **Bottom sheet:** `selectedTranscript?.let` `ModalBottomSheet` `skipPartiallyExpanded` `containerColor cardSurface`, `Column` `verticalScroll` `titleSuggestion`, `transcript` `Card` `background`/`cardBorder`, `summary` `Card`, `actionItems` `Card`, `Row` `OutlinedButton` `نسخ` `ClipboardManager` + `Button` `مشاركة` `ACTION_SEND text/plain` `MainScreen.kt:920` `onCopyTranscript`/`onShareTranscript` via `MainScreen.kt:214`.

## Search & Trim Interaction

- Search `OutlinedTextField` `search_field` filters includes transcript; `trim` dialog `MainScreen.kt:798` not AI-related but uses `MiniWaveformPreview` 20 bars hash-derived `MainScreen.kt:962` shared with AI preview.

## Testing

- `GeminiServiceTest.kt:1` `runTest` temp file `transcribe` mock Arabic `contains("اجتماع")`, `summarize` `contains("•")`, etc., placeholder key.
- `AiScreenshotTest.kt:1` captures `ai-enabled-dark`, `ai-processing-dark`, `transcript-sheet-dark` (`ModalBottomSheet` with `mainClock` freeze) via `MainScreenContent` `aiOptIn`/`transcripts`/`selectedTranscript`.
- `ViewModel` AI flows testable via `TestDispatcher` + `TemporaryFolder` (future `ROADMAP.md:91`).

## Security & Cost

- **Placeholder:** `MY_GEMINI_API_KEY` committed in `.env.example` safe; real key via `.env` + `secrets` not in git (`.gitignore` `.env`).
- **Production:** Proxy through backend to keep key server-side (`metadata.json:5` `SERVER_SIDE_GEMINI_API`, `firebase-ai` commented `build.gradle.kts:106`), add `SafetyNet`/`App Check`.
- **Cost/latency:** Limit 750KB (~45s) `GeminiService.kt:40`, chunk upload + VAD pre-filter in `ROADMAP.md:114` future, `generationConfig` `maxOutputTokens 1024`.

## Future

- FTS triggers auto-populate `transcripts_fts` (currently manual `LIKE`), transcript encryption (`EncryptedSharedPreferences`/SQLCipher), `Media3 Transformer` real audio trim vs copy.
