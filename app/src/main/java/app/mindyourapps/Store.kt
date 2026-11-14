package app.mindyourapps

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.concurrent.Executors

data class DayStat(val day: Long, val opens: Int, val ms: Long)

/**
 * Everything the app remembers, all on the phone:
 *  - settings and the list of watched apps in SharedPreferences
 *  - per-day open counts and time spent in a tiny SQLite table
 *
 * The properties are Compose state, so the UI updates by itself when they change.
 * The accessibility service and the activity run in the same process and share this object.
 */
object Store {

    private lateinit var prefs: SharedPreferences
    private lateinit var db: Db
    private val io = Executors.newSingleThreadExecutor()

    @Volatile
    private var ready = false

    var apps by mutableStateOf<List<String>>(emptyList())
        private set

    var off by mutableStateOf<Set<String>>(emptySet())
        private set

    var theme by mutableStateOf(PauseTheme.PASTEL)
        private set

    var graceSec by mutableStateOf(30)
        private set

    var friendly by mutableStateOf(true)
        private set

    var customLines by mutableStateOf<List<String>>(emptyList())
        private set

    var lastCustomMin by mutableStateOf(20)
        private set

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("mya", Context.MODE_PRIVATE)
        db = Db(app)
        apps = lines(prefs.getString("apps", ""))
        off = lines(prefs.getString("off", "")).toSet()
        theme = PauseTheme.fromName(prefs.getString("theme", null))
        graceSec = prefs.getInt("grace", 30)
        friendly = prefs.getBoolean("friendly", true)
        customLines = lines(prefs.getString("lines", ""))
        lastCustomMin = prefs.getInt("lastCustom", 20)
        val cutoff = today() - 60
        io.execute { db.prune(cutoff) }
        ready = true
    }

    private fun lines(s: String?): List<String> =
        s?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    // ---- watched apps -------------------------------------------------

    fun addApp(pkg: String) {
        if (pkg !in apps) {
            apps = apps + pkg
            prefs.edit().putString("apps", apps.joinToString("\n")).apply()
        }
        if (pkg in off) setEnabled(pkg, true)
    }

    fun removeApp(pkg: String) {
        apps = apps - pkg
        off = off - pkg
        prefs.edit()
            .putString("apps", apps.joinToString("\n"))
            .putString("off", off.joinToString("\n"))
            .remove("msg_$pkg")
            .apply()
    }

    fun setEnabled(pkg: String, on: Boolean) {
        off = if (on) off - pkg else off + pkg
        prefs.edit().putString("off", off.joinToString("\n")).apply()
    }

    fun isEnabled(pkg: String): Boolean = pkg !in off

    fun isWatched(pkg: String): Boolean = pkg in apps && pkg !in off

    fun message(pkg: String): String = prefs.getString("msg_$pkg", "") ?: ""

    fun saveMessage(pkg: String, msg: String) {
        prefs.edit().putString("msg_$pkg", msg.replace('\n', ' ')).apply()
    }

    // ---- settings -----------------------------------------------------

    fun chooseTheme(t: PauseTheme) {
        theme = t
        prefs.edit().putString("theme", t.name).apply()
    }

    fun chooseGrace(sec: Int) {
        graceSec = sec
        prefs.edit().putInt("grace", sec).apply()
    }

    fun graceMs(): Long = graceSec * 1000L

    fun useFriendly(on: Boolean) {
        friendly = on
        prefs.edit().putBoolean("friendly", on).apply()
    }

    fun addLine(line: String) {
        val l = line.trim().replace('\n', ' ')
        if (l.isEmpty() || l in customLines) return
        customLines = customLines + l
        prefs.edit().putString("lines", customLines.joinToString("\n")).apply()
    }

    fun removeLine(line: String) {
        customLines = customLines - line
        prefs.edit().putString("lines", customLines.joinToString("\n")).apply()
    }

    fun chooseLastCustom(min: Int) {
        lastCustomMin = min
        prefs.edit().putInt("lastCustom", min).apply()
    }

    // ---- stats --------------------------------------------------------

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Writes happen off the main thread so the pause screen is never held up. */
    fun addOpen(pkg: String) {
        io.execute { db.bump(pkg, 1, 0L) }
    }

    fun addTime(pkg: String, ms: Long) {
        if (ms <= 0L) return
        io.execute { db.bump(pkg, 0, ms) }
    }

    fun stats(pkg: String, day: Long): DayStat = db.read(pkg, day)

    /** The last seven days, oldest first. */
    fun week(pkg: String): List<DayStat> {
        val t = today()
        return (6 downTo 0).map { db.read(pkg, t - it) }
    }

    private class Db(ctx: Context) : SQLiteOpenHelper(ctx, "mya.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE daily (" +
                    "pkg TEXT NOT NULL, day INTEGER NOT NULL, " +
                    "opens INTEGER NOT NULL DEFAULT 0, ms INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (pkg, day))"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

        fun bump(pkg: String, opens: Int, ms: Long) {
            val day = LocalDate.now().toEpochDay()
            val w = writableDatabase
            w.execSQL(
                "INSERT OR IGNORE INTO daily (pkg, day, opens, ms) VALUES (?, ?, 0, 0)",
                arrayOf<Any?>(pkg, day)
            )
            w.execSQL(
                "UPDATE daily SET opens = opens + ?, ms = ms + ? WHERE pkg = ? AND day = ?",
                arrayOf<Any?>(opens, ms, pkg, day)
            )
        }

        fun read(pkg: String, day: Long): DayStat {
            val c = readableDatabase.rawQuery(
                "SELECT opens, ms FROM daily WHERE pkg = ? AND day = ?",
                arrayOf(pkg, day.toString())
            )
            c.use {
                if (it.moveToFirst()) return DayStat(day, it.getInt(0), it.getLong(1))
            }
            return DayStat(day, 0, 0L)
        }

        fun prune(beforeDay: Long) {
            writableDatabase.execSQL(
                "DELETE FROM daily WHERE day < ?",
                arrayOf<Any?>(beforeDay)
            )
        }
    }
}
