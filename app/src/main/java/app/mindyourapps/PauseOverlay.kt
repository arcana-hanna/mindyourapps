package app.mindyourapps

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.core.graphics.PathParser
import kotlin.math.min
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The "Are you sure?" screen. A plain window drawn by the accessibility service on top of the
 * app that was just opened, built from ordinary views so it appears instantly.
 *
 *   [app icon]                              [opens today]
 *                  [time today]
 *
 *                   (pause logo)
 *                  [ curved arrow ]          <- the big button: leave, nothing is counted
 *
 *   [ No reminder ]            [ 10 min ]    <- open the app
 *
 * Back (or the home gesture) behaves like the curved arrow.
 */
class PauseOverlay(
    private val ctx: Context,
    private val wm: WindowManager,
    private val theme: PauseTheme,
    systemDark: Boolean,
    private val appLabel: String,
    private val icon: Drawable?,
    private val opens: Int,
    private val ms: Long,
    /** Minutes for the right-hand button. */
    private val remindMin: Int,
    /** minutes, or [MindService.INFINITE] for "no reminder" */
    private val onChoose: (Int) -> Unit,
    private val onExit: () -> Unit,
) {

    private val p: Palette = theme.palette(systemDark)

    private var root: FrameLayout? = null

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

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dp(24), side, dp(28))
        }

        // ---- top: app icon on the left, number of opens on the right
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val iconView = ImageView(ctx).apply {
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = appLabel
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.22f)
                }
            }
            clipToOutline = true
        }
        topRow.addView(iconView, LinearLayout.LayoutParams(dp(52), dp(52)))
        topRow.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))

        val opensBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        opensBox.addView(
            label("$opens", 36f, p.text, if (thin) regular else medium).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(WRAP, WRAP)
        )
        opensBox.addView(
            label(if (opens == 1) "open today" else "opens today", 13f, p.subtext).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(WRAP, WRAP)
        )
        topRow.addView(opensBox, LinearLayout.LayoutParams(WRAP, WRAP))
        col.addView(topRow, lp(MATCH, WRAP))

        // ---- total time today, centred below
        col.addView(label(fmtMinutes(ms), 52f, p.text, if (thin) regular else medium), lp(MATCH, WRAP, top = 8))
        col.addView(label("spent today", 14f, p.subtext), lp(MATCH, WRAP, top = 0))

        // ---- middle: the logo, with the way back right under it
        val middle = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        middle.addView(LogoView(ctx), LinearLayout.LayoutParams(dp(150), dp(150)))
        middle.addView(backButton(), lp(MATCH, dp(76), top = 32))
        col.addView(middle, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ---- bottom: the two ways in
        val bottom = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        // Left (harder to reach with the thumb): no reminder at all.
        bottom.addView(
            button("No reminder", 16f, Color.TRANSPARENT, p.subtext, "Open the app without a reminder", outline = true) {
                onChoose(MindService.INFINITE)
            },
            LinearLayout.LayoutParams(0, dp(56), 1f)
        )
        // Right: remind me after the fixed time chosen in Settings.
        bottom.addView(
            button("$remindMin min", 16f, p.secondary, p.onSecondary, "Open the app and remind me in $remindMin minutes") {
                onChoose(remindMin)
            },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(12) }
        )
        col.addView(bottom, lp(MATCH, WRAP, top = 8))

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
            col.setPadding(side, dp(24) + topInset, side, dp(28) + bottomInset)
            insets
        }

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

    // ------------------------------------------------------------------ drawn pieces

    /** The Mind Your Apps logo: the calm ring with the pause bars, on its blue-violet tile. */
    private class LogoView(c: Context) : View(c) {
        private val tile = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            alpha = 153
        }
        private val bars = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val s = min(width, height).toFloat()
            val ox = (width - s) / 2f
            val oy = (height - s) / 2f

            tile.shader = LinearGradient(
                ox, oy, ox + s, oy + s,
                0xFF6F9BFF.toInt(), 0xFFB49CFF.toInt(), Shader.TileMode.CLAMP
            )
            rect.set(ox, oy, ox + s, oy + s)
            canvas.drawRoundRect(rect, s * 0.24f, s * 0.24f, tile)

            val cx = ox + s / 2f
            val cy = oy + s / 2f
            val r = s * 0.31f
            ring.strokeWidth = s * 0.04f
            canvas.drawCircle(cx, cy, r, ring)

            val barW = r * 0.41f
            val barH = r * 1.04f
            val gap = r * 0.15f
            val radius = barW * 0.18f
            rect.set(cx - gap / 2f - barW, cy - barH / 2f, cx - gap / 2f, cy + barH / 2f)
            canvas.drawRoundRect(rect, radius, radius, bars)
            rect.set(cx + gap / 2f, cy - barH / 2f, cx + gap / 2f + barW, cy + barH / 2f)
            canvas.drawRoundRect(rect, radius, radius, bars)
        }
    }

    /** A curved arrow turning back to the left (the Material "undo" shape). */
    private class UndoView(c: Context, tint: Int) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
        private val path: Path? = PathParser.createPathFromPathData(
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
                "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z"
        )

        override fun onDraw(canvas: Canvas) {
            val pth = path ?: return
            val scale = min(width, height) / 24f
            canvas.save()
            canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
            canvas.scale(scale, scale)
            canvas.drawPath(pth, paint)
            canvas.restore()
        }
    }

    /** The big button: change your mind. No text, just the way back. Nothing is counted. */
    private fun backButton(): View {
        val b = FrameLayout(ctx).apply {
            background = pill(p.primary, 24, false)
            contentDescription = "Don't open the app"
            isClickable = true
            setOnClickListener { onExit() }
        }
        b.addView(
            UndoView(ctx, p.onPrimary),
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
        )
        return b
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
        background = pill(fill, 18, outline)
        contentDescription = description
        isClickable = true
        setOnClickListener { onClick() }
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
