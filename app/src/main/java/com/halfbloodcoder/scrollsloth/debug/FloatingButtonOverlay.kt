package com.halfbloodcoder.scrollsloth.debug

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating, draggable button drawn as an accessibility overlay: the Inspector's "Capture" and the
 * calibration "+1". It must be created with the AccessibilityService context;
 * TYPE_ACCESSIBILITY_OVERLAY needs no extra permission.
 */
class FloatingButtonOverlay(
    private val context: Context,
    private val label: String,
    private val description: String,
    private val color: Int,
    private val topOffsetDp: Int,
    private val debounceMs: Long,
    /** Left edge instead of right; reel apps keep their like / comment column on the right. */
    private val alignStart: Boolean = false,
    private val onClick: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var button: TextView? = null
    private var lastClickUptimeMs = 0L

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or (if (alignStart) Gravity.START else Gravity.END)
        x = dp(12)
        y = dp(topOffsetDp)
    }

    fun show() {
        if (button != null) return
        val view = TextView(context).apply {
            text = label
            contentDescription = description
            setTextColor(Color.WHITE)
            textSize = 16f
            minWidth = dp(48)
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(this@FloatingButtonOverlay.color)
            }
            setOnClickListener {
                val now = SystemClock.uptimeMillis()
                if (now - lastClickUptimeMs >= debounceMs) {
                    lastClickUptimeMs = now
                    onClick()
                }
            }
            setOnTouchListener(DragListener())
        }
        windowManager.addView(view, layoutParams)
        button = view
    }

    fun hide() {
        button?.let(windowManager::removeView)
        button = null
    }

    val isShowing: Boolean get() = button != null

    fun setText(text: String) {
        button?.text = text
    }

    fun setBusy(busy: Boolean, busyText: String) {
        button?.apply {
            isEnabled = !busy
            alpha = if (busy) 0.5f else 1f
            text = if (busy) busyText else label
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private inner class DragListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        // With Gravity.END, x is measured from the right edge.
                        val newX = if (alignStart) startX + dx else startX - dx
                        layoutParams.x = newX.roundToInt().coerceAtLeast(0)
                        layoutParams.y = (startY + dy).roundToInt().coerceAtLeast(0)
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging && view.isEnabled) view.performClick()
            }
            return true
        }
    }
}
