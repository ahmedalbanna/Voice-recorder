package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): TranscriptEntity?

    @Query("SELECT * FROM transcripts")
    fun getAll(): Flow<List<TranscriptEntity>>

    // Keyword search over transcripts + summary + actionItems via LIKE (FTS in future via transcripts_fts)
    @Query("SELECT * FROM transcripts WHERE transcript LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR actionItems LIKE '%' || :query || '%' OR titleSuggestion LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<TranscriptEntity>

    // FTS search (when FTS table populated via triggers)
    @Query("SELECT t.* FROM transcripts t JOIN transcripts_fts fts ON t.filePath = fts.docid WHERE transcripts_fts MATCH :query")
    suspend fun ftsSearch(query: String): List<TranscriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM transcripts")
    suspend fun clearAll()
}
