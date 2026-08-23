package com.example.analytics

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight observability helper (Phase 4).
 * Logs recording/playback events for debugging and future Firebase Analytics/Crashlytics.
 * No network call — pure local logging, so it works without google-services.json.
 */
object AnalyticsHelper {
    private const val TAG = "Analytics"

    private val counters = ConcurrentHashMap<String, Int>()

    fun logRecordingStarted() {
        increment("recording_started")
        Log.i(TAG, "event=recording_started")
    }

    fun logRecordingStopped(durationSec: Int, success: Boolean) {
        increment(if (success) "recording_stopped_success" else "recording_stopped_failure")
        Log.i(TAG, "event=recording_stopped durationSec=$durationSec success=$success")
    }

    fun logRecordingError(code: Int, message: String) {
        increment("recording_error_$code")
        Log.e(TAG, "event=recording_error code=$code message=$message")
    }

    fun logPlaybackStarted(durationMs: Long) {
        increment("playback_started")
        Log.i(TAG, "event=playback_started durationMs=$durationMs")
    }

    fun logPlaybackError(message: String) {
        increment("playback_error")
        Log.e(TAG, "event=playback_error message=$message")
    }

    fun logAiTranscription(durationMs: Long, success: Boolean) {
        increment(if (success) "ai_transcription_success" else "ai_transcription_failure")
        Log.i(TAG, "event=ai_transcription durationMs=$durationMs success=$success")
    }

    private fun increment(key: String) {
        counters.merge(key, 1) { old, _ -> old + 1 }
    }

    fun getCounters(): Map<String, Int> = counters.toMap()
}
