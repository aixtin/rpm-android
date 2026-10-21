package com.rpm.player.phone.stage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rpm.player.core.playback.AudioTap

/** Which visualisers this build draws. A look is listed in the picker only once it is here ([StageLook.isDrawn]). */
object StageLooks {
    private val ready = emptySet<StageLook>()

    fun drawn(look: StageLook): Boolean = look in ready
}

/** The visualiser [look], drawn over the whole stage and listening to [tap]. */
@Composable
fun StageSurface(look: StageLook, tap: AudioTap, modifier: Modifier) {
}
