package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var isRecording = false

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
            true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording: ${e.message}", e)
            recorder?.release()
            recorder = null
            isRecording = false
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
        }
    }

    fun isRecording(): Boolean = isRecording

    fun getMaxAmplitude(): Int {
        if (!isRecording) return 0
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
