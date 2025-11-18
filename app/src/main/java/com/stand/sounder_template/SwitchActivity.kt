package com.stand.sounder_template

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Switch

class SwitchActivity : Activity() {
    private lateinit var beepService: BeepService
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            beepService = (service as BeepService.LocalBinder).getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch)

        // 绑定服务
        bindService(
            Intent(this, BeepService::class.java),
            connection, Context.BIND_AUTO_CREATE
        )

        // 开关控制中断模式
        findViewById<Switch>(R.id.switch_interrupt).setOnCheckedChangeListener { _, isChecked ->
            if (isBound) beepService.toggleInterruptMode(isChecked)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }
}