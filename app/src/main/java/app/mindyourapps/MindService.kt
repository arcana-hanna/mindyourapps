package app.mindyourapps

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
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

        /** A short lock-screen break never ends an open-ended session; a longer one (over 15 minutes) does. */
        private const val SCREEN_OFF_KEEP_MS = 15 * 60_000L

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
    private var screenOffAt = 0L

    private var overlay: PauseOverlay? = null
    private var overlayPkg: String? = null

    private val grants = HashMap<String, Grant>()
    private val timers = HashMap<String, Runnable>()

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
        restoreGrants()
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
            if (resumePending) afterScreenOn(pkg)
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
            screenOffAt = now()
            leave(p)
            resumePending = true
        }
    }

    private fun onUnlocked() {
        val p = fgPkg ?: return
        if (resumePending) afterScreenOn(p)
    }

    /**
     * The screen is back on. Locking the phone for a while isn't "leaving" the app, so a short
     * lock never ends a session. And nothing is shown on top of the lock screen: if the phone
     * is still locked we wait for the unlock.
     */
    private fun afterScreenOn(pkg: String) {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) return // resumePending stays true until the unlock arrives
        resumePending = false
        val g = grants[pkg]
        if (g != null && screenOffAt != 0L && now() - screenOffAt <= SCREEN_OFF_KEEP_MS) {
            g.leftAt = 0L
        }
        enter(pkg)
    }

    private fun enter(pkg: String) {
        if (!Store.isWatched(pkg)) return
        if (overlay != null && overlayPkg == pkg) return

        val n = now()
        val g = grants[pkg]
        if (g != null) {
            val timed = g.until != Long.MAX_VALUE
            val away = if (g.leftAt == 0L) 0L else n - g.leftAt

            // The time you picked is a real clock. While it runs, leaving the app, switching
            // apps or locking the phone never brings the check-in back. (An open-ended session
            // is the exception: it ends once you've been away longer than the come-back window.)
            if (n < g.until && (timed || away <= Store.graceMs())) {
                g.leftAt = 0L
                sessionStart = n
                if (timed) schedule(pkg, g.until - n) // exact time left, even after the phone slept
                return
            }

            // Your time ran out a moment ago and you're still here: say so.
            if (away <= Store.graceMs()) {
                showOverlay(pkg, countOpen = false, extraMs = 0L)
                return
            }

            // You've been gone a while: that was the end of the visit.
            grants.remove(pkg)
            cancelTimer(pkg)
            Store.clearGrant(pkg)
        }

        // Fresh visit. It is only counted as an open if you choose to continue.
        showOverlay(pkg, countOpen = true, extraMs = 0L)
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
        if (now() < g.until) {
            // fired a touch early (the phone slept): wait out the rest
            schedule(pkg, g.until - now())
            return
        }
        if (fgPkg == pkg && !resumePending) {
            var extra = 0L
            if (sessionStart != 0L) {
                extra = now() - sessionStart
                Store.addTime(pkg, extra)
                sessionStart = 0L
            }
            showOverlay(pkg, countOpen = false, extraMs = extra)
        }
    }

    // ------------------------------------------------------------------ pause screen

    /** Used by the "preview" button in Settings. */
    fun showPreview() {
        showOverlay(packageName, countOpen = false, extraMs = 0L, preview = true)
    }

    private fun showOverlay(
        pkg: String,
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
        val opens = if (preview) 7 else today.opens
        val ms = if (preview) 42 * 60_000L else today.ms + extraMs
        val systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        val o = PauseOverlay(
            ctx = this,
            wm = windowManager,
            theme = Store.theme,
            systemDark = systemDark,
            appLabel = label,
            icon = icon,
            opens = opens,
            ms = ms,
            remindMin = Store.defaultMin,
            onChoose = { minutes -> choose(pkg, minutes, preview, countOpen) },
            onExit = { exit(preview) }
        )
        overlay = o
        overlayPkg = pkg
        if (!o.show()) {
            overlay = null
            overlayPkg = null
        }
    }

    private fun choose(pkg: String, minutes: Int, preview: Boolean, countOpen: Boolean) {
        hideOverlay()
        if (preview) return
        // Only now does it count as an open. The way-back arrow never does.
        if (countOpen) Store.addOpen(pkg)

        val n = now()
        val until = if (minutes == INFINITE) Long.MAX_VALUE else n + minutes * 60_000L
        grants[pkg] = Grant(until)
        cancelTimer(pkg)
        if (minutes != INFINITE) {
            schedule(pkg, minutes * 60_000L)
            Store.saveGrant(pkg, System.currentTimeMillis() + minutes * 60_000L)
        } else {
            Store.clearGrant(pkg)
        }
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

    /** If Android restarted this service, the clocks you started are picked up again. */
    private fun restoreGrants() {
        val wall = System.currentTimeMillis()
        val n = now()
        for ((pkg, untilWall) in Store.savedGrants()) {
            if (untilWall > wall) {
                grants[pkg] = Grant(n + (untilWall - wall))
            } else {
                Store.clearGrant(pkg)
            }
        }
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
