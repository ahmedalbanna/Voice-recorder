package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY lastModified DESC")
    fun getAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    fun search(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE folder = :folder ORDER BY lastModified DESC")
    fun getByFolder(folder: String): Flow<List<RecordingEntity>>

    @Query("SELECT DISTINCT folder FROM recordings")
    fun getFolders(): Flow<List<String>>

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecordingEntity>)

    @Query("DELETE FROM recordings WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM recordings")
    suspend fun clearAll()
}
