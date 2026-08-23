package com.example.screenshot

import androidx.compose.runtime.Composable
import com.example.ui.MainScreenContent
import com.example.ui.MainUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Main screen state matrix on a phone: idle-empty, recordings list, and an
 * active recording session with live timer + waveform.
 * One capture per @Test - Compose forbids calling setContent twice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-420dpi")
class HomeScreenshotTest : ScreenshotTestBase() {

    private fun home(state: MainUiState): @Composable () -> Unit = {
        MainScreenContent(uiState = state)
    }

    private val emptyState = MainUiState(hasMicPermission = true)

    private val recordingsState = MainUiState(
        hasMicPermission = true,
        recordings = ScreenshotFixtures.fakeRecordings()
    )

    private val recordingState = MainUiState(
        hasMicPermission = true,
        isRecording = true,
        recordingDurationSeconds = 42,
        amplitudes = ScreenshotFixtures.fakeAmplitudes()
    )

    @Test
    fun homeEmpty_phone_light_ar() {
        capture("home/home-empty-phone-light-ar.png", darkTheme = false, home(emptyState))
    }

    @Test
    fun homeEmpty_phone_dark_ar() {
        capture("home/home-empty-phone-dark-ar.png", darkTheme = true, home(emptyState))
    }

    @Test
    fun homeRecordings_phone_light_ar() {
        capture("home/home-recordings-phone-light-ar.png", darkTheme = false, home(recordingsState))
    }

    @Test
    fun homeRecordings_phone_dark_ar() {
        capture("home/home-recordings-phone-dark-ar.png", darkTheme = true, home(recordingsState))
    }

    @Test
    fun homeRecordingActive_phone_dark_ar() {
        capture("home/home-recording-phone-dark-ar.png", darkTheme = true, home(recordingState))
    }
}
