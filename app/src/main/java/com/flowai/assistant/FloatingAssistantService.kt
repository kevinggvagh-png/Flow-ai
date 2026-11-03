package com.flowai.assistant

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FloatingAssistantService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private fun glass(radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.argb(225, 13, 35, 54))
            setStroke(2, Color.argb(210, 91, 226, 244))
        }

    private fun params(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 180 }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun showBubble() {
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "F"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(190, 250, 255))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(235, 15, 55, 78))
                setStroke(3, Color.rgb(91, 226, 244))
            }
            elevation = 14f
        }
        val p = params(62, 62)
        b.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=p.x; oy=p.y; moved=false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(e.rawX-sx).toInt(); val dy=(e.rawY-sy).toInt()
                        if (kotlin.math.abs(dx)+kotlin.math.abs(dy)>8) moved=true
                        p.x=ox+dx; p.y=oy+dy; wm.updateViewLayout(b,p); return true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) togglePanel(); return true }
                }
                return false
            }
        })
        wm.addView(b, p)
        bubble = b
    }

    private fun togglePanel() {
        if (panel != null) {
            try { wm.removeView(panel) } catch (_: Exception) {}
            panel = null
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
            background = glass(34f)
            elevation = 22f
        }
        val heading = TextView(this).apply {
            text = "FLOW AI  ·  LIQUID GLASS"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(142, 242, 255))
        }
        val answer = TextView(this).apply {
            text = "Hi! I'm Flow AI. What can I help you with?\n\nThis starter currently uses local demo replies."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 18, 0, 18)
        }
        val input = EditText(this).apply {
            hint = "Ask Flow AI…"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            background = glass(22f)
            setPadding(14, 10, 14, 10)
        }
        val send = TextView(this).apply {
            text = "  SEND  "
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(5, 22, 32))
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.rgb(116, 235, 247))
            }
            setPadding(18, 12, 18, 12)
        }
        val close = TextView(this).apply {
            text = "Close"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.END
            setPadding(0, 12, 0, 0)
        }
        box.addView(heading)
        box.addView(answer)
        box.addView(input, LinearLayout.LayoutParams(-1, -2))
        box.addView(send, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = 12 })
        box.addView(close)
        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) {
                answer.text = "You: $q\n\nI'm the Flow AI demo. A real AI service hasn't been connected yet."
                input.setText("")
            }
        }
        close.setOnClickListener { togglePanel() }
        val pp = params(310, -2).apply {
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            x = 18; y = 260
        }
        wm.addView(box, pp)
        panel = box
        panelParams = pp
    }

    override fun onDestroy() {
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        bubble = null; panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
