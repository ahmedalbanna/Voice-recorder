package com.example.screenshot

import androidx.compose.runtime.Composable
import com.example.data.TranscriptEntity
import com.example.ui.MainScreenContent
import com.example.ui.MainUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AI opt-in banner + transcript sheet (Phase 3 + 4).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-420dpi")
class AiScreenshotTest : ScreenshotTestBase() {

    private val transcript = TranscriptEntity(
        filePath = "/data/data/com.example/files/recordings/اجتماع.m4a",
        transcript = "مرحبا، اجتماع الفريق ناقشنا الخطة واتفقنا على التسليم الخميس.",
        summary = "• الملخص 1: ناقشنا الخطة\n• الملخص 2: التسليم الخميس",
        actionItems = "- أحمد يراجع النماذج\n- سارة تجهز العرض",
        titleSuggestion = "اجتماع الفريق",
        language = "ar"
    )

    private val aiEnabled: @Composable () -> Unit = {
        MainScreenContent(
            uiState = MainUiState(recordings = ScreenshotFixtures.fakeRecordings()),
            aiOptIn = true,
            transcripts = mapOf(transcript.filePath to transcript)
        )
    }

    private val aiProcessing: @Composable () -> Unit = {
        MainScreenContent(
            uiState = MainUiState(recordings = ScreenshotFixtures.fakeRecordings()),
            aiOptIn = true,
            aiProcessing = setOf(transcript.filePath)
        )
    }

    private val withSheet: @Composable () -> Unit = {
        MainScreenContent(
            uiState = MainUiState(recordings = ScreenshotFixtures.fakeRecordings()),
            aiOptIn = true,
            transcripts = mapOf(transcript.filePath to transcript),
            selectedTranscript = transcript
        )
    }

    @Test
    fun ai_enabled_dark() {
        capture("ai/ai-enabled-dark-ar.png", true, aiEnabled)
    }

    @Test
    fun ai_processing_dark() {
        capture("ai/ai-processing-dark-ar.png", true, aiProcessing)
    }

    @Test
    fun transcript_sheet_dark() {
        // Sheet is ModalBottomSheet — capture with sheet open. Freeze clock to avoid shimmer.
        setContentWithTheme(darkTheme = true, content = withSheet)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(800)
        captureCurrent("ai/transcript-sheet-dark-ar.png")
    }
}
