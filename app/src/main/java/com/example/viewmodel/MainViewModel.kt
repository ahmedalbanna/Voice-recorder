package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.model.RecordingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val audioRecorder = AudioRecorder(context)
    private val audioPlayer = AudioPlayer()

    // Directories setup
    private val recordingsDir = File(context.filesDir, "recordings").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    // State flows
    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    // Playback States
    private val _currentlyPlayingFile = MutableStateFlow<File?>(null)
    val currentlyPlayingFile: StateFlow<File?> = _currentlyPlayingFile.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0)
    val playbackPositionMs: StateFlow<Int> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0)
    val playbackDurationMs: StateFlow<Int> = _playbackDurationMs.asStateFlow()

    // Jobs for timing
    private var recordingTimerJob: Job? = null
    private var recordingVisualizerJob: Job? = null
    private var playbackProgressJob: Job? = null

    // Temp file used for active recording
    private var activeRecordingFile: File? = null

    // Operation feedback for UI (Snackbar) — null when idle
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    fun consumeOperationError() {
        _operationError.value = null
    }

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                scanRecordingsDirectory()
            }
            _recordings.value = items
            updateListWithPlaybackState()
        }
    }

    private suspend fun scanRecordingsDirectory(): List<RecordingItem> {
        val files = recordingsDir.listFiles { _, name -> name.endsWith(".m4a") || name.endsWith(".mp3") }
            ?: return emptyList()

        // Sort by last modified descending so new ones are on top
        val sortedFiles = files.sortedByDescending { it.lastModified() }

        return sortedFiles.map { file ->
            val retriever = MediaMetadataRetriever()
            var duration = 0L
            try {
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationStr?.toLong() ?: 0L
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to get metadata for ${file.name}", e)
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }

            val dateString = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(file.lastModified()))

            RecordingItem(
                file = file,
                name = file.nameWithoutExtension,
                dateString = dateString,
                durationMs = duration,
                fileSizeBytes = file.length()
            )
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        
        // Stop any playing audio before starting recording
        stopPlaying()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // In Arabic strings, prefix has been defined as "تسجيل_" (or Recording_)
        val outputName = "تسجيل_$timestamp.m4a"
        val file = File(recordingsDir, outputName)
        activeRecordingFile = file

        val success = audioRecorder.start(file)
        if (success) {
            _isRecording.value = true
            _recordingDurationSeconds.value = 0
            _amplitudes.value = emptyList()

            // Timer Job
            recordingTimerJob = viewModelScope.launch(Dispatchers.Default) {
                while (audioRecorder.isRecording()) {
                    delay(1000)
                    _recordingDurationSeconds.value += 1
                }
            }

            // Waveform Amplitude Visualizer Job
            recordingVisualizerJob = viewModelScope.launch(Dispatchers.Default) {
                while (audioRecorder.isRecording()) {
                    delay(70)
                    val amp = audioRecorder.getMaxAmplitude()
                    // Normalize amplitude to scale between 0.0f and 1.0f for visualizer
                    val maxNorm = 32767f
                    val normalized = (amp / maxNorm).coerceIn(0.02f, 1.0f)
                    
                    _amplitudes.update { current ->
                        val updated = current.toMutableList()
                        if (updated.size > 80) { // Limit number of lines in drawing
                            updated.removeAt(0)
                        }
                        updated.add(normalized)
                        updated
                    }
                }
            }
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        audioRecorder.stop()
        _isRecording.value = false

        recordingTimerJob?.cancel()
        recordingTimerJob = null
        recordingVisualizerJob?.cancel()
        recordingVisualizerJob = null

        loadRecordings()
    }

    // Playback Logic
    fun togglePlayPause(recording: RecordingItem) {
        val currentPlaying = _currentlyPlayingFile.value
        if (currentPlaying == recording.file) {
            if (_isPlaying.value) {
                audioPlayer.pause()
                _isPlaying.value = false
            } else {
                audioPlayer.resume()
                _isPlaying.value = true
                startPlaybackProgressTimer()
            }
        } else {
            // New file selected
            stopPlaying()
            _currentlyPlayingFile.value = recording.file
            _isPlaying.value = true
            _playbackPositionMs.value = 0
            _playbackDurationMs.value = recording.durationMs.toInt()

            val success = audioPlayer.play(recording.file) {
                // Done callback
                _isPlaying.value = false
                _currentlyPlayingFile.value = null
                _playbackPositionMs.value = 0
                _playbackProgress.value = 0f
                playbackProgressJob?.cancel()
                playbackProgressJob = null
                updateListWithPlaybackState()
            }

            if (success) {
                startPlaybackProgressTimer()
            } else {
                _isPlaying.value = false
                _currentlyPlayingFile.value = null
            }
        }
        updateListWithPlaybackState()
    }

    fun stopPlaying() {
        audioPlayer.stop()
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        _isPlaying.value = false
        _currentlyPlayingFile.value = null
        _playbackPositionMs.value = 0
        _playbackProgress.value = 0f
        updateListWithPlaybackState()
    }

    fun seekTo(progress: Float) {
        val duration = _playbackDurationMs.value
        if (duration > 0) {
            val positionMs = (progress * duration).toInt()
            audioPlayer.seekTo(positionMs)
            _playbackPositionMs.value = positionMs
            _playbackProgress.value = progress
            updateListWithPlaybackState()
        }
    }

    private fun startPlaybackProgressTimer() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch(Dispatchers.Default) {
            while (audioPlayer.isPlaying()) {
                delay(150)
                val pos = audioPlayer.getCurrentPosition()
                val dur = audioPlayer.getDuration()
                if (dur > 0) {
                    _playbackPositionMs.value = pos
                    _playbackDurationMs.value = dur
                    _playbackProgress.value = pos.toFloat() / dur.toFloat()
                    updateListWithPlaybackState()
                }
            }
        }
    }

    private fun updateListWithPlaybackState() {
        val playingFile = _currentlyPlayingFile.value
        val playingState = _isPlaying.value
        val progress = _playbackProgress.value
        val pos = _playbackPositionMs.value

        _recordings.update { items ->
            items.map { item ->
                if (item.file == playingFile) {
                    item.copy(
                        isPlaying = playingState,
                        playProgress = progress,
                        playPositionMs = pos
                    )
                } else {
                    item.copy(
                        isPlaying = false,
                        playProgress = 0f,
                        playPositionMs = 0
                    )
                }
            }
        }
    }

    fun deleteRecording(recording: RecordingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_currentlyPlayingFile.value == recording.file) {
                withContext(Dispatchers.Main) {
                    stopPlaying()
                }
            }
            try {
                if (recording.file.exists()) {
                    val deleted = recording.file.delete()
                    if (!deleted) {
                        Log.e("MainViewModel", "Failed to delete file: ${recording.file.name} — delete() returned false")
                        _operationError.value = "تعذر حذف التسجيل"
                    }
                } else {
                    _operationError.value = "الملف غير موجود"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete file", e)
                _operationError.value = "تعذر حذف التسجيل"
            }
            loadRecordings()
        }
    }

    /**
     * Sanitizes a user-provided file name (without extension).
     * Allows Arabic/Unicode letters, digits, space, underscore, hyphen.
     * Rejects path separators, traversal, and over-long names.
     * Returns null if invalid.
     */
    private fun sanitizeFileName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) return null
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains('\u0000')) return null
        if (trimmed == "." || trimmed == "..") return null
        if (trimmed.endsWith(".") || trimmed.endsWith(" ")) return null
        // Unicode letters + digits + space, _ , -
        val regex = Regex("^[\\p{L}0-9 _-]+$")
        if (!regex.matches(trimmed)) return null
        return trimmed
    }

    private fun resolveUniqueDestination(baseName: String, extension: String): File {
        var candidate = File(recordingsDir, "$baseName.$extension")
        var counter = 1
        while (candidate.exists() && counter < 100) {
            candidate = File(recordingsDir, "${baseName}_$counter.$extension")
            counter++
        }
        return candidate
    }

    fun renameRecording(recording: RecordingItem, newName: String) {
        val sanitized = sanitizeFileName(newName)
        if (sanitized == null) {
            Log.w("MainViewModel", "Rejected invalid rename: '$newName'")
            _operationError.value = "اسم غير صالح — استخدم أحرف/أرقام و _ - فقط (حتى 64 حرف)"
            return
        }
        // No-op if same name
        if (sanitized == recording.file.nameWithoutExtension) return

        viewModelScope.launch(Dispatchers.IO) {
            val extension = recording.file.extension
            val destFile = resolveUniqueDestination(sanitized, extension)

            // Check if playing
            val isPlayingThis = _currentlyPlayingFile.value == recording.file
            if (isPlayingThis) {
                withContext(Dispatchers.Main) {
                    stopPlaying()
                }
            }

            try {
                if (!recording.file.exists()) {
                    _operationError.value = "الملف غير موجود"
                } else {
                    val success = recording.file.renameTo(destFile)
                    if (!success) {
                        Log.e("MainViewModel", "renameTo failed: ${recording.file.name} -> ${destFile.name}")
                        _operationError.value = "تعذر إعادة التسمية"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to rename file", e)
                _operationError.value = "تعذر إعادة التسمية"
            }
            loadRecordings()
        }
    }

    fun shareRecording(recording: RecordingItem, context: Context) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, recording.file)
            
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "audio/m4a"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة التسجيل عبر...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to share file: ${e.message}", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        audioPlayer.stop()
        recordingTimerJob?.cancel()
        recordingVisualizerJob?.cancel()
        playbackProgressJob?.cancel()
    }
}
