package app.mindyourapps

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Watches which app is in front. When it is one you chose, it covers it with the pause screen.
 *
 * Only window-change events are used: the service learns *which app* came to the front, never
 * what is on the screen.
 *
 * Rules, in plain words:
 *  - Opening a watched app shows the pause screen (and counts as one open).
 *  - Choosing "∞" lets you use the app until you leave it for longer than the come-back window.
 *  - Choosing N minutes lets you use it for N minutes, then the pause screen comes back.
 *  - Leaving and returning within the come-back window changes nothing.
 *  - Back on the pause screen leaves the app and goes home.
 */
class MindService : AccessibilityService() {

    companion object {
        const val INFINITE = -1

        @Volatile
        var instance: MindService? = null

        /** Windows that belong to the system, not to an app you "went to". */
        private val IGNORED = setOf("com.android.systemui", "android")

        fun isEnabled(ctx: Context): Boolean {
            if (instance != null) return true
            val setting = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(ctx, MindService::class.java)
            return setting.split(':').any {
                it.equals(me.flattenToString(), ignoreCase = true) ||
                    it.equals(me.flattenToShortString(), ignoreCase = true)
            }
        }
    }

    /** "Allowed until": set when you pick a time on the pause screen. */
    private class Grant(val until: Long, var leftAt: Long = 0L)

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var fgPkg: String? = null
    private var sessionStart = 0L
    private var resumePending = false

    private var overlay: PauseOverlay? = null
    private var overlayPkg: String? = null

    private val grants = HashMap<String, Grant>()
    private val timers = HashMap<String, Runnable>()
    private val lastOpenCounted = HashMap<String, Long>()

