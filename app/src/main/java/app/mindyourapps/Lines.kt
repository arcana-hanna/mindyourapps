package app.mindyourapps

/** The friendly sentences shown on the pause screen. One is picked at random each time. */
object Lines {

    val greetings: List<String> = listOf(
        "Hey, is this what you want to do right now?",
        "Quick check-in: are you sure about this one?",
        "Pause. Take a breath. Still want to go in?",
        "Is this the best use of the next few minutes?",
        "Hey you. Opening this on purpose?",
        "Hold on a sec. What were you about to do?",
        "Friendly nudge: do you really need this right now?",
        "A little pause before you dive in.",
        "Is this what future you would pick?",
        "No judgement, just checking in. Still want to open it?",
        "Is this a want, or just a habit?",
        "You've got this. Open it on your terms.",
        "Still the plan? Go ahead, but with intention.",
        "Breathe in, breathe out. Still want to open it?",
        "Anything else you'd rather do right now?",
        "Take a second. Is it worth your time right now?",
        "Thumb on autopilot? Let's check in.",
        "Hello again! Are you opening this on purpose?",
        "Just a gentle tap on the shoulder. All good?",
        "Before you go in: how are you feeling?",
    )

    val timeUps: List<String> = listOf(
        "Time's up! Keep going or step out?",
        "Your time is up. Still enjoying it?",
        "Check-in time. Want to keep going?",
        "Time flies. Do you want to stay?",
        "Another quick check: still worth it?",
        "Here we are again. How's it going?",
        "Your timer ran out. Your call!",
    )

    /**
     * Wording for the "keep going until I leave" button. It changes every time on purpose, so it
     * never turns into a button your thumb hits without reading.
     */
    val staying: List<String> = listOf(
        "Don't bother me this session",
        "This session will take a while",
        "I'm not sure how long I'll be",
        "Leave me be until I'm done here",
        "No timer, I'll stop on my own",
        "Let me stay without a reminder",
        "I'll keep an eye on the time myself",
        "No reminder, I've got this",
    )

    private var last: String? = null
    private var lastStaying: String? = null

    fun pickStaying(): String {
        val options = staying.filter { it != lastStaying }
        val choice = options.random()
        lastStaying = choice
        return choice
    }

    /** Random line from [pool], avoiding the same one twice in a row. */
    fun pick(pool: List<String>): String {
        if (pool.isEmpty()) return ""
        val options = if (pool.size > 1) pool.filter { it != last } else pool
        val choice = options.random()
        last = choice
        return choice
    }
}
