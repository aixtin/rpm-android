package com.rpm.player.core.library

import com.rpm.player.core.room.RpmPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistPhotosTest {
    @Test fun aPlainMoodWearsItsPhotographWashedInItsHue() {
        // Calm's hue is Azure, and a plain mood's seed is 977 * 10 + 5, which lands on it.
        assertEquals(PlaylistPhoto("calm-all.jpg", RpmPalette.RING[5]), PlaylistPhotos.of("Calm", null, 0))
    }

    @Test fun aGenreCutWearsItsOwn() {
        assertEquals("calm-rock.jpg", PlaylistPhotos.of("Calm", "Rock", 0)?.file)
    }

    @Test fun listsWithNoPhotographHaveNone() {
        assertNull(PlaylistPhotos.of("RightNow", null, 0))
        assertNull(PlaylistPhotos.of(null, null, 0))
        assertNull(PlaylistPhotos.of("Calm", "No such genre", 0))
    }

    /** Every file the table names is in the assets, so a list never asks for a picture that is not there. */
    @Test fun everyFileInTheTableIsShipped() {
        val folder = File("src/main/assets/" + PlaylistPhotos.FOLDER)
        val missing = PlaylistPhotoFiles.BY_CUT.values.filterNot { File(folder, it).isFile }
        assertTrue("missing: $missing", missing.isEmpty())
    }
}
