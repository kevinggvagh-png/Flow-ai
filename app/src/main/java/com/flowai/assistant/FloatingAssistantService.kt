package com.flowai.assistant

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FloatingAssistantService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun glass(radius: Float): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(246, 14, 31, 57), Color.argb(238, 25, 25, 65))
        ).apply {
            cornerRadius = dp(radius.toInt()).toFloat()
            setStroke(dp(1), Color.argb(190, 91, 220, 255))
        }

    @Suppress("DEPRECATION")
    private fun params(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(18); y = dp(180) }

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
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(35, 202, 255), Color.rgb(105, 82, 255), Color.rgb(185, 86, 255))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.argb(220, 190, 245, 255))
            }
            elevation = dp(14).toFloat()
        }
        val p = params(dp(58), dp(58))
        b.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=p.x; oy=p.y; moved=false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(e.rawX-sx).toInt(); val dy=(e.rawY-sy).toInt()
                        if (kotlin.math.abs(dx)+kotlin.math.abs(dy)>dp(7)) moved=true
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

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun togglePanel() {
        if (panel != null) {
            try { wm.removeView(panel) } catch (_: Exception) {}
            panel = null
            return
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(21), dp(18), dp(21), dp(18))
            background = glass(30f)
            elevation = dp(28).toFloat()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = label("FLOW AI", 15f, Color.rgb(151, 239, 255), true)
        val spacer = View(this)
        val status = label("● INTERFACE READY", 9f, Color.rgb(115, 245, 205), true)
        val close = label("×", 27f, Color.rgb(190, 215, 244), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { togglePanel() }
        }
        top.addView(brand)
        top.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(status)
        top.addView(close)

        val orb = FlowOrbView(this)
        val greeting = label("I am Flow.", 23f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(4))
        }
        val subtitle = label("Your assistant interface is ready.", 13f, Color.rgb(183, 205, 231)).apply {
            gravity = Gravity.CENTER
        }
        val answer = label("Systems ready.\nState your objective.", 14f, Color.WHITE).apply {
            setPadding(dp(2), dp(16), dp(2), dp(16))
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(19).toFloat()
                setColor(Color.argb(145, 5, 17, 38))
                setStroke(dp(1), Color.argb(150, 102, 197, 239))
            }
            setPadding(dp(12), dp(3), dp(5), dp(3))
        }
        val input = EditText(this).apply {
            hint = "Give Flow an instruction..."
            textSize = 14f
            setHintTextColor(Color.rgb(137, 162, 194))
            setTextColor(Color.WHITE)
            setSingleLine(false)
            maxLines = 3
            background = null
            setPadding(dp(3), dp(8), dp(3), dp(8))
        }
        val send = TextView(this).apply {
            text = "➤"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(28, 194, 245), Color.rgb(117, 91, 255))
            ).apply { shape = GradientDrawable.OVAL }
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(42), dp(42)))

        val footer = label("LIQUID GLASS  •  FLOATING ASSISTANT", 9f, Color.rgb(126, 164, 204)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }

        card.addView(top)
        card.addView(orb, LinearLayout.LayoutParams(-1, dp(112)))
        card.addView(greeting)
        card.addView(subtitle)
        card.addView(answer)
        card.addView(inputRow)
        card.addView(footer)

        send.setOnClickListener {
            val question = input.text.toString().trim()
            if (question.isNotEmpty()) {
                answer.text = "Flow is thinking…"
                input.setText("")
                send.isEnabled = false
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
                LocalFlowBrain.ask(
                    this, question,
                    onChunk = { piece ->
                        if (answer.text.toString() == "Flow is thinking…") answer.text = ""
                        answer.append(piece)
                    },
                    onDone = { error ->
                        send.isEnabled = true
                        if (error != null) answer.append("\n\n" + error)
                    }
                )
            }
        }

        val panelWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost(dp(420))
        val pp = params(panelWidth, -2).apply {
            x = ((resources.displayMetrics.widthPixels - panelWidth) / 2).coerceAtLeast(dp(8))
            y = (resources.displayMetrics.heightPixels * 0.22f).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(card, pp)
        panel = card
    }

    private class FlowOrbView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            color = Color.rgb(121, 240, 255)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            val r = 42f * d

            paint.shader = RadialGradient(
                cx - 9f*d, cy - 10f*d, r * 1.45f,
                intArrayOf(Color.argb(220, 68, 229, 255), Color.argb(145, 67, 91, 255), Color.argb(0, 115, 62, 255)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * 1.45f, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(205, 18, 48, 110)
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f*d
            paint.color = Color.rgb(102, 233, 255)
            canvas.drawCircle(cx, cy, r, paint)
            paint.strokeWidth = 1.2f*d
            paint.color = Color.argb(220, 170, 125, 255)
            canvas.drawCircle(cx, cy, r - 5f*d, paint)

            val path = Path().apply {
                moveTo(cx-r*0.68f, cy+2f*d)
                cubicTo(cx-r*0.25f, cy-r*0.62f, cx+r*0.02f, cy+r*0.62f, cx+r*0.36f, cy+1f*d)
                cubicTo(cx+r*0.52f, cy-r*0.18f, cx+r*0.60f, cy-r*0.20f, cx+r*0.69f, cy-3f*d)
            }
            canvas.drawPath(path, wave)
        }
    }

    override fun onDestroy() {
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        bubble = null
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
