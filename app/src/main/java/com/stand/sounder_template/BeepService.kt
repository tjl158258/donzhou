package com.stand.sounder_template

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import java.util.Collections

class BeepService : Service() {
    private val binder = LocalBinder()
    private val notificationId: Int = 1
    // 管理正在播放的MediaPlayer（线程安全）
    private val livePlayers = Collections.synchronizedSet(mutableSetOf<MediaPlayer>())
    // 通知删除的广播接收器
    private val notificationDeleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 停止所有音频并释放资源
            livePlayers.forEach {
                if (it.isPlaying) it.stop()
                it.release()
            }
            livePlayers.clear()
            // 停止前台服务并销毁自身
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
        playParallelBeep()
        startForeground(notificationId, createNotification())
        return START_NOT_STICKY
    }


    override fun onCreate() {
        super.onCreate()
        // 注册通知删除的广播接收器
        registerReceiver(
            notificationDeleteReceiver,
            IntentFilter("com.stand.sounder_template.NOTIFICATION_DELETED")
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        // 注销广播接收器，避免内存泄漏
        unregisterReceiver(notificationDeleteReceiver)
        // 释放所有剩余资源
        livePlayers.forEach { it.release() }
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

        // Android O及以上创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // 构建“通知被删除”的PendingIntent
        val deleteIntent = Intent("com.stand.sounder_template.NOTIFICATION_DELETED")
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知并绑定删除事件
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDeleteIntent(deletePendingIntent) // 关键：通知被删除时触发广播
            .build()
    }
}
