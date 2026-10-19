package com.rpm.player.tv.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * How bright and how saturated the screens are drawn, chosen in the settings panel. It helps on HDR televisions
 * that show SDR content at their own full brightness. [light] multiplies every colour, so white is that share of
 * full; [saturation] pulls colours toward grey. The live room has a surface of its own, so it takes [light] in its
 * shader (`RoomTarget.light`); everything else that Compose draws takes [filter].
 */
enum class PictureLook(val light: Float, val saturation: Float) {
    Standard(1f, 1f),
    Softer(0.85f, 1f),
    Muted(0.78f, 0.8f),
    ;

    /** The colour filter over the screens, or null for [Standard], which then needs no extra layer. */
    val filter: ColorFilter? by lazy { if (light == 1f && saturation == 1f) null else ColorFilter.colorMatrix(pictureMatrix(light, saturation)) }
}

/** Saturation about Rec.709 luminance, then every channel times [light]. */
private fun pictureMatrix(light: Float, saturation: Float): ColorMatrix {
    val r = 0.2126f * (1 - saturation)
    val g = 0.7152f * (1 - saturation)
    val b = 0.0722f * (1 - saturation)
    return ColorMatrix(
        floatArrayOf(
            (r + saturation) * light, g * light, b * light, 0f, 0f,
            r * light, (g + saturation) * light, b * light, 0f, 0f,
            r * light, g * light, (b + saturation) * light, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}
