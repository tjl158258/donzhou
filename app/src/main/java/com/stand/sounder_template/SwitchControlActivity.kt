package com.stand.sounder_template

import android.app.Activity
import android.os.Bundle
import android.widget.Switch
import android.widget.Toast

class SwitchControlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch_control)

        val interruptSwitch = findViewById<Switch>(R.id.switch_interrupt)
        // 初始化开关状态
        interruptSwitch.isChecked = SwitchManager.isInterruptPlayEnabled

        // 开关状态变化监听
        interruptSwitch.setOnCheckedChangeListener { _, isChecked ->
            SwitchManager.isInterruptPlayEnabled = isChecked
            Toast.makeText(
                this,
                "中断式播放已${if (isChecked) "开启" else "关闭"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}