package com.example.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule

/**
 * Shared plumbing for AI-feed screenshot tests.
 *
 * Output convention (keeps token cost low for AI agents):
 *   build/outputs/roborazzi/ai_feed/<group>/<screen>-<device>-<theme>-<lang>.png
 */
abstract class ScreenshotTestBase {

    @get:Rule
    val composeTestRule: ComposeContentTestRule = createComposeRule()

    protected fun setContentWithTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = darkTheme) {
                content()
            }
        }
    }

    protected fun captureCurrent(filePath: String) {
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/ai_feed/$filePath"
        )
    }

    protected fun capture(fileName: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        setContentWithTheme(darkTheme, content)
        captureCurrent(fileName)
    }
}
