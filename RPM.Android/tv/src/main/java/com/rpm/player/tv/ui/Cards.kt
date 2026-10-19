package com.rpm.player.tv.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.rpm.player.core.R
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Cover
import com.rpm.player.core.library.Kicker
import com.rpm.player.core.library.ListName
import com.rpm.player.core.library.moodSentence
import com.rpm.player.core.library.TrackList

@Composable
fun albumTitle(album: CloudAlbum): String = album.album.ifBlank { stringResource(R.string.Queue_UnknownAlbum) }

@Composable
fun albumArtist(album: CloudAlbum): String = album.artist.ifBlank { stringResource(R.string.Queue_UnknownArtist) }

/**
 * The line under a list's name on home. It says what the list is when it can, and how many songs the
 * list has when it cannot (HomeView's `HomeListCard.Caption`). A mood says which kinds of music it
 * drew from. Every other list says what it is in its own name, so the song count is shown instead.
 * The card, Quick selection and the banner all use this line, so a list reads the same way in all
 * three.
 */
@Composable
fun listCaption(list: TrackList): String =
    list.blurb?.let { moodSentence(it) } ?: stringResource(R.string.Home_Songs, list.tracks.size.toString())

@StringRes
fun kickerName(kicker: Kicker): Int = when (kicker) {
    Kicker.CONTINUE -> R.string.Home_Continue
    Kicker.ADDED -> R.string.Home_Added
    Kicker.MADE_FOR_YOU -> R.string.Home_MadeForYou
    Kicker.SUGGESTED -> R.string.Home_Suggested
    Kicker.PLAYED -> R.string.Home_Played
    Kicker.LISTENED -> R.string.Home_Listened
}

