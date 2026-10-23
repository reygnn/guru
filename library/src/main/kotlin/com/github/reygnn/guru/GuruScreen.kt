package com.github.reygnn.guru

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

/**
 * The alert: black screen, red text, a red border that blinks — unless the system's animations
 * are off (Settings › Accessibility › Remove animations), then it just stays lit. Tap = left
 * button (continue: the reports go), hold = right button (send), Back = neither (they come
 * back on the next start). Dismisses itself when its activity is destroyed.
 */
internal object GuruScreen {

    fun show(activity: Activity, lines: List<String>, texts: GuruTexts, onContinue: () -> Unit, onSend: () -> Unit): Dialog {
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        var decided = false
        val view = GuruView(activity, lines, texts.hint, texts.actionContinue, texts.actionSend)
        view.setOnClickListener {
            if (!decided) {
                decided = true
                dialog.dismiss()
                onContinue()
            }
        }
        view.setOnLongClickListener {
            if (!decided) {
                decided = true
                dialog.dismiss()
                onSend()
            }
            true
        }
        dialog.setContentView(view)
        dialog.show()
        activity.registerActivityLifecycleCallbacks(DismissOnDestroy(dialog))
        return dialog
    }

    private class DismissOnDestroy(private val dialog: Dialog) : Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(activity: Activity) {
            if (dialog.isShowing) dialog.dismiss()
            activity.unregisterActivityLifecycleCallbacks(this)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}

/**
 * Drawn, not laid out: every size is computed when the size or the insets change, none in onDraw.
 * The quote [lines] and the host's [hint] each shrink to fit the width — a translation may be longer.
 */
// Built in code only, never inflated from XML: the tools constructors would have no lines to draw.
@SuppressLint("ViewConstructor")
internal class GuruView(
    context: Context,
    private val lines: List<String>,
    private val hint: String,
    private val actionContinue: String,
    private val actionSend: String,
) : View(context) {

    private val metrics = resources.displayMetrics
    private val pad = BOX_PAD_DP * metrics.density
    private val box = RectF()
    private var topInset = 0
    private var lineHeight = 0f
    private var lit = true

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ALERT_RED
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HINT_GREY
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val border = Paint().apply {
        color = ALERT_RED
        style = Paint.Style.STROKE
        strokeWidth = BORDER_DP * metrics.density
    }

    private val blink = object : Runnable {
        override fun run() {
            lit = !lit
            invalidate()
            postDelayed(this, BLINK_MS)
        }
    }

    init {
        contentDescription = (lines + hint).joinToString(". ")
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, actionContinue))
                info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, actionSend))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) postDelayed(blink, BLINK_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        lit = true
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        topInset = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top
        measureBox()
        invalidate()
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        measureBox()
    }

    // The text size the longest line fits the box at, never above TEXT_SP; the hint likewise
    // fits the width under the box, never above HINT_SP.
    private fun measureBox() {
        if (width == 0) return
        val inside = width - 2 * pad
        fit(text, TEXT_SP, lines, inside - 2 * pad)
        fit(hintText, HINT_SP, listOf(hint), inside)
        lineHeight = text.fontSpacing
        val top = topInset + pad
        box.set(pad, top, width - pad, top + 2 * pad + lineHeight * lines.size)
    }

    private fun fit(paint: Paint, sp: Float, texts: List<String>, room: Float) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        val widest = texts.maxOf { paint.measureText(it) }
        if (widest > room) paint.textSize *= room / widest
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (lit) canvas.drawRect(box, border)
        val x = width / 2f
        var y = box.top + pad - text.ascent()
        for (line in lines) {
            canvas.drawText(line, x, y, text)
            y += lineHeight
        }
        canvas.drawText(hint, x, box.bottom + pad - hintText.ascent(), hintText)
    }

    companion object {
        /** The Kickstart's alert red. */
        val ALERT_RED = Color.rgb(0xFF, 0x00, 0x00)
        val HINT_GREY = Color.rgb(0x66, 0x66, 0x66)
        const val BLINK_MS = 700L
        const val TEXT_SP = 16f
        const val HINT_SP = 12f
        const val BOX_PAD_DP = 16f
        const val BORDER_DP = 6f
    }
}
