package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayer {
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    fun play(file: File, onCompletion: () -> Unit): Boolean {
        stop()
        currentFile = file
        return try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    onCompletion()
                    stop()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play audio: ${e.message}", e)
            stop()
            false
        }
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to pause playback: ${e.message}", e)
        }
    }

    fun resume() {
        try {
            if (player != null && !player!!.isPlaying) {
                player?.start()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to resume playback: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // Ignore stops on released player
        } finally {
            player?.release()
            player = null
            currentFile = null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            player?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            player?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getDuration(): Int {
        return try {
            player?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            player?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to seek playback: ${e.message}", e)
        }
    }

    fun getCurrentPlayingFile(): File? = currentFile
}
