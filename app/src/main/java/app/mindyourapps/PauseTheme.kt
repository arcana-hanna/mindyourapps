package app.mindyourapps

/**
 * Colours for the pause screen. All values are ARGB ints.
 * The BLUR theme draws a translucent scrim over the live, blurred app instead of an image.
 */
enum class PauseTheme(
    val label: String,
    val bg: Int,
    val bgNoBlur: Int,
    val text: Int,
    val subtext: Int,
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val blur: Boolean,
    val light: Boolean,
) {
    BLUE(
        label = "Classic blue",
        bg = 0xFFF3F7FF.toInt(),
        bgNoBlur = 0xFFF3F7FF.toInt(),
        text = 0xFF0F1E3D.toInt(),
        subtext = 0xFF5B6B8C.toInt(),
        primary = 0xFF1E6FEB.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        secondary = 0xFFDCE8FF.toInt(),
        onSecondary = 0xFF1E4FA8.toInt(),
        blur = false,
        light = true,
    ),
    PASTEL(
        label = "Pastel",
        bg = 0xFFFFF6F0.toInt(),
        bgNoBlur = 0xFFFFF6F0.toInt(),
        text = 0xFF40364F.toInt(),
        subtext = 0xFF8A7B98.toInt(),
        primary = 0xFFC9B8F5.toInt(),
        onPrimary = 0xFF35295E.toInt(),
        secondary = 0xFFCDEFE0.toInt(),
        onSecondary = 0xFF2E5B49.toInt(),
        blur = false,
        light = true,
    ),
    DARK(
        label = "Dark minimal",
        bg = 0xFF0A0A0B.toInt(),
        bgNoBlur = 0xFF0A0A0B.toInt(),
        text = 0xFFEDEDED.toInt(),
        subtext = 0xFF8A8A92.toInt(),
        primary = 0xFFEDEDED.toInt(),
        onPrimary = 0xFF0A0A0B.toInt(),
        secondary = 0xFF1B1B1F.toInt(),
        onSecondary = 0xFFEDEDED.toInt(),
        blur = false,
        light = false,
    ),
    BLUR(
        label = "Blur",
        bg = 0x66101018.toInt(),
        bgNoBlur = 0xE6101018.toInt(),
        text = 0xFFFFFFFF.toInt(),
        subtext = 0xCCFFFFFF.toInt(),
        primary = 0xF2FFFFFF.toInt(),
        onPrimary = 0xFF14141C.toInt(),
        secondary = 0x33FFFFFF,
        onSecondary = 0xFFFFFFFF.toInt(),
        blur = true,
        light = false,
    );

    companion object {
        fun fromName(name: String?): PauseTheme =
            values().firstOrNull { it.name == name } ?: PASTEL
    }
}
