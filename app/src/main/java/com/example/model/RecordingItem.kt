package com.example.model

import java.io.File

data class RecordingItem(
    val file: File,
    val name: String,
    val dateString: String,
    val durationMs: Long,
    val fileSizeBytes: Long = 0L,
    val isPlaying: Boolean = false,
    val playProgress: Float = 0f,
    val playPositionMs: Int = 0
)
