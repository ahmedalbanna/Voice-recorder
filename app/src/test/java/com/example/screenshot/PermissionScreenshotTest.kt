package com.example.screenshot

import androidx.compose.runtime.Composable
import com.example.ui.PermissionRequestScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Permission screen captures: phone form factor, Arabic (default) and
 * English locales, each in light + dark themes.
 * Note: one capture per @Test - Compose forbids calling setContent twice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ar-w412dp-h915dp-420dpi")
class PermissionScreenshotTest : ScreenshotTestBase() {

    private fun content(): @Composable () -> Unit = {
        PermissionRequestScreen(onRequestPermission = {})
    }

    @Test
    fun permission_phone_light_ar() {
        capture("permission/permission-phone-light-ar.png", darkTheme = false, content())
    }

    @Test
    fun permission_phone_dark_ar() {
        capture("permission/permission-phone-dark-ar.png", darkTheme = true, content())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h915dp-420dpi")
    fun permission_phone_light_en() {
        capture("permission/permission-phone-light-en.png", darkTheme = false, content())
    }

    @Test
    @Config(sdk = [36], qualifiers = "en-w412dp-h915dp-420dpi")
    fun permission_phone_dark_en() {
        capture("permission/permission-phone-dark-en.png", darkTheme = true, content())
    }
}
