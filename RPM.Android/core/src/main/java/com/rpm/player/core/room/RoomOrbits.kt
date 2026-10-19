package com.rpm.player.core.room

import kotlin.math.cos
import kotlin.math.sin

/**
 * Where each of the room's fifteen lobes is this frame, and how large: the orbit arithmetic from
 * RoomSource.Glsl (RPM.Core/Viz/RoomSource.cs), formula for formula, evaluated once per frame instead
 * of once per pixel (see [RoomShaders]). Written as (x, y, radius) triples, ready for `glUniform3fv`.
 * Computed in double, so the orbits keep their precision however long the room has run.
 */
internal class RoomOrbits {
    val lobes1 = FloatArray(LOBES1 * 3)
    val lobes2 = FloatArray(LOBES2 * 3)
    val lobes3 = FloatArray(LOBES3 * 3)

    fun update(t: Double, bass: Float, mid: Float, air: Float) {
        // The third family, at AIR_RATE.
        val t3 = t * AIR_RATE
        for (i in 0 until LOBES3) {
            val fi = i.toDouble()
            val a = t3 * (0.095 + fi * 0.026) + fi * 2.9
            put(
                lobes3, i,
                cos(a * 0.94 + fi * 1.9) * 0.70,
                sin(a * 1.37 + fi * 2.6) * 0.42,
                (0.085 + 0.030 * sin(t3 * 0.41 + fi * 3.1)) * (1.0 + air * 0.85),
            )
        }
        // The figures; the golden angle spreads their phases.
        for (i in 0 until LOBES1) {
            val fi = i.toDouble()
            val a = t * (0.047 + fi * 0.0115) + fi * 2.39996
            put(
                lobes1, i,
                cos(a + fi * 1.3) * (0.46 + 0.20 * sin(t * 0.031 + fi)),
                sin(a * 0.83 + fi * 2.1) * 0.32,
                (0.20 + 0.055 * sin(t * 0.21 + fi * 1.7)) * (1.0 + bass * 0.62),
            )
        }
        // The second family.
        for (i in 0 until LOBES2) {
            val fi = i.toDouble()
            val a = t * (0.083 + fi * 0.021) + fi * 1.7
            put(
                lobes2, i,
                cos(a * 1.21 + fi * 2.7) * 0.62,
                sin(a + fi * 0.9) * 0.38,
                (0.145 + 0.045 * sin(t * 0.37 + fi * 2.3)) * (1.0 + mid * 0.55),
            )
        }
    }

    private fun put(into: FloatArray, i: Int, x: Double, y: Double, r: Double) {
        into[i * 3] = x.toFloat()
        into[i * 3 + 1] = y.toFloat()
        into[i * 3 + 2] = r.toFloat()
    }

    companion object {
        const val LOBES1 = 6
        const val LOBES2 = 5
        const val LOBES3 = 4
        const val AIR_RATE = 1.3
    }
}
