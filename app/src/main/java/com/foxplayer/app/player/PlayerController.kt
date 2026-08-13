package com.foxplayer.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.foxplayer.app.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val title: String = "Ninguna canción",
    val artist: String = "",
    val artworkUri: String? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isConnected: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
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
                artist = metadata?.artist?.toString() ?: "",
                artworkUri = metadata?.artworkUri?.toString()
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePosition()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
        }
    }

    fun connect() {
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    controller = controllerFuture?.get()
                    controller?.addListener(playerListener)
                    _uiState.value = _uiState.value.copy(isConnected = true)
                    syncFromController()
                } catch (e: Exception) {
                    Log.e("PlayerController", "Error connecting controller", e)
                    _uiState.value = _uiState.value.copy(isConnected = false)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e("PlayerController", "Error creating session token", e)
        }
    }

    private fun syncFromController() {
        controller?.let { c ->
            val metadata = c.mediaMetadata
            _uiState.value = _uiState.value.copy(
                isPlaying = c.isPlaying,
                title = metadata.title?.toString() ?: "Ninguna canción",
                artist = metadata.artist?.toString() ?: "",
                artworkUri = metadata.artworkUri?.toString(),
                duration = if (c.duration > 0) c.duration else 0L,
                currentPosition = c.currentPosition,
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode
            )
        }
    }

    fun playUri(uri: String, title: String = "Streaming", artist: String = "Fox Music", artworkUri: String? = null) {
        val item = PlaybackService.mediaItemFromUri(uri, title, artist, artworkUri)
        val c = controller
        if (c != null) {
            c.setMediaItem(item)
            c.prepare()
            c.play()
            _uiState.value = _uiState.value.copy(title = title, artist = artist, artworkUri = artworkUri)
        } else {
            Log.e("PlayerController", "Controller is null, cannot play")
            connect()
        }
    }

    fun playSong(song: Song) {
        playUri(song.uri, song.title, song.artist, song.artworkUri)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: run {
            connect()
            return
        }
        if (songs.isEmpty()) return
        val items = songs.map {
            PlaybackService.mediaItemFromUri(it.uri, it.title, it.artist, it.artworkUri)
        }
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
        val current = songs.getOrNull(startIndex)
        if (current != null) {
            _uiState.value = _uiState.value.copy(
                title = current.title,
                artist = current.artist,
                artworkUri = current.artworkUri
            )
        }
    }

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(position: Long) { controller?.seekTo(position) }
    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem() }

    fun toggleShuffle() {
        val c = controller ?: return
        val newValue = !c.shuffleModeEnabled
        c.shuffleModeEnabled = newValue
        _uiState.value = _uiState.value.copy(shuffleEnabled = newValue)
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _uiState.value = _uiState.value.copy(repeatMode = next)
    }

    fun updatePosition() {
        controller?.let { c ->
            _uiState.value = _uiState.value.copy(
                currentPosition = c.currentPosition,
                duration = if (c.duration > 0) c.duration else 0L,
                isPlaying = c.isPlaying
            )
        }
    }

    fun release() {
        try {
            controller?.removeListener(playerListener)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (_: Exception) {}
        controller = null
        _uiState.value = PlayerUiState()
    }
}
