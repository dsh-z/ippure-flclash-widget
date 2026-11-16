package com.example.ippurewidget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workReq = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("ippure_periodic_refresh", ExistingPeriodicWorkPolicy.KEEP, workReq)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D1017"))
            setPadding(56, 56, 56, 56)
        }

        val titleText = TextView(this).apply {
            text = "IPPure 节点监测 Pro (V2.0)"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        }

        val infoText = TextView(this).apply {
            text = "1. 大字号高密度看板：新增 ISP 运营商、海外代理/国内直连双延迟、原生/广播与住宅独立徽章。\n\n" +
                   "2. 秒级点击反馈：点击桌面小组件立刻显示青色边框「测速中」，并发预热连接，0.5秒内出结果。\n\n" +
                   "3. 切换节点自动刷新：点击下方开启「网络变动实时监听」后，在 FlClash 切换节点时桌面小组件将自动无感更新。"
            textSize = 15f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, 48)
        }

        val refreshBtn = Button(this).apply {
            text = "⚡ 立即测速并刷新小组件"
            setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    IPPureWidget.isFetchingNow = false
                    IPPureWidget().updateAll(applicationContext)
                    Toast.makeText(this@MainActivity, "已刷新最新节点情报", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val watchBtn = Button(this).apply {
            text = "🛰️ 开启节点切换自动实时监听"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                }
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, NetworkWatchService::class.java))
                Toast.makeText(this@MainActivity, "已开启实时网络监听保活服务", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(titleText)
        layout.addView(infoText)
        layout.addView(refreshBtn)
        layout.addView(watchBtn)
        setContentView(layout)
    }
}
