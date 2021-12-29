package com.example.jetcaster.data.service

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.mutableStateOf
import com.example.jetcaster.data.Episode
import com.example.jetcaster.data.constant.K
import com.example.jetcaster.data.exoplayer.PodcastMediaSource
import com.example.jetcaster.util.currentPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaPlayerServiceConnection(
    context: Context,
    private val mediaSource: PodcastMediaSource,
) {

    var playbackState = mutableStateOf<PlaybackStateCompat?>(null)
    var currentPlayingEpisode = mutableStateOf<Episode?>(null)

    private val connectionFlow = MutableStateFlow<ConnectionState>(ConnectionState.NoConnection)
    //TODO fix duplication like was described here https://medium.com/google-developer-experts/avoid-backing-properties-for-livedata-and-stateflow-706006c9867e
    val connectionState: StateFlow<ConnectionState> get() = connectionFlow

    lateinit var mediaController: MediaControllerCompat

    val transportControls: MediaControllerCompat.TransportControls
        get() = mediaController.transportControls

    private val mediaBrowser = MediaBrowserCompat(
        context,
        ComponentName(context, MediaPlayerService::class.java),
        MediaBrowserConnectionCallback(context),
        null
    ).apply {

        connect()
    }

    fun playPodcast(episodes: List<Episode>) {
        mediaSource.setEpisodes(episodes)
        mediaBrowser.sendCustomAction(K.START_MEDIA_PLAYBACK_ACTION, null, null)
    }

    fun fastForward(seconds: Int = 10) {
        playbackState.value?.currentPosition?.let { currentPosition ->
            transportControls.seekTo(currentPosition + seconds * 1000)
        }
    }

    fun rewind(seconds: Int = 10) {
        playbackState.value?.currentPosition?.let { currentPosition ->
            transportControls.seekTo(currentPosition - seconds * 1000)
        }
    }

    fun subscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        mediaBrowser.subscribe(parentId, callback)
    }

    fun unsubscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        mediaBrowser.unsubscribe(parentId, callback)
    }

    fun refreshMediaBrowserChildren() {
        mediaBrowser.sendCustomAction(K.REFRESH_MEDIA_BROWSER_CHILDREN, null, null)
    }

    private inner class MediaBrowserConnectionCallback(
        private val context: Context
    ) : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            super.onConnected()
            mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                registerCallback(createMediaMediaControllerCallback { onConnectionSuspended() })
            }
            connectionFlow.value = ConnectionState.Connected
        }

        override fun onConnectionSuspended() {
            super.onConnectionSuspended()
            connectionFlow.value = ConnectionState.NoConnection
        }

        override fun onConnectionFailed() {
            super.onConnectionFailed()
            connectionFlow.value = ConnectionState.NoConnection
        }
    }

    private fun createMediaMediaControllerCallback(onConnectionSuspended: () -> Unit) =
        object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                super.onPlaybackStateChanged(state)
                playbackState.value = state
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                super.onMetadataChanged(metadata)
                currentPlayingEpisode.value = metadata?.let {
                    mediaSource.podcastEpisodes.find {
                        it.uri == metadata.description?.mediaId
                    }
                }
            }

            override fun onSessionDestroyed() {
                super.onSessionDestroyed()
                onConnectionSuspended()
            }
        }
}

sealed class ConnectionState {
    object NoConnection : ConnectionState()
    object Connected : ConnectionState()
}
