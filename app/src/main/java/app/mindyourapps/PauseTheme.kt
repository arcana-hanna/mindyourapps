package app.mindyourapps

/** Colours for one look of the pause screen. All values are ARGB ints. */
class Palette(
    /** Screen background. For Blur this is a see-through tint over the blurred app. */
    val bg: Int,
    /** Background used instead when the phone can't blur (older Android, battery saver). */
    val bgNoBlur: Int,
    val text: Int,
    val subtext: Int,
    /** Big return button */
    val primary: Int,
    val onPrimary: Int,
    /** Smaller buttons */
    val secondary: Int,
    val onSecondary: Int,
    /** The panel that holds today's numbers */
    val card: Int,
)

/**
 * The four looks. Classic blue and Pastel have a light and a dark palette and follow the
 * phone's system-wide dark mode, so the pause screen never flashes white at night.
 * Dark minimal and Blur are always dark by design.
 */
enum class PauseTheme(
    val label: String,
    val blur: Boolean,
    private val light: Palette,
    private val dark: Palette = light,
) {
    BLUE(
        label = "Classic blue",
        blur = false,
        light = Palette(
            bg = 0xFFF3F7FF.toInt(), bgNoBlur = 0xFFF3F7FF.toInt(),
            text = 0xFF0F1E3D.toInt(), subtext = 0xFF5B6B8C.toInt(),
            primary = 0xFF1E6FEB.toInt(), onPrimary = 0xFFFFFFFF.toInt(),
            secondary = 0xFFDCE8FF.toInt(), onSecondary = 0xFF1E4FA8.toInt(),
            card = 0xFFE4EDFF.toInt(),
        ),
        dark = Palette(
            bg = 0xFF0D1320.toInt(), bgNoBlur = 0xFF0D1320.toInt(),
            text = 0xFFE8EEFC.toInt(), subtext = 0xFF93A1BF.toInt(),
            primary = 0xFF6FA0FF.toInt(), onPrimary = 0xFF06162F.toInt(),
            secondary = 0xFF1B2640.toInt(), onSecondary = 0xFFBBD0FF.toInt(),
            card = 0xFF151F36.toInt(),
        ),
    ),
    PASTEL(
        label = "Pastel",
        blur = false,
        light = Palette(
            bg = 0xFFFFF6F0.toInt(), bgNoBlur = 0xFFFFF6F0.toInt(),
            text = 0xFF40364F.toInt(), subtext = 0xFF6F6180.toInt(),
            primary = 0xFFC9B8F5.toInt(), onPrimary = 0xFF35295E.toInt(),
            secondary = 0xFFCDEFE0.toInt(), onSecondary = 0xFF2E5B49.toInt(),
            card = 0xFFFFE8DA.toInt(),
        ),
        dark = Palette(
            bg = 0xFF18141E.toInt(), bgNoBlur = 0xFF18141E.toInt(),
            text = 0xFFF0EAF6.toInt(), subtext = 0xFFA89DB8.toInt(),
            primary = 0xFFC9B8F5.toInt(), onPrimary = 0xFF2A1F4D.toInt(),
            secondary = 0xFF22332C.toInt(), onSecondary = 0xFFBFE8D5.toInt(),
            card = 0xFF241E2D.toInt(),
        ),
    ),
    DARK(
        label = "Dark minimal",
        blur = false,
        light = Palette(
            bg = 0xFF0A0A0B.toInt(), bgNoBlur = 0xFF0A0A0B.toInt(),
            text = 0xFFEDEDED.toInt(), subtext = 0xFF8A8A92.toInt(),
            primary = 0xFFEDEDED.toInt(), onPrimary = 0xFF0A0A0B.toInt(),
            secondary = 0xFF1B1B1F.toInt(), onSecondary = 0xFFEDEDED.toInt(),
            card = 0xFF131316.toInt(),
        ),
    ),
    BLUR(
        label = "Blur",
        blur = true,
        light = Palette(
            bg = 0x66101018, bgNoBlur = 0xE6101018.toInt(),
            text = 0xFFFFFFFF.toInt(), subtext = 0xCCFFFFFF.toInt(),
            primary = 0xF2FFFFFF.toInt(), onPrimary = 0xFF14141C.toInt(),
            secondary = 0x33FFFFFF, onSecondary = 0xFFFFFFFF.toInt(),
            card = 0x26FFFFFF,
        ),
    );

    fun palette(systemDark: Boolean): Palette = if (systemDark) dark else light

    companion object {
        fun fromName(name: String?): PauseTheme =
            entries.firstOrNull { it.name == name } ?: PASTEL
    }
}
