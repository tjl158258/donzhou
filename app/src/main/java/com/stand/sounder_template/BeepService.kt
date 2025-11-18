package com.stand.sounder_template

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class BeepService : Service() {
    private val binder = LocalBinder()
    private val notificationId: Int = 1
    private val livePlayers = Collections.synchronizedSet(mutableSetOf<MediaPlayer>())
    // 通知删除的广播接收器
    private val notificationDeleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAllAudio()
            stopForeground(true)
            stopSelf()
            Toast.makeText(this@BeepService, "音频已停止", Toast.LENGTH_SHORT).show()
        }
    }


    inner class LocalBinder : Binder() {
        fun getService(): BeepService = this@BeepService
    }


    override fun onBind(intent: Intent): IBinder = binder


    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 安卓13及以上，先检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled().not()) {
                // 没有通知权限，提示用户开启（可选逻辑）
                Toast.makeText(this, "需要通知权限以持续播放音频", Toast.LENGTH_LONG).show()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        playParallelBeep()
        // 安卓12及以上，前台服务需要指定类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startForeground(notificationId, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId, createNotification())
        }
        return START_NOT_STICKY
    }


    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            notificationDeleteReceiver,
            IntentFilter("com.stand.sounder_template.NOTIFICATION_DELETED")
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationDeleteReceiver)
        stopAllAudio()
    }


    // 停止所有音频并释放资源
    private fun stopAllAudio() {
        livePlayers.forEach {
            if (it.isPlaying) it.stop()
            it.release()
        }
        livePlayers.clear()
    }


    fun playParallelBeep() {
        val afd = BeepManager.randomAssetFd(this)
        val mp = MediaPlayer()
        livePlayers += mp

        try {
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mp.setOnPreparedListener {
                afd.close()
                it.start()
            }
            mp.setOnCompletionListener {
                it.release()
                livePlayers -= it
            }
            mp.setOnErrorListener { _, _, _ ->
                mp.release()
                livePlayers -= mp
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            mp.release()
            livePlayers -= mp
            afd.close()
            e.printStackTrace()
        }
    }


    private fun createNotification(): Notification {
        val channelId = getString(R.string.channel_id)
        val channelName = getString(R.string.app_name)
        val text = getString(R.string.notice_title)

        // 安卓O及以上创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // 通知被删除的PendingIntent
        val deleteIntent = Intent("com.stand.sounder_template.NOTIFICATION_DELETED")
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            deleteIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDeleteIntent(deletePendingIntent)
            .build()
    }
}
