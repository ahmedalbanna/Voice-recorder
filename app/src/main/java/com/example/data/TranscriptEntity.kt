package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey val filePath: String,
    val transcript: String,
    val summary: String = "",
    val actionItems: String = "",
    val titleSuggestion: String = "",
    val language: String = "ar",
    val createdAt: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = TranscriptEntity::class)
@Entity(tableName = "transcripts_fts")
data class TranscriptFts(
    @ColumnInfo(name = "transcript") val transcript: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "actionItems") val actionItems: String,
    @ColumnInfo(name = "titleSuggestion") val titleSuggestion: String
)
