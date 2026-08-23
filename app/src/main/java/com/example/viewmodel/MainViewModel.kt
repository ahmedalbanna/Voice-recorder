package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiService
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.data.AiPrefs
import com.example.data.AppDatabase
import com.example.data.RecordingEntity
import com.example.data.TranscriptEntity
import com.example.model.RecordingItem
import com.example.service.PlaybackNotificationHelper
import com.example.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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

    // Audio focus (1.1)
    private val db = AppDatabase.get(context)
    private val dao = db.recordingDao()
    private val transcriptDao = db.transcriptDao()
    private val aiPrefs = AiPrefs(context)
    private val gemini = GeminiService()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeTransientLoss = false
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss: stop playback/recording
                if (_isPlaying.value) {
                    viewModelScope.launch { stopPlaying() }
                    _operationError.value = "توقف التشغيل — فقدان تركيز الصوت"
                }
                if (_isRecording.value) {
                    stopRecording()
                    _operationError.value = "توقف التسجيل — مقاطعة صوتية"
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_isPlaying.value) {
                    wasPlayingBeforeTransientLoss = true
                    audioPlayer.pause()
                    _isPlaying.value = false
                    playbackProgressJob?.cancel()
                    updateListWithPlaybackState()
                } else {
                    wasPlayingBeforeTransientLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeTransientLoss && !_isPlaying.value && _currentlyPlayingFile.value != null) {
                    audioPlayer.resume()
                    _isPlaying.value = true
                    wasPlayingBeforeTransientLoss = false
                    startPlaybackProgressTimer()
                    updateListWithPlaybackState()
                }
            }
        }
    }

    private fun requestAudioFocusForRecording(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "requestAudioFocusForRecording failed", e)
            true // don't block recording on focus failure
        }
    }

    private fun requestAudioFocusForPlayback(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "requestAudioFocusForPlayback failed", e)
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusListener)
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "abandonAudioFocus failed", e)
        }
    }

    // Becoming noisy receiver (1.1) — pause on headphone unplug
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && _isPlaying.value) {
                audioPlayer.pause()
                _isPlaying.value = false
                playbackProgressJob?.cancel()
                playbackProgressJob = null
                updateListWithPlaybackState()
            }
        }
    }
    private var noisyReceiverRegistered = false
    private fun registerNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            try {
                context.registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                noisyReceiverRegistered = true
            } catch (e: Exception) {
                Log.w("MainViewModel", "registerNoisyReceiver failed", e)
            }
        }
    }
    private fun unregisterNoisyReceiver() {
        if (noisyReceiverRegistered) {
            try {
                context.unregisterReceiver(becomingNoisyReceiver)
            } catch (e: Exception) {
                // ignore
            }
            noisyReceiverRegistered = false
        }
    }

    // State flows
    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    // Search / folder (Phase 2)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q; applyFilters() }
    fun setSelectedFolder(folder: String?) { _selectedFolder.value = folder; applyFilters() }
    private var allRecordingsCache: List<RecordingItem> = emptyList()

    // AI state (Phase 3)
    private val _aiOptIn = MutableStateFlow(false)
    val aiOptIn: StateFlow<Boolean> = _aiOptIn.asStateFlow()
    private val _aiProcessing = MutableStateFlow<Set<String>>(emptySet())
    val aiProcessing: StateFlow<Set<String>> = _aiProcessing.asStateFlow()
    private val _transcripts = MutableStateFlow<Map<String, TranscriptEntity>>(emptyMap())
    val transcripts: StateFlow<Map<String, TranscriptEntity>> = _transcripts.asStateFlow()
    private val _selectedTranscript = MutableStateFlow<TranscriptEntity?>(null)
    val selectedTranscript: StateFlow<TranscriptEntity?> = _selectedTranscript.asStateFlow()

    fun setAiOptIn(enabled: Boolean) {
        viewModelScope.launch { aiPrefs.setAiOptIn(enabled) }
        _aiOptIn.value = enabled
        if (!enabled) {
            // Clear sensitive hint
            _operationError.value = "تم إيقاف ميزات الذكاء الاصطناعي"
        } else {
            _operationError.value = "تم تفعيل الذكاء الاصطناعي — التسجيلات ستُنسخ تلقائياً"
        }
    }

    fun selectTranscriptFor(filePath: String) {
        viewModelScope.launch {
            val entity = transcriptDao.getByPath(filePath) ?: _transcripts.value[filePath]
            _selectedTranscript.value = entity
        }
    }

    fun clearSelectedTranscript() { _selectedTranscript.value = null }

    private fun applyFilters() {
        val query = _searchQuery.value.trim().lowercase(Locale.getDefault())
        val folder = _selectedFolder.value
        var list = allRecordingsCache
        if (query.isNotEmpty()) {
            // Keyword search over name, tags, and transcripts (Phase 3)
            list = list.filter {
                it.name.lowercase(Locale.getDefault()).contains(query) ||
                    deriveTags(it.name).lowercase(Locale.getDefault()).contains(query) ||
                    _transcripts.value[it.file.absolutePath]?.let { tr ->
                        tr.transcript.lowercase(Locale.getDefault()).contains(query) ||
                            tr.summary.lowercase(Locale.getDefault()).contains(query) ||
                            tr.actionItems.lowercase(Locale.getDefault()).contains(query) ||
                            tr.titleSuggestion.lowercase(Locale.getDefault()).contains(query)
                    } == true
            }
        }
        if (folder != null) {
            list = list.filter { deriveFolder(it.name) == folder }
        }
        _recordings.value = list
        updateListWithPlaybackState()
    }

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
    private var playbackStartElapsedMs: Long = 0L
    private var playbackStartPosMs: Int = 0

    // Temp file used for active recording
    private var activeRecordingFile: File? = null
    private var recordingStartElapsedMs: Long = 0L
    private var pauseStartElapsedMs: Long = 0L

    // Operation feedback for UI (Snackbar) — null when idle
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    fun consumeOperationError() {
        _operationError.value = null
    }

    init {
        loadRecordings()
        registerNoisyReceiver()
        // Observe folders from Room (Phase 2) — fallback to file-derived if empty
        viewModelScope.launch {
            dao.getFolders().collect { dbFolders ->
                if (dbFolders.isNotEmpty()) {
                    _folders.value = dbFolders
                }
            }
        }
        // AI opt-in + transcripts (Phase 3)
        viewModelScope.launch {
            aiPrefs.aiOptIn.collect { _aiOptIn.value = it }
        }
        viewModelScope.launch {
            transcriptDao.getAll().collect { list ->
                _transcripts.value = list.associateBy { it.filePath }
                // Re-apply filters so keyword search over transcripts updates live
                if (_searchQuery.value.isNotEmpty()) applyFilters()
            }
        }
    }

    private fun deriveFolder(name: String): String = when {
        name.contains("اجتماع") -> "اجتماعات"
        name.contains("محاضرة") -> "محاضرات"
        name.contains("مذكرة") -> "مذكرات"
        name.contains("فكرة") -> "أفكار"
        else -> "عام"
    }

    private fun deriveTags(name: String): String {
        val tags = mutableListOf<String>()
        if (name.contains("اجتماع")) tags.add("اجتماع")
        if (name.contains("محاضرة")) tags.add("محاضرة")
        if (name.contains("مذكرة")) tags.add("مذكرة")
        if (name.contains("فكرة")) tags.add("فكرة")
        if (tags.isEmpty()) tags.add("عام")
        return tags.joinToString(",")
    }

    fun loadRecordings() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                scanRecordingsDirectory()
            }
            allRecordingsCache = items
            // Derive folders from synthetic tag logic (Phase 2)
            val derivedFolders = items.map { deriveFolder(it.name) }.distinct().sorted()
            if (derivedFolders.isNotEmpty()) {
                // Keep Room as source of truth for folders, but update in-memory for now
                if (_folders.value.isEmpty()) _folders.value = derivedFolders
            } else {
                _folders.value = listOf("الكل")
            }
            // Sync to Room (Phase 2) — non-blocking
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val entities = items.map { item ->
                        RecordingEntity(
                            filePath = item.file.absolutePath,
                            name = item.name,
                            folder = deriveFolder(item.name),
                            tags = deriveTags(item.name),
                            durationMs = item.durationMs,
                            fileSizeBytes = item.fileSizeBytes,
                            lastModified = item.file.lastModified()
                        )
                    }
                    if (entities.isNotEmpty()) dao.insertAll(entities)
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Room sync failed", e)
                }
            }
            // Apply active filters
            applyFilters()
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

            val dateString = try {
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("ar"))
                Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).format(formatter)
            } catch (e: Exception) {
                // Fallback for desugaring edge
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(java.util.Date(file.lastModified()))
            }

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

        // Audio focus for recording (1.1)
        requestAudioFocusForRecording()

        val timestamp = try {
            val fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
            LocalDateTime.now().format(fmt)
        } catch (e: Exception) {
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
        }
        // In Arabic strings, prefix has been defined as "تسجيل_" (or Recording_)
        val outputName = "تسجيل_$timestamp.m4a"
        val file = File(recordingsDir, outputName)
        activeRecordingFile = file

        val success = audioRecorder.start(file)
        if (success) {
            _isRecording.value = true
            _isPaused.value = false
            pauseStartElapsedMs = 0L
            _recordingDurationSeconds.value = 0
            _amplitudes.value = emptyList()
            recordingStartElapsedMs = SystemClock.elapsedRealtime()

            // Foreground service to survive backgrounding (1.1)
            try {
                RecordingService.start(context)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to start RecordingService", e)
            }

            // Timer Job — elapsedRealtime base (1.3) with pause handling (2) + revocation guard (1.1)
            recordingTimerJob = viewModelScope.launch {
                while (isActive && audioRecorder.isRecording()) {
                    delay(250)
                    if (_isPaused.value) continue
                    // Revocation check: stop cleanly if RECORD_AUDIO revoked mid-recording
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        stopRecording()
                        _operationError.value = "تم إيقاف التسجيل — تم إلغاء إذن الميكروفون"
                        break
                    }
                    val elapsed = ((SystemClock.elapsedRealtime() - recordingStartElapsedMs) / 1000).toInt().coerceAtLeast(0)
                    _recordingDurationSeconds.value = elapsed
                }
            }

            // Waveform Amplitude Visualizer Job — silence → 0f (1.3) with gap marker when paused (2)
            recordingVisualizerJob = viewModelScope.launch(Dispatchers.Default) {
                while (audioRecorder.isRecording()) {
                    delay(70)
                    if (_isPaused.value) {
                        // Gap marker: insert 0f every iteration while paused
                        _amplitudes.update { current ->
                            val updated = current.toMutableList()
                            if (updated.size > 80) updated.removeAt(0)
                            updated.add(0f)
                            updated
                        }
                        continue
                    }
                    val amp = audioRecorder.getMaxAmplitude()
                    val maxNorm = 32767f
                    val normalized = (amp / maxNorm).coerceIn(0f, 1f)

                    _amplitudes.update { current ->
                        val updated = current.toMutableList()
                        if (updated.size > 80) {
                            updated.removeAt(0)
                        }
                        updated.add(normalized)
                        updated
                    }
                }
            }
        } else {
            abandonAudioFocus()
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            _operationError.value = "الإيقاف المؤقت غير مدعوم على هذا الجهاز"
            return
        }
        val ok = audioRecorder.pause()
        if (ok) {
            _isPaused.value = true
            pauseStartElapsedMs = SystemClock.elapsedRealtime()
        } else {
            _operationError.value = "تعذر إيقاف التسجيل مؤقتاً"
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        val ok = audioRecorder.resume()
        if (ok) {
            // Compensate elapsed base for paused duration
            val pausedMs = SystemClock.elapsedRealtime() - pauseStartElapsedMs
            recordingStartElapsedMs += pausedMs
            pauseStartElapsedMs = 0L
            _isPaused.value = false
        } else {
            _operationError.value = "تعذر استئناف التسجيل"
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        audioRecorder.stop()
        _isRecording.value = false
        _isPaused.value = false
        recordingStartElapsedMs = 0L
        pauseStartElapsedMs = 0L

        recordingTimerJob?.cancel()
        recordingTimerJob = null
        recordingVisualizerJob?.cancel()
        recordingVisualizerJob = null

        // Stop foreground service and abandon focus (1.1)
        try {
            RecordingService.stop(context)
        } catch (e: Exception) {
            Log.w("MainViewModel", "Failed to stop RecordingService", e)
        }
        abandonAudioFocus()

        val justRecordedFile = activeRecordingFile
        activeRecordingFile = null
        loadRecordings()
        // Auto-transcribe if AI opt-in (Phase 3)
        if (justRecordedFile != null && justRecordedFile.exists() && _aiOptIn.value) {
            viewModelScope.launch {
                delay(600)
                val item = RecordingItem(
                    file = justRecordedFile,
                    name = justRecordedFile.nameWithoutExtension,
                    dateString = "",
                    durationMs = 0L,
                    fileSizeBytes = justRecordedFile.length()
                )
                requestTranscription(item)
            }
        }
    }

    // Playback Logic — with lock-screen notification (Phase 2)
    fun togglePlayPause(recording: RecordingItem) {
        val currentPlaying = _currentlyPlayingFile.value
        if (currentPlaying == recording.file) {
            if (_isPlaying.value) {
                audioPlayer.pause()
                _isPlaying.value = false
                playbackProgressJob?.cancel()
                playbackProgressJob = null
                try { PlaybackNotificationHelper.show(context, recording.name, isPlaying = false) } catch (_: Exception) {}
                // Keep focus but allow transient loss handling
            } else {
                // Resume: re-request focus
                if (!requestAudioFocusForPlayback()) {
                    _operationError.value = "تعذر استئناف التشغيل — فقدان تركيز الصوت"
                    return
                }
                audioPlayer.resume()
                _isPlaying.value = true
                playbackStartElapsedMs = SystemClock.elapsedRealtime()
                playbackStartPosMs = audioPlayer.getCurrentPosition()
                startPlaybackProgressTimer()
                try { PlaybackNotificationHelper.show(context, recording.name, isPlaying = true) } catch (_: Exception) {}
            }
        } else {
            // New file selected
            stopPlaying()
            if (!requestAudioFocusForPlayback()) {
                _operationError.value = "تعذر تشغيل الصوت — فقدان تركيز الصوت"
            }
            _currentlyPlayingFile.value = recording.file
            _isPlaying.value = true
            _playbackPositionMs.value = 0
            _playbackDurationMs.value = recording.durationMs.toInt()
            playbackStartElapsedMs = SystemClock.elapsedRealtime()
            playbackStartPosMs = 0

            val success = audioPlayer.play(recording.file) {
                // Done callback
                _isPlaying.value = false
                _currentlyPlayingFile.value = null
                _playbackPositionMs.value = 0
                _playbackProgress.value = 0f
                playbackProgressJob?.cancel()
                playbackProgressJob = null
                playbackStartElapsedMs = 0L
                playbackStartPosMs = 0
                abandonAudioFocus()
                try { PlaybackNotificationHelper.hide(context) } catch (_: Exception) {}
                updateListWithPlaybackState()
            }

            if (success) {
                startPlaybackProgressTimer()
                try { PlaybackNotificationHelper.show(context, recording.name, isPlaying = true) } catch (_: Exception) {}
            } else {
                _isPlaying.value = false
                _currentlyPlayingFile.value = null
                abandonAudioFocus()
                try { PlaybackNotificationHelper.hide(context) } catch (_: Exception) {}
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
        playbackStartElapsedMs = 0L
        playbackStartPosMs = 0
        abandonAudioFocus()
        try { PlaybackNotificationHelper.hide(context) } catch (_: Exception) {}
        updateListWithPlaybackState()
    }

    fun seekTo(progress: Float) {
        val duration = _playbackDurationMs.value
        if (duration > 0) {
            val positionMs = (progress * duration).toInt()
            audioPlayer.seekTo(positionMs)
            _playbackPositionMs.value = positionMs
            _playbackProgress.value = progress
            // Reset elapsed base for accurate ticker (1.1)
            playbackStartElapsedMs = SystemClock.elapsedRealtime()
            playbackStartPosMs = positionMs
            updateListWithPlaybackState()
        }
    }

    private fun startPlaybackProgressTimer() {
        playbackProgressJob?.cancel()
        // Initialize base for drift-corrected ticker
        playbackStartElapsedMs = SystemClock.elapsedRealtime()
        playbackStartPosMs = audioPlayer.getCurrentPosition()
        playbackProgressJob = viewModelScope.launch {
            while (isActive && audioPlayer.isPlaying()) {
                delay(80)
                val dur = audioPlayer.getDuration()
                if (dur > 0) {
                    val elapsed = (SystemClock.elapsedRealtime() - playbackStartElapsedMs).toInt()
                    val predicted = (playbackStartPosMs + elapsed).coerceAtMost(dur)
                    val actual = audioPlayer.getCurrentPosition()
                    // Correct drift >300ms (1.1)
                    val pos = if (abs(predicted - actual) > 300) {
                        playbackStartElapsedMs = SystemClock.elapsedRealtime()
                        playbackStartPosMs = actual
                        actual
                    } else predicted
                    // Reduce churn: only publish if meaningful delta
                    val prevPos = _playbackPositionMs.value
                    if (abs(pos - prevPos) >= 80 || pos == 0 || pos == dur) {
                        _playbackPositionMs.value = pos
                        _playbackDurationMs.value = dur
                        _playbackProgress.value = pos.toFloat() / dur.toFloat()
                        updateListWithPlaybackState()
                    }
                }
            }
        }
    }

    private var lastPlaybackHash: Int = 0
    private fun updateListWithPlaybackState() {
        val playingFile = _currentlyPlayingFile.value
        val playingState = _isPlaying.value
        val progress = _playbackProgress.value
        val pos = _playbackPositionMs.value

        // Simple deduplication to reduce recomposition churn (1.1)
        val hash = (playingFile?.hashCode() ?: 0) * 31 + (if (playingState) 1 else 0) + (progress * 1000).toInt() + pos / 150
        if (hash == lastPlaybackHash && _recordings.value.isNotEmpty()) {
            // still update occasionally but skip excessive no-op
            if (pos % 500 != 0) return
        }
        lastPlaybackHash = hash

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
                    } else {
                        try { dao.deleteByPath(recording.file.absolutePath) } catch (_: Exception) {}
                        try { transcriptDao.deleteByPath(recording.file.absolutePath) } catch (_: Exception) {}
                    }
                } else {
                    _operationError.value = "الملف غير موجود"
                    try { dao.deleteByPath(recording.file.absolutePath) } catch (_: Exception) {}
                    try { transcriptDao.deleteByPath(recording.file.absolutePath) } catch (_: Exception) {}
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
                    } else {
                        try {
                            dao.deleteByPath(recording.file.absolutePath)
                            transcriptDao.deleteByPath(recording.file.absolutePath)
                            val newItem = RecordingItem(
                                file = destFile,
                                name = destFile.nameWithoutExtension,
                                dateString = "",
                                durationMs = 0L,
                                fileSizeBytes = destFile.length()
                            )
                            dao.insert(
                                RecordingEntity(
                                    filePath = destFile.absolutePath,
                                    name = destFile.nameWithoutExtension,
                                    folder = deriveFolder(destFile.nameWithoutExtension),
                                    tags = deriveTags(destFile.nameWithoutExtension),
                                    durationMs = newItem.durationMs,
                                    fileSizeBytes = newItem.fileSizeBytes,
                                    lastModified = destFile.lastModified()
                                )
                            )
                        } catch (_: Exception) {}
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
            _operationError.value = "تعذر مشاركة التسجيل"
        }
    }

    fun shareMultipleRecordings(recordings: List<RecordingItem>, context: Context) {
        if (recordings.isEmpty()) return
        try {
            val authority = "${context.packageName}.fileprovider"
            val uris = recordings.map { FileProvider.getUriForFile(context, authority, it.file) }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة التسجيلات (${recordings.size}) عبر...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Grant read permission for all URIs
            try {
                val resInfo = context.packageManager.queryIntentActivities(chooser, 0)
                for (resolve in resInfo) {
                    val pkg = resolve.activityInfo.packageName
                    uris.forEach { uri -> context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
            } catch (_: Exception) {}
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to share multiple: ${e.message}", e)
            _operationError.value = "تعذر مشاركة التسجيلات"
        }
    }

    fun trimRecording(recording: RecordingItem, startFraction: Float, endFraction: Float) {
        if (startFraction < 0f || endFraction > 1f || startFraction >= endFraction) {
            _operationError.value = "نطاق قص غير صالح"
            return
        }
        val duration = recording.durationMs
        if (duration <= 0L) {
            _operationError.value = "لا يمكن قص هذا التسجيل"
            return
        }
        val selectedMs = ((endFraction - startFraction) * duration).toLong()
        if (selectedMs < 500L) {
            _operationError.value = "المقطع قصير جداً (أقل من ٠٫٥ ثانية)"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val baseName = "${recording.name}_قص"
            val dest = resolveUniqueDestination(baseName, recording.file.extension)
            try {
                // Phase 2 MVP: file copy (actual audio trim via Media3 Transformer in future)
                // For now we duplicate the file and treat as trimmed segment; metadata will show full duration until real trim is implemented
                recording.file.copyTo(dest, overwrite = false)
                // Insert trimmed entry into Room with derived metadata
                dao.insert(
                    RecordingEntity(
                        filePath = dest.absolutePath,
                        name = dest.nameWithoutExtension,
                        folder = deriveFolder(dest.nameWithoutExtension),
                        tags = deriveTags(dest.nameWithoutExtension) + ",مقصوص",
                        durationMs = selectedMs,
                        fileSizeBytes = dest.length(),
                        lastModified = dest.lastModified()
                    )
                )
                withContext(Dispatchers.Main) {
                    _operationError.value = "تم حفظ المقطع: ${dest.nameWithoutExtension}"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "trimRecording failed", e)
                _operationError.value = "تعذر قص التسجيل"
                try { if (dest.exists()) dest.delete() } catch (_: Exception) {}
            }
            loadRecordings()
        }
    }

    // ---- AI Intelligence (Phase 3) ----

    fun requestTranscription(recording: RecordingItem) {
        val path = recording.file.absolutePath
        if (_aiProcessing.value.contains(path)) return
        if (!_aiOptIn.value) {
            _operationError.value = "فعّل ميزات الذكاء الاصطناعي من الشريط العلوي أولاً"
            return
        }
        // Return cached if exists and show it
        _transcripts.value[path]?.let {
            _selectedTranscript.value = it
            _operationError.value = "النسخ موجود مسبقاً: ${it.titleSuggestion.take(30)}"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _aiProcessing.update { it + path }
            try {
                val transcript = gemini.transcribe(recording.file)
                val summary = gemini.summarize(transcript)
                val actionsList = gemini.extractActionItems(transcript)
                val actionItemsStr = if (actionsList.isEmpty()) "" else actionsList.joinToString("\n")
                val title = gemini.suggestTitle(transcript.ifBlank { recording.name })
                val entity = TranscriptEntity(
                    filePath = path,
                    transcript = transcript,
                    summary = summary,
                    actionItems = actionItemsStr,
                    titleSuggestion = title
                )
                transcriptDao.insert(entity)
                withContext(Dispatchers.Main) {
                    _selectedTranscript.value = entity
                    _operationError.value = "تم النسخ والتلخيص: $title"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "requestTranscription failed", e)
                _operationError.value = "تعذر النسخ — ${e.message?.take(60)}"
            } finally {
                _aiProcessing.update { it - path }
            }
        }
    }

    fun deleteTranscript(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { transcriptDao.deleteByPath(path) } catch (_: Exception) {}
            if (_selectedTranscript.value?.filePath == path) _selectedTranscript.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            RecordingService.stop(context)
        } catch (e: Exception) { }
        try { PlaybackNotificationHelper.hide(context) } catch (_: Exception) {}
        audioRecorder.stop()
        audioPlayer.stop()
        abandonAudioFocus()
        unregisterNoisyReceiver()
        recordingTimerJob?.cancel()
        recordingVisualizerJob?.cancel()
        playbackProgressJob?.cancel()
    }
}
