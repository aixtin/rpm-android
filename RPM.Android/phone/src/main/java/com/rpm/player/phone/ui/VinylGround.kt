package com.rpm.player.phone.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rpm.player.phone.R
import com.rpm.player.phone.design.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The background of the app, as on the iPhone (`VinylGround` in RPMUI/VinylGround.swift). It is the app's
 * colour, the backdrop picture over it, and a 20% black layer over that. Nothing else.
 *
 * The backdrop is the RPaMp picture, the default on iPhone (`Backdrop.Choice.standard`). No screen lets the
 * user choose another backdrop yet.
 *
 * **Laid out in the window, not in the container that draws it.** The iPhone app works the same way. If each
 * screen cropped the picture to its own box, the background would move between screens of different heights.
 * The picture fills the window. It is centred across the window and hangs from the top, wherever the
 * container is.
 */
@Composable
fun VinylGround(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val plate by produceState(Plates.held) { value = Plates.ground(context) }
    val window = LocalWindowInfo.current.containerSize
    var here by remember { mutableStateOf(Offset.Zero) }

    Box(modifier.fillMaxSize().background(Ink.ground).onGloballyPositioned { here = it.positionInRoot() }) {
        plate?.let { picture ->
            Canvas(Modifier.fillMaxSize()) {
                val fill = max(window.width / picture.width.toFloat(), window.height / picture.height.toFloat())
                val width = (picture.width * fill).roundToInt()
                val height = (picture.height * fill).roundToInt()
                drawImage(
                    picture,
                    dstOffset = IntOffset(((window.width - width) / 2f - here.x).roundToInt(), (-here.y).roundToInt()),
                    dstSize = IntSize(width, height),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0x33 / 255f)))
    }
}

/**
 * The background picture, decoded once and kept for the life of the process. The same picture is behind every screen.
 */
private object Plates {
    @Volatile
    var held: ImageBitmap? = null
        private set

    suspend fun ground(context: Context): ImageBitmap? {
        held?.let { return it }
        return withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(context.resources, R.drawable.background_rpamp)?.asImageBitmap()
        }?.also { held = it }
    }
}
