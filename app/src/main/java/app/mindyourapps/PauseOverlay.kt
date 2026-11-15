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
import android.view.WindowInsets
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
 * The numbers come first (how often, how long), then the friendly line, then the choices:
 *
 *   [ ←  Not now ]                         the big button: leave, nothing is counted
 *   [ 5 min ] [ 10 min ] [ 15 min ]        open it and ask me again after that long
 *   [ Don't bother me this session ]       open it, no timer (the wording changes every time)
 *   Custom time…                           any number of minutes
 *
 * Back (or the home gesture) behaves like the big button.
 */
class PauseOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val theme: PauseTheme,
    systemDark: Boolean,
    private val appLabel: String,
    private val icon: Drawable?,
    private val headline: String,
    private val opens: Int,
    private val ms: Long,
    /** The number this open would be if you continue, or null when nothing new would be counted. */
    private val nextOpen: Int?,
    private val expired: Boolean,
    private val stayLabel: String,
    startCustom: Int,
    /** minutes (or [MindService.INFINITE]) and whether it came from the custom picker */
    private val onChoose: (Int, Boolean) -> Unit,
    private val onExit: () -> Unit,
) {

    private val p: Palette = theme.palette(systemDark)

    private var root: FrameLayout? = null
    private var panel: LinearLayout? = null
    private var customValue: Int = startCustom.coerceIn(1, 480)

    private val blurOn: Boolean =
        theme.blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wm.isCrossWindowBlurEnabled

    private val thin = theme == PauseTheme.DARK

    private val regular: Typeface = Typeface.create(
        if (thin) "sans-serif-light" else "sans-serif",
        Typeface.NORMAL
    )
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // ------------------------------------------------------------------ window

    fun show(): Boolean {
        val r = Root(ctx)
        r.setBackgroundColor(if (theme.blur && !blurOn) p.bgNoBlur else p.bg)

        val side = maxOf(dp(24), (ctx.resources.displayMetrics.widthPixels - dp(440)) / 2)

        // Whole screen is one column: your numbers float in the upper part, the choices sit at
        // the bottom where a thumb reaches them.
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dp(40), side, dp(28))
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // App
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
        top.addView(iconView, lp(dp(60), dp(60)))
        top.addView(label(appLabel, 15f, p.subtext, medium), lp(WRAP, WRAP, top = 10))

        // Your numbers: the star of the screen
        top.addView(statsCard(), lp(MATCH, WRAP, top = 22))
        if (nextOpen != null) {
            top.addView(
                label("Opening it now makes it open #$nextOpen today", 14f, p.subtext),
                lp(MATCH, WRAP, top = 12)
            )
        }

        // The friendly line
        val headlineView = label(headline, 20f, p.text, if (thin) regular else medium)
        headlineView.setLineSpacing(0f, 1.1f)
        top.addView(headlineView, lp(MATCH, WRAP, top = 26))

        col.addView(top, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // The choices
        val choices = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        panel = choices
        col.addView(choices, lp(MATCH, WRAP, top = 20))

        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        r.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        // Keep clear of the status bar, camera cut-out and gesture bar.
        r.setOnApplyWindowInsetsListener { _, insets ->
            val (topInset, bottomInset) = verticalInsets(insets)
            col.setPadding(side, dp(40) + topInset, side, dp(28) + bottomInset)
            insets
        }

        showMain()

        // Fade in quickly. The buttons work from the very first frame, so this never delays you.
        r.alpha = 0f

        return try {
            wm.addView(r, windowParams())
            root = r
            r.animate().alpha(1f).setDuration(140L).start()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Space taken by the status bar / camera cut-out (top) and gesture bar (bottom). */
    @Suppress("DEPRECATION")
    private fun verticalInsets(insets: WindowInsets): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            bars.top to bars.bottom
        } else {
            insets.systemWindowInsetTop to insets.systemWindowInsetBottom
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
        val params = WindowManager.LayoutParams(
            MATCH,
            MATCH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (blurOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = 60
        }
        return params
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

    // ------------------------------------------------------------------ numbers

    private fun statsCard(): View {
        val m = (ms / 60_000L).toInt()
        val timeValue = if (m < 60) "$m" else "${m / 60}h ${m % 60}m"
        val timeCaption = if (m < 60) "min today" else "today"
        val openCaption = if (opens == 1) "open today" else "opens today"

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(p.card, 24)
            setPadding(dp(8), dp(20), dp(8), dp(18))
        }
        row.addView(stat("$opens", openCaption), LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(stat(timeValue, timeCaption), LinearLayout.LayoutParams(0, WRAP, 1f))
        return row
    }

    private fun stat(value: String, caption: String): View {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        c.addView(label(value, 46f, p.text, if (thin) regular else medium), LinearLayout.LayoutParams(WRAP, WRAP))
        c.addView(label(caption, 14f, p.subtext), lp(WRAP, WRAP, top = 2))
        return c
    }

    // ------------------------------------------------------------------ panels

    private fun showMain() {
        val c = panel ?: return
        c.removeAllViews()

        // The big, obvious choice: change your mind. Nothing is counted.
        c.addView(
            button(
                if (expired) "←  I'm done" else "←  Not now",
                22f, p.primary, p.onPrimary,
                if (expired) "Close the app" else "Don't open the app"
            ) { onExit() },
            lp(MATCH, dp(72))
        )

        c.addView(
            label("or open it and remind me in", 13f, p.subtext),
            lp(MATCH, WRAP, top = 22, bottom = 10)
        )

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5, 10, 15).forEachIndexed { i, m ->
            val b = button("$m min", 16f, p.secondary, p.onSecondary, "Open and ask again in $m minutes") {
                onChoose(m, false)
            }
            val params = LinearLayout.LayoutParams(0, dp(52), 1f)
            if (i > 0) params.leftMargin = dp(10)
            row.addView(b, params)
        }
        c.addView(row, lp(MATCH, WRAP))

        // Quiet option, with wording that changes every time so it stays a conscious choice.
        c.addView(
            button(stayLabel, 14f, Color.TRANSPARENT, p.subtext, "Open and don't remind me", outline = true) {
                onChoose(MindService.INFINITE, false)
            },
            lp(MATCH, dp(48), top = 10)
        )

        val custom = label("Custom time…", 14f, p.subtext)
        custom.paintFlags = custom.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        custom.setPadding(dp(18), dp(12), dp(18), dp(12))
        custom.isClickable = true
        custom.contentDescription = "Choose a custom time"
        custom.setOnClickListener { showCustom() }
        c.addView(custom, lp(WRAP, WRAP, top = 4).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    private fun showCustom() {
        val c = panel ?: return
        c.removeAllViews()

        c.addView(label("Open it and remind me in", 15f, p.subtext), lp(MATCH, WRAP, bottom = 12))

        val valueView = label(fmtShort(customValue), 30f, p.text, medium)
        fun refresh() {
            valueView.text = fmtShort(customValue)
        }

        val stepper = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stepper.addView(
            button("−", 26f, p.secondary, p.onSecondary, "Less time") {
                customValue = stepDown(customValue)
                refresh()
            },
            LinearLayout.LayoutParams(dp(64), dp(64))
        )
        stepper.addView(valueView, LinearLayout.LayoutParams(0, WRAP, 1f))
        stepper.addView(
            button("+", 26f, p.secondary, p.onSecondary, "More time") {
                customValue = stepUp(customValue)
                refresh()
            },
            LinearLayout.LayoutParams(dp(64), dp(64))
        )
        c.addView(stepper, lp(MATCH, WRAP))

        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(1, 2, 20, 30, 45, 60).forEachIndexed { i, m ->
            val chip = button("$m", 15f, p.secondary, p.onSecondary, "$m minutes") {
                customValue = m
                refresh()
            }
            val params = LinearLayout.LayoutParams(0, dp(44), 1f)
            if (i > 0) params.leftMargin = dp(8)
            chips.addView(chip, params)
        }
        c.addView(chips, lp(MATCH, WRAP, top = 18))

        c.addView(
            button("Open it", 18f, p.primary, p.onPrimary, "Open the app and ask again later") {
                onChoose(customValue, true)
            },
            lp(MATCH, dp(60), top = 20)
        )

        val back = label("Back", 14f, p.subtext)
        back.paintFlags = back.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        back.setPadding(dp(18), dp(14), dp(18), dp(14))
        back.isClickable = true
        back.setOnClickListener { showMain() }
        c.addView(back, lp(WRAP, WRAP, top = 8).apply { gravity = Gravity.CENTER_HORIZONTAL })
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

    private fun fmtShort(m: Int): String = when {
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
        outline: Boolean = false,
        onClick: () -> Unit,
    ): TextView = TextView(ctx).apply {
        text = str
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(textColor)
        typeface = medium
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        background = pill(fill, if (outline) 16 else 18, outline)
        contentDescription = description
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, radiusDp: Int): Drawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun pill(fill: Int, radiusDp: Int, outline: Boolean): Drawable {
        val radius = dp(radiusDp).toFloat()
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (outline) setStroke(dp(1), (p.subtext and 0x00FFFFFF) or (0x66 shl 24))
        }
        val mask = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = radius
        }
        val rippleColor = (p.text and 0x00FFFFFF) or (0x30 shl 24)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), shape, mask)
    }
}
