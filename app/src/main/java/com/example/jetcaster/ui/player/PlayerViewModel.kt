/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetcaster.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.example.jetcaster.data.Episode
import com.example.jetcaster.data.EpisodeStore
import com.example.jetcaster.data.PodcastStore
import com.example.jetcaster.data.constant.K
import com.example.jetcaster.data.service.ConnectionState
import com.example.jetcaster.data.service.MediaPlayerService
import com.example.jetcaster.data.service.MediaPlayerServiceConnection
import com.example.jetcaster.util.currentPosition
import com.example.jetcaster.util.isPlayEnabled
import com.example.jetcaster.util.isPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val duration: Duration? = null,
    val podcastName: String = "",
    val podcastImageUrl: String = "",
    val isPlaying: Boolean = false
)

/**
 * ViewModel that handles the business logic and screen state of the Player screen
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    episodeStore: EpisodeStore,
    podcastStore: PodcastStore,
    private val serviceConnection: MediaPlayerServiceConnection,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var currentPlaybackPosition by mutableStateOf(0L)

    val podcastIsPlaying: Boolean
        get() = playbackState.value?.isPlaying == true

    val currentEpisodeProgress: Float
        get() {
            if (currentEpisodeDuration > 0) {
                return currentPlaybackPosition.toFloat() / currentEpisodeDuration
            }
            return 0f
        }

    val currentPlaybackFormattedPosition: String
        get() = formatLong(currentPlaybackPosition)

    val currentEpisodeFormattedDuration: String
        get() = formatLong(currentEpisodeDuration)

    private val playbackState = serviceConnection.playbackState

    private val currentEpisodeDuration: Long
        get() = MediaPlayerService.currentDuration

    fun playPodcast(episodes: List<Episode>, currentEpisode: Episode) {
        serviceConnection.playPodcast(episodes)
        if (currentEpisode.uri == serviceConnection.currentPlayingEpisode.value?.uri) {
            tooglePlaybackState()
        } else {
            serviceConnection.transportControls.playFromMediaId(currentEpisode.uri, null)
        }
    }

    private fun preparePodcastPlaying(currentEpisode: Episode) {
        serviceConnection.playPodcast(listOf(currentEpisode))
        if (currentEpisode.uri != serviceConnection.currentPlayingEpisode.value?.uri) {
            serviceConnection.transportControls.playFromMediaId(currentEpisode.uri, null)
        } else {
            //tooglePlaybackState
        }
    }

    fun tooglePlaybackState() {
        when {

            podcastIsPlaying -> {
                serviceConnection.transportControls.pause()
            }
            playbackState.value?.isPlayEnabled == true -> {
                serviceConnection.transportControls.play()
            }
        }
        uiState = uiState.copy(isPlaying = podcastIsPlaying)
    }

    fun stopPlayback() {
        serviceConnection.transportControls.stop()
    }

    fun calculateColorPalette(drawable: Drawable, onFinised: (Color) -> Unit) {
        val bitmap = (drawable as BitmapDrawable).bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Palette.from(bitmap).generate { palette ->
            palette?.darkVibrantSwatch?.rgb?.let { colorValue ->
                onFinised(Color(colorValue))
            }
        }
    }

    fun fastForward() {
        serviceConnection.fastForward()
    }

    fun rewind() {
        serviceConnection.rewind()
    }

    /**
     * @param value 0.0 to 1.0
     */
    fun seekToFraction(value: Float) {
        serviceConnection.transportControls.seekTo(
            (currentEpisodeDuration * value).toLong()
        )
    }

    suspend fun updateCurrentPlaybackPosition() {
        val currentPosition = playbackState.value?.currentPosition
        if (currentPosition != null && currentPosition != currentPlaybackPosition) {
            currentPlaybackPosition = currentPosition
        }
        delay(K.PLAYBACK_POSITION_UPDATE_INTERVAL)
        updateCurrentPlaybackPosition()
    }

    private fun formatLong(value: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(value)
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection.unsubscribe(
            K.MEDIA_ROOT_ID,
            object : MediaBrowserCompat.SubscriptionCallback() {})
    }

    // episodeUri should always be present in the PlayerViewModel.
    // If that's not the case, fail crashing the app!
    private val episodeUri: String = Uri.decode(savedStateHandle.get<String>("episodeUri")!!)

    var uiState by mutableStateOf(PlayerUiState())
        private set

    init {
        viewModelScope.launch {
            val episode = episodeStore.episodeWithUri(episodeUri).first()
            val podcast = podcastStore.podcastWithUri(episode.podcastUri).first()
            // preparePodcastPlaying(episode)
            uiState = PlayerUiState(
                title = episode.title,
                duration = episode.duration,
                podcastName = podcast.title,
                podcastImageUrl = podcast.imageUrl ?: ""
            )
            serviceConnection.connectionState
                .filter { it == ConnectionState.Connected }
                .collect {
                    preparePodcastPlaying(episode)
                }
        }
    }
}
