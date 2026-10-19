package com.rpm.player.core.library

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The genres' outlines, each parsed once and kept for the life of the app. Parsing one is several thousand path
 * commands, so it is done off the main thread. The TV's and the phone's lettering both read it. */
object WordmarkPaths {
    private val kept = HashMap<String, Path>()

    @Synchronized
    fun cached(mark: GenreWordmark): Path? = kept[mark.genre]

    suspend fun of(mark: GenreWordmark): Path {
        cached(mark)?.let { return it }
        val parsed = withContext(Dispatchers.Default) {
            PathParser().parsePathString(mark.path).toPath().apply {
                fillType = if (mark.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
            }
        }
        synchronized(this) { kept[mark.genre] = parsed }
        return parsed
    }
}
