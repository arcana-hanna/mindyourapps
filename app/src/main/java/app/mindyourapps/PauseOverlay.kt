package app.mindyourapps

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The "Are you sure?" screen. It is a plain window drawn by the accessibility service on top of
 * the app that was just opened, built from ordinary views so it appears instantly.
 *
 * Every choice is one tap:
 *   [ ∞ ]                      open, don't ask again until I leave the app
 *   [ 5 min ][ 10 min ][ 15 min ]   open, ask me again after that long
 *   Custom time...             pick any number of minutes
 *
 * Back (or the home gesture) leaves the app and goes home.
 */
class PauseOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val theme: PauseTheme,
    private val appLabel: String,
    private val icon: Drawable?,
    private val headline: String,
    private val stats: String,
    startCustom: Int,
    /** minutes (or [MindService.INFINITE]) and whether it came from the custom picker */
    private val onChoose: (Int, Boolean) -> Unit,
    private val onExit: () -> Unit,
) {

    private var root: FrameLayout? = null
    private var panel: LinearLayout? = null
    private var customValue: Int = startCustom.coerceIn(1, 480)

    private val blurOn: Boolean =
        theme.blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wm.isCrossWindowBlurEnabled

    private val regular: Typeface = Typeface.create(
        if (theme == PauseTheme.DARK) "sans-serif-light" else "sans-serif",
        Typeface.NORMAL
    )
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // ------------------------------------------------------------------ window

    fun show(): Boolean {
        val r = Root(ctx)
        r.setBackgroundColor(if (theme.blur && !blurOn) theme.bgNoBlur else theme.bg)

        val side = maxOf(dp(28), (ctx.resources.displayMetrics.widthPixels - dp(440)) / 2)

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(side, dp(56), side, dp(56))
        }

        val iconView = ImageView(ctx).apply {
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.22f)
                }
            }
            clipToOutline = true
        }
        col.addView(iconView, lp(dp(76), dp(76)))

        col.addView(label(appLabel, 15f, theme.subtext, medium), lp(WRAP, WRAP, top = 12))

        val headlineView = label(
            headline, 27f, theme.text,
            if (theme == PauseTheme.DARK) regular else medium
        )
        headlineView.setLineSpacing(0f, 1.08f)
        col.addView(headlineView, lp(MATCH, WRAP, top = 22))

        col.addView(label(stats, 15f, theme.subtext), lp(MATCH, WRAP, top = 14))

        val p = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        panel = p
        col.addView(p, lp(MATCH, WRAP, top = 36))

        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        r.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        showMain()

        return try {
            wm.addView(r, windowParams())
            root = r
            true
        } catch (e: Exception) {
            false
        }
    }

    fun dismiss() {
        val r = root ?: return
        root = null
        try {
            wm.removeViewImmediate(r)
        } catch (e: Exception) {
            // already gone
        }
    }

    private fun windowParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            MATCH,
            MATCH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (blurOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 60
        }
        return lp
    }

    /** Root view: catches the back key so back always leaves the app. */
    private inner class Root(c: Context) : FrameLayout(c) {
        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            requestFocus()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onExit()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    // ------------------------------------------------------------------ panels

    private fun showMain() {
        val p = panel ?: return
        p.removeAllViews()

        p.addView(
            button("∞", 40f, theme.primary, theme.onPrimary, "Open without asking again") {
                onChoose(MindService.INFINITE, false)
            },
            lp(MATCH, dp(72))
        )

        p.addView(
            label("or ask me again in", 13f, theme.subtext),
            lp(MATCH, WRAP, top = 22, bottom = 10)
        )

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5, 10, 15).forEachIndexed { i, m ->
            val b = button("$m min", 17f, theme.secondary, theme.onSecondary, "Ask again in $m minutes") {
                onChoose(m, false)
            }
            val params = LinearLayout.LayoutParams(0, dp(56), 1f)
            if (i > 0) params.leftMargin = dp(10)
            row.addView(b, params)
        }
        p.addView(row, lp(MATCH, WRAP))

        val custom = label("Custom time…", 14f, theme.subtext)
        custom.paintFlags = custom.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        custom.setPadding(dp(18), dp(14), dp(18), dp(14))
        custom.isClickable = true
        custom.contentDescription = "Choose a custom time"
        custom.setOnClickListener { showCustom() }
        p.addView(custom, lp(WRAP, WRAP, top = 12).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    private fun showCustom() {
        val p = panel ?: return
        p.removeAllViews()

        p.addView(label("Ask me again in", 15f, theme.subtext), lp(MATCH, WRAP, bottom = 12))

        val valueView = label(fmtMinutesShort(customValue), 30f, theme.text, medium)
        fun refresh() {
            valueView.text = fmtMinutesShort(customValue)
        }

        val stepper = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stepper.addView(
            button("−", 26f, theme.secondary, theme.onSecondary, "Less time") {
                customValue = stepDown(customValue)
                refresh()
            },
            LinearLayout.LayoutParams(dp(64), dp(64))
        )
        stepper.addView(valueView, LinearLayout.LayoutParams(0, WRAP, 1f))
        stepper.addView(
            button("+", 26f, theme.secondary, theme.onSecondary, "More time") {
                customValue = stepUp(customValue)
                refresh()
            },
            LinearLayout.LayoutParams(dp(64), dp(64))
        )
        p.addView(stepper, lp(MATCH, WRAP))

        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(1, 2, 20, 30, 45, 60).forEachIndexed { i, m ->
            val c = button("$m", 15f, theme.secondary, theme.onSecondary, "$m minutes") {
                customValue = m
                refresh()
            }
            val params = LinearLayout.LayoutParams(0, dp(44), 1f)
            if (i > 0) params.leftMargin = dp(8)
            chips.addView(c, params)
        }
        p.addView(chips, lp(MATCH, WRAP, top = 18))

        p.addView(
            button("Start", 18f, theme.primary, theme.onPrimary, "Open the app and ask again later") {
                onChoose(customValue, true)
            },
            lp(MATCH, dp(60), top = 20)
        )

        val back = label("Back", 14f, theme.subtext)
        back.paintFlags = back.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        back.setPadding(dp(18), dp(14), dp(18), dp(14))
        back.isClickable = true
        back.setOnClickListener { showMain() }
        p.addView(back, lp(WRAP, WRAP, top = 8).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    private fun stepUp(v: Int): Int = when {
        v < 10 -> v + 1
        v < 60 -> v + 5
        else -> v + 15
    }.coerceAtMost(480)

    private fun stepDown(v: Int): Int = when {
        v <= 10 -> v - 1
        v <= 60 -> v - 5
        else -> v - 15
    }.coerceAtLeast(1)

    private fun fmtMinutesShort(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }

    // ------------------------------------------------------------------ view helpers

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).roundToInt()

    private fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun label(str: String, sp: Float, color: Int, face: Typeface = regular): TextView =
        TextView(ctx).apply {
            text = str
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = face
            gravity = Gravity.CENTER
        }

    private fun button(
        str: String,
        sp: Float,
        fill: Int,
        textColor: Int,
        description: String,
        onClick: () -> Unit,
    ): TextView = TextView(ctx).apply {
        text = str
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(textColor)
        typeface = medium
        gravity = Gravity.CENTER
        background = pill(fill, 18)
        contentDescription = description
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun pill(fill: Int, radiusDp: Int): Drawable {
        val radius = dp(radiusDp).toFloat()
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = radius
        }
        val rippleColor = (theme.text and 0x00FFFFFF) or (0x30 shl 24)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), shape, mask)
    }
}
