package com.flowai.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var overlayStatus: TextView
    private lateinit var permissionButton: Button
    private lateinit var startButton: Button

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(color)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(34), dp(24), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(5, 13, 29), Color.rgb(13, 24, 51), Color.rgb(29, 17, 61))
            )
        }

        val orb = FlowHomeOrb(this)
        root.addView(orb, LinearLayout.LayoutParams(dp(124), dp(124)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(18)
        })

        root.addView(text("FLOW AI", 30f, Color.rgb(145, 241, 255), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        })
        root.addView(text("I AM FLOW.  SYSTEMS READY.", 12f, Color.rgb(160, 185, 222), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        })

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.argb(115, 20, 39, 70), Color.argb(120, 85, 197, 238))
        }
        statusCard.addView(text("ASSISTANT STATUS", 11f, Color.rgb(126, 220, 247), true))
        overlayStatus = text("Checking overlay permission…", 14f, Color.WHITE).apply {
            setPadding(0, dp(9), 0, 0)
        }
        statusCard.addView(overlayStatus)
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })

        permissionButton = Button(this).apply {
            text = "GRANT FLOATING PERMISSION"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(37, 107, 175))
        }
        startButton = Button(this).apply {
            text = "LAUNCH FLOW AI"
            isAllCaps = false
            setTextColor(Color.rgb(4, 20, 38))
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(93, 235, 255), Color.rgb(153, 129, 255))
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        val stopButton = Button(this).apply {
            text = "STOP FLOATING ASSISTANT"
            isAllCaps = false
            setTextColor(Color.rgb(206, 220, 242))
            background = rounded(Color.argb(90, 28, 43, 70), Color.argb(110, 102, 139, 184))
        }

        root.addView(permissionButton, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        root.addView(startButton, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(10) })
        root.addView(stopButton, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(24) })

        root.addView(text("A quiet assistant, ready when you are.", 13f, Color.rgb(139, 163, 198)).apply {
            gravity = Gravity.CENTER
        })
        root.addView(text("FLOATING MODE  •  LIQUID GLASS", 9f, Color.rgb(89, 137, 183), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        setContentView(root)

        permissionButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        startButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingAssistantService::class.java))
            } else {
                permissionButton.performClick()
            }
        }
        stopButton.setOnClickListener {
            stopService(Intent(this, FloatingAssistantService::class.java))
        }
        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        if (::overlayStatus.isInitialized) updatePermissionState()
    }

    private fun updatePermissionState() {
        val granted = Settings.canDrawOverlays(this)
        overlayStatus.text = if (granted)
            "● Floating permission granted. Flow is ready to launch."
        else
            "○ Allow display over other apps to use the floating assistant."
        overlayStatus.setTextColor(if (granted) Color.rgb(120, 245, 194) else Color.rgb(236, 207, 145))
        permissionButton.visibility = if (granted) View.GONE else View.VISIBLE
        startButton.isEnabled = granted
        startButton.alpha = if (granted) 1f else 0.62f
    }

    private class FlowHomeOrb(context: android.content.Context) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = dp(39).toFloat()
            paint.shader = android.graphics.RadialGradient(
                cx, cy, dp(58).toFloat(),
                intArrayOf(Color.argb(220, 74, 224, 255), Color.argb(110, 91, 94, 255), Color.TRANSPARENT),
                null, android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, dp(58).toFloat(), paint)
            paint.shader = null
            paint.color = Color.argb(205, 17, 43, 104)
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.rgb(100, 235, 255)
            canvas.drawCircle(cx, cy, radius, paint)
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(173, 130, 255)
            canvas.drawCircle(cx, cy, radius - dp(6), paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = dp(3).toFloat()
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.color = Color.rgb(133, 248, 255)
            val path = android.graphics.Path().apply {
                moveTo(cx - dp(25), cy)
                cubicTo(cx - dp(10), cy - dp(23), cx + dp(4), cy + dp(23), cx + dp(25), cy)
            }
            canvas.drawPath(path, paint)
        }
        private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    }
}
