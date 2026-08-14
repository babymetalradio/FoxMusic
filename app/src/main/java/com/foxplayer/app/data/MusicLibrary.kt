package com.foxplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicLibrary(private val context: Context) {

    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff", "alac"
    )

    suspend fun loadSongs(folderStore: FolderStore = FolderStore(context)): List<Song> =
        withContext(Dispatchers.IO) {
            val folders = folderStore.getFolders()
            if (folders.isEmpty()) {
                loadFromMediaStore()
            } else {
                loadFromFolders(folders)
            }
        }

    private fun loadFromMediaStore(): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationCol)
                if (duration < 10_000) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()
                val artworkUri = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                } else null

                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Desconocido",
                        artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" }
                            ?: "Artista desconocido",
                        album = cursor.getString(albumCol) ?: "Álbum desconocido",
                        duration = duration,
                        uri = contentUri,
                        albumId = albumId,
                        artworkUri = artworkUri
                    )
                )
            }
        }
        return songs
    }

    private fun loadFromFolders(folders: List<MusicFolder>): List<Song> {
        val songs = mutableListOf<Song>()
        var nextId = 1L

        folders.forEach { folder ->
            val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return@forEach
            scanDocumentTree(root, folder.name, songs) { nextId++ }
        }

        return songs.sortedBy { it.title.lowercase() }
    }

    private fun scanDocumentTree(
        dir: DocumentFile,
        folderLabel: String,
        out: MutableList<Song>,
        nextId: () -> Long
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDocumentTree(file, folderLabel, out, nextId)
            } else if (file.isFile && isAudioFile(file)) {
                val name = file.name ?: continue
                val title = name.substringBeforeLast('.')
                out.add(
                    Song(
                        id = nextId(),
                        title = title,
                        artist = folderLabel,
                        album = dir.name ?: folderLabel,
                        duration = 0L,
                        uri = file.uri.toString(),
                        albumId = 0L,
                        artworkUri = null
                    )
                )
            }
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val ext = name.substringAfterLast('.', "")
        if (ext in audioExtensions) return true
        val mime = file.type ?: return false
        return mime.startsWith("audio/")
    }
}
