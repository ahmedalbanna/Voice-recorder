package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color palette used across the app UI.
 * Provided by [MyApplicationTheme] through [LocalAppColors] so that every
 * screen can react to Light/Dark mode without referencing raw constants.
 */
@Immutable
data class AppColors(
    val background: Color,
    val cardSurface: Color,
    val cardSurfaceActive: Color,
    val cardBorder: Color,
    val accentActivePill: Color,
    val accentOnActiveText: Color,
    val accentLightBlue: Color,
    val recordingRed: Color,
    val recordingRedDeep: Color,
    val recordingRedGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val playbackGold: Color,
    val playbackOnSurface: Color
)

fun darkAppColors(): AppColors = AppColors(
    background = DeepDarkBackground,
    cardSurface = CardSurfaceColor,
    cardSurfaceActive = CardSurfaceActiveColor,
    cardBorder = CardBorderColor,
    accentActivePill = AccentActivePill,
    accentOnActiveText = AccentOnActiveText,
    accentLightBlue = AccentLightBlue,
    recordingRed = RecordingRed,
    recordingRedDeep = RecordingRedDeep,
    recordingRedGlow = RecordingRedGlow,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textHint = TextHint,
    playbackGold = PlaybackGold,
    playbackOnSurface = PlaybackWhite
)

fun lightAppColors(): AppColors = AppColors(
    background = LightBackground,
    cardSurface = LightCardSurfaceColor,
    cardSurfaceActive = LightCardSurfaceActiveColor,
    cardBorder = LightCardBorderColor,
    accentActivePill = LightAccentActivePill,
    accentOnActiveText = LightAccentOnActiveText,
    accentLightBlue = LightAccentBlue,
    recordingRed = LightRecordingRed,
    recordingRedDeep = LightRecordingRedDeep,
    recordingRedGlow = LightRecordingRedGlow,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textHint = LightTextHint,
    playbackGold = LightPlaybackGold,
    playbackOnSurface = LightPlaybackOnSurface
)

val LocalAppColors = staticCompositionLocalOf { darkAppColors() }
