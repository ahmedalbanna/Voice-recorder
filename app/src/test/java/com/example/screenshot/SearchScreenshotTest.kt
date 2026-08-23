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
 * Search + folder filter state matrix (Phase 2 + 4).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-420dpi")
class SearchScreenshotTest : ScreenshotTestBase() {

    private val withSearch: @Composable () -> Unit = {
        MainScreenContent(
            uiState = MainUiState(
                recordings = ScreenshotFixtures.fakeRecordings(),
                searchQuery = "اجتماع"
            ),
            folders = listOf("الكل", "اجتماعات", "محاضرات"),
        )
    }

    private val withFolderFilter: @Composable () -> Unit = {
        MainScreenContent(
            uiState = MainUiState(
                recordings = ScreenshotFixtures.fakeRecordings().take(1),
                selectedFolder = "اجتماعات"
            ),
            folders = listOf("اجتماعات", "محاضرات", "عام"),
        )
    }

    @Test
    fun search_ar_dark() {
        capture("search/search-query-dark-ar.png", true, withSearch)
    }

    @Test
    fun search_ar_light() {
        capture("search/search-query-light-ar.png", false, withSearch)
    }

    @Test
    fun folder_filter_dark() {
        capture("search/folder-filter-dark-ar.png", true, withFolderFilter)
    }
}
