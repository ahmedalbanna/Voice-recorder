package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false

    fun start(outputFile: File): Boolean {
        if (isRecording) return false
        
        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                prepare()
                start()
            }
            isRecording = true
            isPaused = false
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording: ${e.message}", e)
            recorder?.release()
            recorder = null
            isRecording = false
            isPaused = false
            false
        }
    }

    /** API 24+ pause — returns false on unsupported or not recording */
    fun pause(): Boolean {
        if (!isRecording || isPaused) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("AudioRecorder", "pause() requires API 24+")
            return false
        }
        return try {
            recorder?.pause()
            isPaused = true
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to pause: ${e.message}", e)
            false
        }
    }

    fun resume(): Boolean {
        if (!isRecording || !isPaused) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            recorder?.resume()
            isPaused = false
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to resume: ${e.message}", e)
            false
        }
    }

    fun stop() {
        if (!isRecording) return
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording correctly: ${e.message}", e)
        } finally {
            recorder?.release()
            recorder = null
            isRecording = false
            isPaused = false
        }
    }

    fun isRecording(): Boolean = isRecording
    fun isPaused(): Boolean = isPaused

    fun getMaxAmplitude(): Int {
        if (!isRecording) return 0
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