    private var imePackages: Set<String> = emptySet()
    private var imeStamp = 0L
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) onUnlocked() else onScreenOff()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        instance = this
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        hideOverlay()
        timers.values.forEach { handler.removeCallbacks(it) }
        timers.clear()
        grants.clear()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                // not registered
            }
            receiverRegistered = false
        }
        if (instance === this) instance = null
    }

    // ------------------------------------------------------------------ events

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()

        // Our own pause screen also produces window events; only our real activity counts.
        if (pkg == packageName && cls != MainActivity::class.java.name) return
        if (pkg in IGNORED || isKeyboard(pkg)) return

        onForeground(pkg)
    }

    private fun onForeground(pkg: String) {
        if (overlay != null && overlayPkg != pkg) hideOverlay()

        val prev = fgPkg
        if (pkg == prev) {
            if (resumePending) {
                resumePending = false
                enter(pkg)
            }
            return
        }

        if (prev != null && !resumePending) leave(prev)
        resumePending = false
        fgPkg = pkg
        enter(pkg)
    }

    private fun onScreenOff() {
        val p = fgPkg ?: return
        if (!resumePending) {
            leave(p)
            resumePending = true
        }
    }

    private fun onUnlocked() {
        val p = fgPkg ?: return
        if (resumePending) {
            resumePending = false
            enter(p)
        }
    }

    private fun enter(pkg: String) {
        if (!Store.isWatched(pkg)) return
        if (overlay != null && overlayPkg == pkg) return

        val n = now()
        val g = grants[pkg]
        if (g != null) {
            val away = if (g.leftAt == 0L) 0L else n - g.leftAt
            if (away > Store.graceMs()) {
                // Gone too long: this is a fresh visit.
                grants.remove(pkg)
                cancelTimer(pkg)
            } else if (n < g.until) {
                // Still allowed.
                g.leftAt = 0L
                sessionStart = n
                return
            } else {
                // The time you picked ran out while you were away.
                showOverlay(pkg, expired = true, countOpen = false, extraMs = 0L)
                return
            }
        }

        // A quick bounce back to an app you haven't answered yet isn't a new open.
        val last = lastOpenCounted[pkg] ?: 0L
        val count = last == 0L || n - last > Store.graceMs()
        if (count) lastOpenCounted[pkg] = n
        showOverlay(pkg, expired = false, countOpen = count, extraMs = 0L)
    }

    private fun leave(pkg: String) {
        if (sessionStart != 0L) {
            Store.addTime(pkg, now() - sessionStart)
            sessionStart = 0L
        }
        grants[pkg]?.leftAt = now()
    }

    private fun onTimer(pkg: String) {
        timers.remove(pkg)
        val g = grants[pkg] ?: return
        if (fgPkg == pkg && !resumePending && now() >= g.until) {
            var extra = 0L
            if (sessionStart != 0L) {
                extra = now() - sessionStart
                Store.addTime(pkg, extra)
                sessionStart = 0L
            }
            showOverlay(pkg, expired = true, countOpen = false, extraMs = extra)
        }
    }

    // ------------------------------------------------------------------ pause screen

    /** Used by the "preview" button in Settings. */
    fun showPreview() {
        showOverlay(packageName, expired = false, countOpen = false, extraMs = 0L, preview = true)
    }

    private fun showOverlay(
        pkg: String,
        expired: Boolean,
        countOpen: Boolean,
        extraMs: Long,
        preview: Boolean = false,
    ) {
        hideOverlay()

        val pm = packageManager
        val label = if (preview) {
            getString(R.string.app_name)
        } else {
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }
        val icon: Drawable? = try {
            pm.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }

        val today = Store.stats(pkg, Store.today())
        val opens = if (preview) 7 else today.opens + if (countOpen) 1 else 0
        val ms = if (preview) 42 * 60_000L else today.ms + extraMs

        val o = PauseOverlay(
            ctx = this,
            wm = windowManager,
            theme = Store.theme,
            appLabel = label,
            icon = icon,
            headline = headline(pkg, label, expired),
            stats = pauseStats(opens, ms),
            startCustom = Store.lastCustomMin,
            onChoose = { minutes, custom -> choose(pkg, minutes, custom, preview) },
            onExit = { exit(preview) }
        )
        overlay = o
        overlayPkg = pkg
        if (!o.show()) {
            overlay = null
            overlayPkg = null
            return
        }
        // Count after the screen is up so nothing delays it.
        if (countOpen && !preview) Store.addOpen(pkg)
    }

    private fun headline(pkg: String, label: String, expired: Boolean): String {
        val own = Store.message(pkg).trim()
        if (own.isNotEmpty()) return own
        if (!Store.friendly) return "Open $label?"
        return if (expired) {
            Lines.pick(Lines.timeUps + Store.customLines)
        } else {
            Lines.pick(Lines.greetings + Store.customLines)
        }
    }

    private fun choose(pkg: String, minutes: Int, custom: Boolean, preview: Boolean) {
        hideOverlay()
        if (preview) return
        if (custom) Store.chooseLastCustom(minutes)

        val n = now()
        val until = if (minutes == INFINITE) Long.MAX_VALUE else n + minutes * 60_000L
        grants[pkg] = Grant(until)
        cancelTimer(pkg)
        if (minutes != INFINITE) schedule(pkg, minutes * 60_000L)
        if (fgPkg == pkg) sessionStart = n
    }

    private fun exit(preview: Boolean) {
        hideOverlay()
        if (!preview) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun hideOverlay() {
        overlay?.dismiss()
        overlay = null
        overlayPkg = null
    }

    // ------------------------------------------------------------------ helpers

    private fun now(): Long = SystemClock.elapsedRealtime()

    private fun schedule(pkg: String, delayMs: Long) {
        cancelTimer(pkg)
        val r = Runnable { onTimer(pkg) }
        timers[pkg] = r
        handler.postDelayed(r, delayMs)
    }

    private fun cancelTimer(pkg: String) {
        timers.remove(pkg)?.let { handler.removeCallbacks(it) }
    }

    private fun isKeyboard(pkg: String): Boolean {
        val t = SystemClock.elapsedRealtime()
        if (t - imeStamp > 60_000L) {
            imeStamp = t
            imePackages = try {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .enabledInputMethodList
                    .map { it.packageName }
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        return pkg in imePackages
    }

    /** Minutes already running in the current visit, for live numbers in the app. */
    fun liveExtraMs(pkg: String): Long =
        if (fgPkg == pkg && sessionStart != 0L) now() - sessionStart else 0L
}
