package com.rpm.player.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpm.player.core.R

/*
 * The desktop's Pegasus sizes, carried to the television.
 *
 * Pegasus sizes most things as a base number times `s = clamp(height / 900, 1, 1.5)`, which is 1.2 on a
 * 1920 x 1080 window, and a few things in fixed pixels. A television is 960 x 540 dp, half of 1920 x
 * 1080, so a desktop pixel is half a dp:
 *
 * - a scaled size `N·s` is `N * 1.2 / 2` dp, written `N.s`;
 * - a fixed size `N` is `N / 2` dp, written `N.px`;
 * - type sizes are the same, as `N.ts` (scaled) and `N.tpx` (fixed).
 *
 * Fractions of the screen (the sleeve is 0.38 of the height) are computed from the screen itself.
 * See the desktop's PegasusChrome.xaml.cs (QueueScale).
 */

/** dp per desktop pixel on a 1920-wide window. */
private const val DESK_PX = 0.5f

/** dp per scaled desktop unit: `s` (1.2 at 1080) times a desktop pixel. */
private const val DESK_S = 1.2f * DESK_PX

/** dp per drawer unit: the desktop's drawer is drawn at `s * 24 / 14` (DrawerScale). */
private const val DESK_DS = DESK_S * 24f / 14f

val Number.s: Dp get() = (toFloat() * DESK_S).dp
val Number.ds: Dp get() = (toFloat() * DESK_DS).dp
val Number.tds: TextUnit get() = (toFloat() * DESK_DS).sp
val Number.px: Dp get() = (toFloat() * DESK_PX).dp
val Number.ts: TextUnit get() = (toFloat() * DESK_S).sp
val Number.tpx: TextUnit get() = (toFloat() * DESK_PX).sp

/**
 * The face Pegasus writes in. The desktop names no face, so Windows draws Segoe UI. Segoe UI may not
 * be shipped on Android, and Selawik is Microsoft's open replacement for it, drawn to the metrics of
 * Segoe UI (OFL 1.1). Characters it lacks fall back to the system's faces.
 */
val Ui = FontFamily(
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
    Font(R.font.selawik_bold, FontWeight.Bold),
)

/** Font Awesome 5 Pro, the desktop's icon faces (PegasusFa and PegasusFaRegular). */
val FaSolid = FontFamily(Font(R.font.fa_solid, FontWeight.Normal))
val FaRegular = FontFamily(Font(R.font.fa_regular, FontWeight.Normal))

/** A line of Pegasus type: the face, a size, a weight. Line height is the face's own, as on Windows. */
fun ui(size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(
        fontFamily = Ui,
        fontWeight = weight,
        fontSize = size,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** A Font Awesome glyph by its code point, in the solid face unless [regular]. */
@Composable
fun Glyph(code: Char, size: TextUnit, color: Color, modifier: Modifier = Modifier, regular: Boolean = false) {
    Text(
        text = code.toString(),
        style = TextStyle(fontFamily = if (regular) FaRegular else FaSolid, fontSize = size, color = color),
        modifier = modifier,
    )
}

/** The glyphs Pegasus uses, by the names Font Awesome gives them. */
object Fa {
    /** Two people: the compilations shelf, which is nobody in particular. */
    const val USERS = '\uf0c0'
    const val SEARCH = ''
    const val RECORD = ''
    const val USER = ''
    const val MUSIC = ''
    const val PLAYLISTS = ''
    const val RADIO = ''
    /** A cassette tape: the mixtape (songs like one song, kept coming), kept apart from the internet radio's mark. */
    const val MIXTAPE = '\uF8AB'
    const val GEAR = ''
    const val BARS = ''
    const val ARROW_LEFT = ''
    const val HEART = ''
    const val BACKWARD = ''
    const val PLAY = ''
    const val PAUSE = ''
    const val STOP = ''
    const val FORWARD = ''
    const val VOLUME = ''
    const val CHEVRON_LEFT = ''
    const val CHEVRON_RIGHT = ''
    const val CHEVRON_DOWN = ''
    /** Two arrows in a circle: read again (the side menu's reload key). */
    const val RELOAD = '\uf021'
    const val SWITCH = ''
    const val SORT = ''
    const val SHUFFLE = ''
    const val ANTENNA = ''
    const val GRIP = ''
    const val CLOSE = ''
    const val CHECK = ''
    const val COMPRESS = ''
    const val EXPAND = ''
    const val EYE_SLASH = ''
    const val LAPTOP = ''
    const val HOUSE = ''
    const val STAR = ''
    /** The side menu's Now playing. */
    const val WAVEFORM = ''
    /** The radio's mode keys: the tuner, news and local stations. */
    const val TOWER = ''
    const val NEWSPAPER = ''
    const val PIN = ''
    /** The settings' Display section. */
    const val TV = ''
    /** The menus' entries, by the desktop's menus (AlbumMenuFactory, TrackMenuFactory, PlaylistView): play
     * next is the step forward, a new playlist the plus, taking a row out of a list the minus. */
    const val STEP_FORWARD = '\uf051'
    const val PLUS = '\uf067'
    const val MINUS = '\uf068'
    /** The player's layout key. */
    const val LAYOUT = ''
    /** The key legend's keys ([ControlsLegend]): Back (undo), the arrows (arrows-alt), Left and Right
     * (arrows-alt-h), Up, Right, and OK (dot-circle). */
    const val UNDO = ''
    const val ARROWS = ''
    const val ARROWS_H = ''
    const val ARROW_UP = ''
    const val ARROW_RIGHT = ''
    const val OK = ''
}

/** The colours Pegasus paints with, as the desktop writes them (#AARRGGBB). */
object Ink {
    val Words = Color(0xFFFFFFFF)
    val Artist = Color(0xB3FFFFFF)
    val Album = Color(0x80FFFFFF)
    val Key = Color(0xE6FFFFFF)
    val Card = Color(0xF2FFFFFF)
    val Dim = Color(0x99FFFFFF)
    /** The flat shade over the room on every collection screen (PegasusChrome.xaml:681). */
    val Scrim = Color(0x73000000)
    /** The light glass: fill and rim (LightSmokeBrush, LightSmokeRimBrush). */
    val Smoke = Color(0x26FFFFFF)
    val SmokeRimTop = Color(0x59FFFFFF)
    val SmokeRimMiddle = Color(0x26FFFFFF)
    val SmokeRimBottom = Color(0x66FFFFFF)
    /** A cover's well before the picture arrives. */
    val Well = Color(0x22FFFFFF)
    /** The heart when set (#FFEE0709) and when not (#66FFFFFF). */
    val Loved = Color(0xFFEE0709)
    val Unloved = Color(0x66FFFFFF)
}

/** The E that marks explicit lyrics (Controls/ExplicitBadge): a square of [size] in `#8CFFFFFF`, corner
 * 2.5 at 15, a bold E in `#14161A` filling it. */
@Composable
fun ExplicitBadge(size: Dp) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(size)
            .background(Color(0x8CFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(size * (2.5f / 15f))),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            "E",
            style = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Bold, fontSize = (size.value - 2.px.value).sp, color = Color(0xFF14161A)),
        )
    }
}
