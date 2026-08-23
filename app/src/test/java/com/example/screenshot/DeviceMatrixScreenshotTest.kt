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
 * Device form-factor matrix via Robolectric resource qualifiers:
 * tablet landscape and a round Wear OS-class screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar-w1280dp-h800dp-240dpi")
class DeviceMatrixScreenshotTest : ScreenshotTestBase() {

    private val recordingsHome: @Composable () -> Unit = {
        MainScreenContent(uiState = MainUiState(recordings = ScreenshotFixtures.fakeRecordings()))
    }

    @Test
    fun captureTablet_dark_ar() {
        capture("home/home-recordings-tablet-dark-ar.png", true, recordingsHome)
    }

    @Test
    @Config(sdk = [36], qualifiers = "ar-w227dp-h227dp-round-320dpi")
    fun captureWatch_dark_ar() {
        capture("home/home-recordings-watch-dark-ar.png", true, recordingsHome)
    }
}
