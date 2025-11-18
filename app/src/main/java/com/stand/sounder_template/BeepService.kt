package com.stand.sounder_template

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
    private lateinit var preferences: SharedPreferences
    
    // 中断模式状态
    private var isInterruptMode = false
    
    private val notificationDeleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAllAudio()
            stopForeground(true)
            stopSelf()
            Toast.makeText(this@BeepService, "服务已停止", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 新增：切换中断模式的广播接收器
    private val toggleInterruptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            toggleInterruptMode()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BeepService = this@BeepService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        preferences = getSharedPreferences("BeepSettings", Context.MODE_PRIVATE)
        isInterruptMode = preferences.getBoolean("interrupt_mode", false)
        
        registerReceiver(
            notificationDeleteReceiver,
            IntentFilter("com.stand.sounder_template.NOTIFICATION_DELETED")
        )
        // 注册切换中断模式的广播
        registerReceiver(
            toggleInterruptReceiver,
            IntentFilter("com.stand.sounder_template.TOGGLE_INTERRUPT")
        )
    }

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationDeleteReceiver)
        unregisterReceiver(toggleInterruptReceiver)
        stopAllAudio()
    }

    private fun stopAllAudio() {
        for (player in livePlayers) {
            if (player.isPlaying) player.stop()
            player.release()
        }
        livePlayers.clear()
    }

    fun playParallelBeep() {
        // 中断模式：播放前停止所有音频
        if (isInterruptMode) {
            stopAllAudio()
        }
        
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

    // 新增：切换中断模式
    private fun toggleInterruptMode() {
        isInterruptMode = !isInterruptMode
        preferences.edit().putBoolean("interrupt_mode", isInterruptMode).apply()
        
        // 更新通知
        startForeground(notificationId, createNotification())
        
        val modeText = if (isInterruptMode) getString(R.string.interrupt_mode_on) else getString(R.string.interrupt_mode_off)
        Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
    }

    private fun createNotification(): Notification {
        val channelId = getString(R.string.channel_id)
        val channelName = getString(R.string.app_name)
        val text = getString(R.string.notice_title)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val deleteIntent = Intent("com.stand.sounder_template.NOTIFICATION_DELETED")
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            deleteIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        // 新增：切换中断模式的PendingIntent
        val toggleIntent = Intent("com.stand.sounder_template.TOGGLE_INTERRUPT")
        val togglePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            toggleIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        // 中断模式状态文本
        val interruptActionText = if (isInterruptMode) 
            getString(R.string.interrupt_mode_on) 
        else 
            getString(R.string.interrupt_mode_off)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(text)
            .setContentText(getString(R.string.toggle_interrupt_hint))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDeleteIntent(deletePendingIntent)
            // 添加操作按钮
            .addAction(
                R.mipmap.ic_launcher, 
                interruptActionText, 
                togglePendingIntent
            )
            .build()
    }
}