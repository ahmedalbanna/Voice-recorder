package com.example.screenshot

import com.example.model.RecordingItem
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/**
 * Deterministic fake data shared by all screenshot tests so captures stay
 * reproducible between record/verify runs.
 */
object ScreenshotFixtures {

    fun fakeRecordings(): List<RecordingItem> = listOf(
        RecordingItem(
            file = File("/data/data/com.example/files/recordings/اجتماع_الفريق_الأسبوعي.m4a"),
            name = "اجتماع_الفريق_الأسبوعي",
            dateString = "23/08/2026 10:45",
            durationMs = 185_000L,
            fileSizeBytes = 1_245_184L
        ),
        RecordingItem(
            file = File("/data/data/com.example/files/recordings/مذكرة_صوتية_سريعة.m4a"),
            name = "مذكرة_صوتية_سريعة",
            dateString = "22/08/2026 18:30",
            durationMs = 47_500L,
            fileSizeBytes = 318_464L
        ),
        RecordingItem(
            file = File("/data/data/com.example/files/recordings/محاضرة_تعلم_الآلة.mp3"),
            name = "محاضرة_تعلم_الآلة",
            dateString = "21/08/2026 09:12",
            durationMs = 3_661_000L,
            fileSizeBytes = 34_603_008L,
            isPlaying = true,
            playProgress = 0.42f,
            playPositionMs = 1_537_620
        )
    )

    /** Smooth pseudo-random waveform samples in the 0.05f..0.95f range. */
    fun fakeAmplitudes(barCount: Int = 45): List<Float> =
        List(barCount) { i -> (0.5f + 0.45f * sin(i * 0.55f)).let { abs(it).coerceIn(0.05f, 0.95f) } }
}
