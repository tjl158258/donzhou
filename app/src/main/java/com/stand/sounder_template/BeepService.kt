package com.stand.sounder_template

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BeepService : Service() {
    private val binder = LocalBinder()
    private val notificationId = 1001
    private val channelId = "beep_channel"
    private var currentPlayer: MediaPlayer? = null // 单播放器（中断式）
    private var isInterruptMode = false // 中断模式开关

    // 绑定服务用
    inner class LocalBinder : Binder() {
        fun getService(): BeepService = this@BeepService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, createNotification())
    }

    // 播放音频（中断模式：停止当前再播放新的）
    fun playBeep() {
        if (isInterruptMode) currentPlayer?.stop()
        try {
            val afd = BeepManager.getRandomAudioFd(this)
            currentPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepareAsync()
                setOnPreparedListener {
                    afd.close()
                    start()
                }
                setOnCompletionListener {
                    it.release()
                    currentPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 切换中断模式
    fun toggleInterruptMode(enable: Boolean) {
        isInterruptMode = enable
    }

    // 创建通知（点击跳转到开关界面）
    private fun createNotification(): Notification {
        val switchIntent = Intent(this, SwitchActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, switchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("音频播放中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    // 创建通知渠道（Android 8+）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "音频服务", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPlayer?.release()
    }
}
