package app.mindyourapps

/** "5 min", "1 h 5 min", "0 min" */
fun fmtMinutes(ms: Long): String {
    val total = (ms / 60_000L).toInt()
    return when {
        total < 1 -> "0 min"
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }
}

/** Text shown on the pause screen under the friendly line. */
fun pauseStats(opens: Int, ms: Long): String {
    val o = when {
        opens <= 1 -> "Opened once today"
        else -> "Opened $opens times today"
    }
    val t = if (ms < 60_000L) "less than a minute so far" else "${fmtMinutes(ms)} so far"
    return "$o · $t"
}

/** Text shown in the app list. */
fun listStats(opens: Int, ms: Long): String {
    val o = if (opens == 1) "1 open" else "$opens opens"
    return "Today: $o · ${fmtMinutes(ms)}"
}
