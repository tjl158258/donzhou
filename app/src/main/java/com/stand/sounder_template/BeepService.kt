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
import java.util.Collections

class BeepService : Service() {
    private val binder = LocalBinder()
    private val notificationId: Int = 1
    private val livePlayers = Collections.synchronizedSet(mutableSetOf<MediaPlayer>())
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, "需要通知权限以持续播放音频", Toast.LENGTH_LONG).show()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        playParallelBeep()
        startForeground(notificationId, createNotification())
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

    private fun stopAllAudio() {
        for (player in livePlayers) {
            if (player.isPlaying) player.stop()
            player.release()
        }
        livePlayers.clear()
    }

    // ========== 核心修改：添加中断式播放逻辑 ==========
    fun playParallelBeep() {
        val afd = BeepManager.randomAssetFd(this)
        val mp = MediaPlayer()

        // 开关开启时，停止所有当前音频
        if (SwitchManager.isInterruptPlayEnabled) {
            stopAllAudio()
        }

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

    // ========== 新增：通知栏点击跳转开关界面 ==========
    private fun createNotification(): Notification {
        val channelId = getString(R.string.channel_id)
        val channelName = getString(R.string.app_name)
        val text = getString(R.string.notice_title)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // 通知点击跳转开关界面
        val switchIntent = Intent(this, SwitchControlActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            switchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // 通知删除广播
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
            .setContentIntent(contentPendingIntent) // 点击打开开关界面
            .setDeleteIntent(deletePendingIntent)    // 删除通知停止播放
            .build()
    }
}
