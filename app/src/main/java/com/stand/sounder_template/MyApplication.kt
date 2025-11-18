package com.stand.sounder_template

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MyApplication : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动前台服务
        startService(Intent(this, BeepService::class.java))
        finish() // 启动后立即关闭界面
    }
}
