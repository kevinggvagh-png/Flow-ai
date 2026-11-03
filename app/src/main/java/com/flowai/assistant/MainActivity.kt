package com.flowai.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(5, 15, 28))
        }
        val title = TextView(this).apply {
            text = "FLOW AI"
            textSize = 34f
            setTextColor(Color.rgb(140, 245, 255))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Your liquid-glass floating assistant"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }
        val permission = Button(this).apply { text = "Allow display over other apps" }
        val start = Button(this).apply { text = "Start floating Flow AI" }
        val stop = Button(this).apply { text = "Stop floating assistant" }
        root.addView(title)
        root.addView(subtitle)
        root.addView(permission)
        root.addView(start)
        root.addView(stop)
        setContentView(root)

        permission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
        start.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingAssistantService::class.java))
            } else {
                permission.performClick()
            }
        }
        stop.setOnClickListener {
            stopService(Intent(this, FloatingAssistantService::class.java))
        }
    }
}
