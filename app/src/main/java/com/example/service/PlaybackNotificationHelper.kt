package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object PlaybackNotificationHelper {
    private const val CHANNEL_ID = "playback_channel"
    private const val NOTIFICATION_ID = 1002
    private const val ACTION_PAUSE = "com.example.action.PLAYBACK_PAUSE"
    private const val ACTION_RESUME = "com.example.action.PLAYBACK_RESUME"
    private const val ACTION_STOP = "com.example.action.PLAYBACK_STOP"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "التشغيل",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عناصر التحكم في تشغيل الصوت على شاشة القفل"
                setShowBadge(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, title: String, isPlaying: Boolean, progress: Float = 0f) {
        ensureChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context, 10, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // For Phase 2 MVP, actions open the app; full MediaSession control via Media3 will replace this in Phase 3
        val pausePending = PendingIntent.getActivity(context, 11, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val resumePending = PendingIntent.getActivity(context, 12, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopPending = PendingIntent.getActivity(context, 13, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (isPlaying) context.getString(R.string.play_btn_desc) else context.getString(R.string.pause_btn_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(isPlaying)
            .setContentIntent(contentPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isPlaying) {
            builder.addAction(R.mipmap.ic_launcher, context.getString(R.string.pause_btn_desc), pausePending)
        } else {
            builder.addAction(R.mipmap.ic_launcher, context.getString(R.string.play_btn_desc), resumePending)
        }
        builder.addAction(R.mipmap.ic_launcher, context.getString(R.string.delete_btn_desc), stopPending)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    fun hide(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }
}
