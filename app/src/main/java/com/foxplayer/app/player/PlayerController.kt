package com.foxplayer.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val title: String = "Ninguna canción",
    val artist: String = "",
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isConnected: Boolean = false
)

class PlayerController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val metadata = mediaItem?.mediaMetadata
            _uiState.value = _uiState.value.copy(
                title = metadata?.title?.toString() ?: "Desconocido",
                artist = metadata?.artist?.toString() ?: ""
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                updatePosition()
            }
        }
    }

    fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            _uiState.value = _uiState.value.copy(isConnected = true)

            // Update initial state
            controller?.let { c ->
                val metadata = c.mediaMetadata
                _uiState.value = _uiState.value.copy(
                    isPlaying = c.isPlaying,
                    title = metadata.title?.toString() ?: "Ninguna canción",
                    artist = metadata.artist?.toString() ?: "",
                    duration = c.duration.coerceAtLeast(0L),
                    currentPosition = c.currentPosition
                )
            }
        }, MoreExecutors.directExecutor())
    }

    fun playUri(uri: String, title: String = "Streaming", artist: String = "Fox Music") {
        val item = PlaybackService.mediaItemFromUri(uri, title, artist)
        controller?.setMediaItem(item)
        controller?.prepare()
        controller?.play()
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        if (controller?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    fun updatePosition() {
        controller?.let { c ->
            _uiState.value = _uiState.value.copy(
                currentPosition = c.currentPosition,
                duration = c.duration.coerceAtLeast(0L),
                isPlaying = c.isPlaying
            )
        }
    }

    fun release() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        _uiState.value = PlayerUiState()
    }
}
