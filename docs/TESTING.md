# Testing — صدى (Sada)

> `testDebugUnitTest` (Robolectric + Roborazzi + JUnit) + `lint` + screenshot AI feed `prepareImagesForAI`.

## Unit

- **`RecordingUtilsTest.kt:1`** pure functions `ui/MainScreen.kt:1025` `formatSeconds`, `formatMillis`, `formatFileSize` locale `ar/en` (`Locale.setDefault` Eastern Arabic `٫` handling, `0 ب`/`0 B` checks).
- **`GeminiServiceTest.kt:1`** `ai/GeminiService.kt:1` `runTest` temp `File` `transcribe` mock Arabic, `summarize` bullet `•`, `suggestTitle`, `extractActionItems`; uses placeholder `MY_GEMINI_API_KEY` so no network.
- **`ExampleUnitTest.kt:6`** sanity `2+2`, `ExampleRobolectricTest.kt:13` `ApplicationProvider.getString(R.string.app_name) == "صدى"` `qualifiers="ar"` `sdk 36`.

## Screenshot (Roborazzi 1.59.0, `GraphicsMode.NATIVE`, `sdk 36`)

- **`ScreenshotTestBase.kt:17`** `createComposeRule`, `MyApplicationTheme(darkTheme)` `setContentWithTheme`, `captureRoboImage` `build/outputs/roborazzi/ai_feed/$filePath`.
- **`HomeScreenshotTest.kt:20`** `ar-w412dp-h915dp-420dpi`: `home-empty-light/dark`, `home-recordings-light/dark`, `home-recording-dark/light`, `dark_en`, `paused` (`isPaused=true` copy) — `MainUiState` pure.
- **`PermissionScreenshotTest.kt:18`** `ar` + `en` `w412dp` light/dark via `qualifiers` override.
- **`DialogsScreenshotTest.kt:22`** freeze `mainClock.autoAdvance=false` + `performClick` on stable `hashCode` tags `rename_*`/`delete_*` (`DialogsScreenshotTest.kt:38` `item.file.absolutePath.hashCode()`) → 1500ms advance → `captureCurrent`.
- **`DeviceMatrixScreenshotTest.kt:18`** tablet `w1280dp-h800dp-240dpi` + watch `w227dp-h227dp-round-320dpi`.
- **`SearchScreenshotTest.kt:1`** search query `اجتماع` + folder chips `الكل/اجتماعات` dark/light.
- **`AiScreenshotTest.kt:1`** `aiOptIn`/`transcripts`/`selectedTranscript` → `ai-enabled-dark`, `ai-processing-dark`, `transcript-sheet-dark` (`ModalBottomSheet` with `mainClock` freeze 800ms).
- **`ScreenshotFixtures.kt:1`** deterministic `fakeRecordings()` 3 items (`اجتماع_الفريق`, `مذكرة`, `محاضرة` `isPlaying`), `fakeAmplitudes(45)` `sin` `0.05..0.95`.

One capture per `@Test` (Compose single `setContent`).

## Run

```bash
./gradlew :app:testDebugUnitTest                          # all 34+ tests, BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --tests "com.example.RecordingUtilsTest"
./gradlew :app:testDebugUnitTest --tests "com.example.GeminiServiceTest"
./gradlew :app:lintDebug                                   # HTML app/build/reports/lint-results-debug.html, 53 warnings baseline=true
./gradlew :app:verifyRoborazziDebug                       # diff vs golden
./gradlew :app:recordRoborazziDebug                       # (re)record PNGs
./gradlew :app:prepareImagesForAI                         # 50% JPG + images_manifest.json (config-cache inputs/outputs)
```

`testOptions.unitTests.includeAndroidResources true`, `roborazzi.outputDir` `build/outputs/roborazzi`, `prepareImagesForAI` depends `recordRoborazziDebug`.

## Coverage Gaps (Phase 4)

- `ViewModel` search `applyFilters` + `deriveFolder` not direct unit (tested via screenshot search + manual), future `TestDispatcher` + `TemporaryFolder` `File` I/O + `Room` in-memory `AppDatabase` `getAll` Flow.
- `MainViewModel` audio focus `requestAudioFocus` and `RecordingService` FGS not Robolectric-covered; instrumented `androidTest` `ExampleInstrumentedTest.kt:1` placeholder.
- `MediaRecorder` error codes `AnalyticsHelper` manual.
- Add `clearSelectedTranscript` flow test.

## Bastion

- `lint { abortOnError false, baseline file("lint-baseline.xml") }` `build.gradle.kts:62` + `lint-baseline.xml:1` empty `<issues>`.
- `isMinifyEnabled+isShrinkResources true` release `proguard-rules.pro:1` keeps for `data/**`, `room`, `BuildConfig`.
- Stable `testTag`s `file.absolutePath.hashCode()` `MainScreen.kt:1241` vs previous `item.name`.

## Adding Tests

1. Add file `app/src/test/java/com/example/...Test.kt` `org.junit.Test` + `RunWith(RobolectricTestRunner)` `@Config(sdk=[36], qualifiers="ar")` if Compose, else plain `JUnit`.
2. For UI: extend `ScreenshotTestBase`, define `@Composable` lambda `MainScreenContent(uiState=...)`, `capture("group/name.png", dark, lambda)`.
3. Run `record` then `verify` + `prepareImagesForAI` for AI feed.

## CI

`gradle.properties:21` `caching=true`, `configuration-cache=true`, `workers.max=4`, `kotlin.compiler.execution.strategy=in-process`. Lint not blocking release `checkReleaseBuilds false`.
