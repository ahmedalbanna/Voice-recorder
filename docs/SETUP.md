# Setup — صدى (Sada)

## Prerequisites

- Android Studio Ladybug+ (AGP 9.1.1)
- JDK 21 (`java -version` 21, `org.gradle.jvmargs=-Xmx4g` `gradle.properties:9`)
- Android SDK Platform 36.1, Build-Tools 36.1, Emulator API 24+
- Git, `GEMINI_API_KEY` (optional, mock fallback)

## Clone

```bash
git clone https://github.com/ahmedalbanna/Voice-recorder.git
cd Voice-recorder
```

Branch `main` is at `669d4ff` (Phase 4). Phases 0–4 `✅ DONE 2026-08-23` `ROADMAP.md:24`.

## Secrets

`secrets` plugin `app/build.gradle.kts:69` reads `.env` → `BuildConfig.GEMINI_API_KEY`:

```bash
cp .env.example .env
# edit .env
GEMINI_API_KEY=YOUR_REAL_GEMINI_KEY  # or MY_GEMINI_API_KEY for mock
```

- Placeholder `MY_GEMINI_API_KEY` → `GeminiService.kt:28` `isMockMode=true` → deterministic mocks per file name (no network). Works in AI Studio.
- Real key: `generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=...` `GeminiService.kt:100`.
- `.env` is `.gitignore`'d, `.env.example` is template.

## Signing

`app/build.gradle.kts:28`:

- `debugConfig` uses `debug.keystore` `android/androiddebugkey` (committed). AI Studio step 5 `Remove signingConfig debugConfig` only for cloud build; keep locally.
- `release` uses env `KEYSTORE_PATH` (default `my-upload-key.jks`), `STORE_PASSWORD`, `KEY_PASSWORD`, `keyAlias upload`. For local release:

```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
export KEYSTORE_PATH=$PWD/my-upload-key.jks STORE_PASSWORD=... KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

`isMinifyEnabled+isShrinkResources=true` `build.gradle.kts:44` with `proguard-rules.pro:1` keeps for Room.

## Build

```bash
./gradlew :app:assembleDebug            # APK app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug              # adb install
./gradlew :app:assembleRelease           # needs keystore env
./gradlew :app:compileDebugKotlin        # quick check (BUILD SUCCESSFUL ~1m30s)
```

`isCoreLibraryDesugaringEnabled` `desugar_jdk_libs:2.1.5` enables `java.time` on API 24 `AppDatabase.kt:24`, `MainViewModel.kt:26`.

## Run

1. Open folder in Android Studio → Sync (Gradle 9.7.1).
2. Select device API 24–36.1.
3. Run `app` → grant `RECORD_AUDIO` (test rationale/Settings flow: deny → “الشرح” → deny+Don’t ask → “فتح الإعدادات” `MainScreen.kt:90`).
4. Record → pause/resume → stop → appears in `filesDir/recordings` (`file_paths.xml:3`). Share uses `${applicationId}.fileprovider` `AndroidManifest.xml:30` verified.

Emulator: use `Microphone` virtual, or physical device for real `getMaxAmplitude` waveform.

## Test

```bash
./gradlew :app:testDebugUnitTest          # 34+ tests (RecordingUtils, GeminiService, Roborazzi)
./gradlew :app:testDebugUnitTest --tests "com.example.RecordingUtilsTest"
./gradlew :app:lintDebug                  # HTML report app/build/reports/lint-results-debug.html (53 warnings baseline)
./gradlew :app:verifyRoborazziDebug      # diff vs golden
./gradlew :app:recordRoborazziDebug      # (re)record screenshots
./gradlew :app:prepareImagesForAI        # 50% JPG + images_manifest.json in build/outputs/ai_feed
```

Screenshot qualifiers `ar-w412dp-h915dp-420dpi` etc., `GraphicsMode.NATIVE` `ScreenshotTestBase.kt:17`, `NATIVE` requires `unitTests.includeAndroidResources=true`.

## Troubleshooting

- **Share crash `IllegalArgumentException`:** Manifest `${applicationId}.fileprovider` must match `packageName` `MainViewModel.kt:868`; `clean` build.
- **Pause unsupported:** `< API24` shows Snackbar `MainViewModel.kt:391`.
- **KSP NPE `FileDocumentManager`:** Daemon AWT NPE harmless, `BUILD SUCCESSFUL`.
- **Desugar error:** Ensure `compileOptions.isCoreLibraryDesugaringEnabled true` and `coreLibraryDesugaring` dep.
- **Lint baseline missing:** `app/lint-baseline.xml` exists empty `<issues>`; update via `./gradlew :app:updateLintBaseline`.
- **Room migration:** `fallbackToDestructiveMigration(dropAllTables=true)` `AppDatabase.kt:24` drops on v1→v2, no manual migration.
- **R8 missing keep:** Check `proguard-rules.pro` keeps for `data/**`, `BuildConfig`.
- **GEMINI_API_KEY not found:** `BuildConfig` not generated if `secrets` misconfigured; check `.env` at root, `propertiesFileName=".env"` `build.gradle.kts:69`.

## CI

`gradle.properties:21` `caching=true`, `configuration-cache=true`, `workers.max=4`, `kotlin.compiler.execution.strategy=in-process`.

For CI release: set `KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD` env, `./gradlew :app:bundleRelease`.
