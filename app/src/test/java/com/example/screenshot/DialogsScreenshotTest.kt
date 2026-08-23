package com.example.screenshot

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.MainScreenContent
import com.example.ui.MainUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rename/delete dialog captures, triggered through the real UI test tags.
 * The dialogs contain infinitely animating elements (text cursor blink), so
 * the frame clock is frozen before interacting - the standard Roborazzi
 * pattern for animated screens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-420dpi")
class DialogsScreenshotTest : ScreenshotTestBase() {

    private val item = ScreenshotFixtures.fakeRecordings().first()

    private fun openDialogAndCapture(buttonTag: String, fileName: String) {
        setContentWithTheme(darkTheme = true) {
            MainScreenContent(
                uiState = MainUiState(recordings = ScreenshotFixtures.fakeRecordings())
            )
        }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(buttonTag).performClick()
        composeTestRule.mainClock.advanceTimeBy(milliseconds = 1500)
        captureCurrent(fileName)
    }

    @Test
    fun renameDialog_phone_dark_ar() {
        openDialogAndCapture(
            buttonTag = "rename_${item.file.absolutePath.hashCode()}",
            fileName = "dialogs/dialog-rename-phone-dark-ar.png"
        )
    }

    @Test
    fun deleteDialog_phone_dark_ar() {
        openDialogAndCapture(
            buttonTag = "delete_${item.file.absolutePath.hashCode()}",
            fileName = "dialogs/dialog-delete-phone-dark-ar.png"
        )
    }
}
