package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val filePath: String,
    val name: String,
    val folder: String = "الكل",
    val tags: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
