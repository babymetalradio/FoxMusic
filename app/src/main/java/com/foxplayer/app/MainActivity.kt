package com.foxplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.foxplayer.app.data.MusicLibrary
import com.foxplayer.app.data.Song
import com.foxplayer.app.data.Playlist
import com.foxplayer.app.data.PlaylistStore
import com.foxplayer.app.player.PlayerController
import com.foxplayer.app.ui.theme.FoxMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerController = PlayerController(this)
        playerController.connect()
        setContent {
            FoxMusicTheme {
                FoxMusicApp(playerController)
            }
        }
    }

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }
}

private enum class Screen { Library, Player }
private enum class LibraryTab { Songs, Playlists }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxMusicApp(controller: PlayerController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by controller.uiState.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.Library) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var libraryTab by remember { mutableStateOf(LibraryTab.Songs) }
    val playlistStore = remember { PlaylistStore(context) }
    var playlists by remember { mutableStateOf(playlistStore.getAll()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            scope.launch {
                isLoading = true
                songs = MusicLibrary(context).loadSongs()
                isLoading = false
            }
        }
    }

    fun checkAndLoad() {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted) {
            scope.launch {
                isLoading = true
                songs = MusicLibrary(context).loadSongs()
                isLoading = false
            }
        } else {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(Unit) { checkAndLoad() }

    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            controller.updatePosition()
            delay(500)
        }
    }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else {
            val q = searchQuery.trim().lowercase()
            songs.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }

    when (currentScreen) {
        Screen.Library -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Fox Music",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        actions = {
                            if (libraryTab == LibraryTab.Playlists) {
                                IconButton(onClick = { showCreatePlaylist = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Nueva playlist")
                                }
                            } else {
                                IconButton(onClick = { checkAndLoad() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                                }
                            }
                            IconButton(onClick = { showUrlDialog = true }) {
                                Icon(Icons.Default.Link, contentDescription = "URL")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    MiniPlayer(
                        uiState = uiState,
                        onTogglePlay = { controller.togglePlayPause() },
                        onOpenPlayer = { currentScreen = Screen.Player }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Tabs Canciones / Playlists
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TabChip(
                            label = "Canciones",
                            selected = libraryTab == LibraryTab.Songs,
                            onClick = {
                                libraryTab = LibraryTab.Songs
                                selectedPlaylistId = null
                            }
                        )
                        TabChip(
                            label = "Playlists",
                            selected = libraryTab == LibraryTab.Playlists,
                            onClick = {
                                libraryTab = LibraryTab.Playlists
                                selectedPlaylistId = null
                                playlists = playlistStore.getAll()
                            }
                        )
                    }

                    when {
                        libraryTab == LibraryTab.Playlists -> {
                            PlaylistsSection(
                                playlists = playlists,
                                allSongs = songs,
                                selectedPlaylistId = selectedPlaylistId,
                                onSelectPlaylist = { selectedPlaylistId = it },
                                onBackFromDetail = { selectedPlaylistId = null },
                                onDeletePlaylist = { id ->
                                    playlistStore.delete(id)
                                    playlists = playlistStore.getAll()
                                    if (selectedPlaylistId == id) selectedPlaylistId = null
                                },
                                onPlayPlaylist = { pl ->
                                    val list = playlistStore.resolveSongs(pl, songs)
                                    if (list.isNotEmpty()) {
                                        controller.playQueue(list, 0)
                                        currentScreen = Screen.Player
                                    }
                                },
                                onPlaySongInPlaylist = { pl, index ->
                                    val list = playlistStore.resolveSongs(pl, songs)
                                    if (list.isNotEmpty()) {
                                        controller.playQueue(list, index)
                                        currentScreen = Screen.Player
                                    }
                                },
                                onRemoveSong = { plId, songId ->
                                    playlistStore.removeSong(plId, songId)
                                    playlists = playlistStore.getAll()
                                },
                                onCreate = { showCreatePlaylist = true }
                            )
                        }

                        !hasPermission -> PermissionPrompt(onRequest = {
                            permissionLauncher.launch(permission)
                        })
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Escaneando música...")
                                }
                            }
                        }
                        songs.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.LibraryMusic,
                                        null,
                                        Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No se encontraron canciones")
                                    Text(
                                        "Asegúrate de tener archivos de audio en el dispositivo",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(
                                            horizontal = 32.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                    Button(onClick = { checkAndLoad() }) {
                                        Text("Reintentar")
                                    }
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                placeholder = { Text("Buscar canción, artista o álbum") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                        }
                                    }
                                }
                            )
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "${songs.size} canciones"
                                } else {
                                    "${filteredSongs.size} resultados"
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            if (filteredSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Sin resultados para \"$searchQuery\"",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else {
                                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                                    itemsIndexed(
                                        filteredSongs,
                                        key = { _, song -> song.id }
                                    ) { index, song ->
                                        SongRow(
                                            song = song,
                                            onClick = {
                                                val fullIndex = songs.indexOfFirst { it.id == song.id }
                                                if (fullIndex >= 0) {
                                                    controller.playQueue(songs, fullIndex)
                                                } else {
                                                    controller.playQueue(filteredSongs, index)
                                                }
                                                currentScreen = Screen.Player
                                            },
                                            onLongClick = {
                                                songForPlaylist = song
                                                showAddToPlaylist = true
                                                playlists = playlistStore.getAll()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Screen.Player -> {
            PlayerScreen(
                controller = controller,
                onBack = { currentScreen = Screen.Library },
                onOpenUrl = { showUrlDialog = true }
            )
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Reproducir URL", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Pega una URL directa de audio (.mp3, .m4a, etc.)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            controller.playUri(urlInput.trim())
                            showUrlDialog = false
                            currentScreen = Screen.Player
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Reproducir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Nueva playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nombre de la playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            playlistStore.create(newPlaylistName)
                            playlists = playlistStore.getAll()
                            newPlaylistName = ""
                            showCreatePlaylist = false
                            libraryTab = LibraryTab.Playlists
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddToPlaylist && songForPlaylist != null) {
        AlertDialog(
            onDismissRequest = {
                showAddToPlaylist = false
                songForPlaylist = null
            },
            title = { Text("Añadir a playlist", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        songForPlaylist!!.title,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (playlists.isEmpty()) {
                        Text("No hay playlists. Crea una primero.")
                    } else {
                        playlists.forEach { pl ->
                            Text(
                                text = "${pl.name} (${pl.songIds.size})",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playlistStore.addSong(pl.id, songForPlaylist!!.id)
                                        playlists = playlistStore.getAll()
                                        showAddToPlaylist = false
                                        songForPlaylist = null
                                    }
                                    .padding(vertical = 12.dp),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddToPlaylist = false
                    songForPlaylist = null
                    showCreatePlaylist = true
                }) { Text("Nueva playlist") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddToPlaylist = false
                    songForPlaylist = null
                }) { Text("Cancelar") }
            }
        )
    }

}

@Composable
fun PermissionPrompt(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                null,
                Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Permiso necesario", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Fox Music necesita acceso a tus archivos de audio para mostrar la biblioteca local.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Conceder permiso")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${song.artist} · ${song.durationFormatted}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    controller: PlayerController,
    onBack: () -> Unit,
    onOpenUrl: () -> Unit
) {
    val uiState by controller.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproduciendo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenUrl) {
                        Icon(Icons.Default.Link, contentDescription = "URL")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.artworkUri != null) {
                    AsyncImage(
                        model = uiState.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        Modifier.size(90.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                uiState.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                uiState.artist.ifBlank { " " },
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(28.dp))
            val progress = if (uiState.duration > 0) {
                (uiState.currentPosition.toFloat() / uiState.duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Slider(
                value = progress,
                onValueChange = { controller.seekTo((it * uiState.duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(uiState.currentPosition),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Text(
                    formatTime(uiState.duration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                IconButton(onClick = { controller.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(38.dp))
                }
                IconButton(
                    onClick = { controller.togglePlayPause() },
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        Modifier.size(42.dp),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { controller.skipNext() }) {
                    Icon(Icons.Default.SkipNext, "Siguiente", Modifier.size(38.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                IconButton(onClick = { controller.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (uiState.shuffleEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = { controller.cycleRepeatMode() }) {
                    val (icon, tint) = when (uiState.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                        else -> Icons.Default.Repeat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                    Icon(icon, contentDescription = "Repetir", tint = tint)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (uiState.isConnected) "● Conectado" else "○ Conectando...",
                fontSize = 12.sp,
                color = if (uiState.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}



@Composable
fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label)
    }
}

@Composable
fun PlaylistsSection(
    playlists: List<Playlist>,
    allSongs: List<Song>,
    selectedPlaylistId: String?,
    onSelectPlaylist: (String) -> Unit,
    onBackFromDetail: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlaySongInPlaylist: (Playlist, Int) -> Unit,
    onRemoveSong: (String, Long) -> Unit,
    onCreate: () -> Unit
) {
    val selected = playlists.find { it.id == selectedPlaylistId }

    if (selected != null) {
        val songsInPl = selected.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackFromDetail) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(selected.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${songsInPl.size} canciones", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { onPlayPlaylist(selected) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDeletePlaylist(selected.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
            if (songsInPl.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Playlist vacía\nMantén pulsada una canción para añadirla", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn {
                    itemsIndexed(songsInPl, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { onPlaySongInPlaylist(selected, index) },
                            onLongClick = { onRemoveSong(selected.id, song.id) }
                        )
                    }
                }
            }
        }
    } else if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QueueMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No hay playlists")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCreate) { Text("Crear playlist") }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tip: mantén pulsada una canción para añadirla",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    } else {
        LazyColumn {
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, pl ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(pl.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QueueMusic,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("${pl.songIds.size} canciones", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { onPlayPlaylist(pl) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    uiState: com.foxplayer.app.player.PlayerUiState,
    onTogglePlay: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    if (uiState.title == "Ninguna canción") return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPlayer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.artworkUri != null) {
                    AsyncImage(
                        model = uiState.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uiState.artist.ifBlank { " " },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
