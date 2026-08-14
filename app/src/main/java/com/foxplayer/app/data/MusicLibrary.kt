package com.foxplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

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
        val artCacheDir = File(context.cacheDir, "album_art").apply { mkdirs() }

        folders.forEach { folder ->
            val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return@forEach
            scanDocumentTree(root, folder.name, songs, artCacheDir)
        }

        return songs.sortedBy { it.title.lowercase() }
    }

    private fun scanDocumentTree(
        dir: DocumentFile,
        folderLabel: String,
        out: MutableList<Song>,
        artCacheDir: File
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDocumentTree(file, folderLabel, out, artCacheDir)
            } else if (file.isFile && isAudioFile(file)) {
                parseAudioFile(file, folderLabel, dir.name ?: folderLabel, artCacheDir)?.let {
                    out.add(it)
                }
            }
        }
    }

    private fun parseAudioFile(
        file: DocumentFile,
        folderLabel: String,
        parentDirName: String,
        artCacheDir: File
    ): Song? {
        val uri = file.uri
        val uriStr = uri.toString()
        val fallbackName = file.name?.substringBeforeLast('.') ?: "Desconocido"

        var title = fallbackName
        var artist = folderLabel
        var album = parentDirName
        var duration = 0L
        var artworkUri: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?.let { title = it }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                ?.let { artist = it }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?.let { artist = it }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?.let { album = it }

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { duration = it }

            // Skip very short files (ringtones/notifications)
            if (duration in 1 until 10_000) return null

            // Embedded artwork
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                val artFile = File(artCacheDir, "${stableId(uriStr)}.jpg")
                if (!artFile.exists() || artFile.length() == 0L) {
                    artFile.writeBytes(picture)
                }
                if (artFile.exists() && artFile.length() > 0L) {
                    artworkUri = artFile.toURI().toString()
                }
            }
        } catch (_: Exception) {
            // Keep filename-based fallbacks
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return Song(
            id = stableId(uriStr),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uriStr,
            albumId = 0L,
            artworkUri = artworkUri
        )
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val ext = name.substringAfterLast('.', "")
        if (ext in audioExtensions) return true
        val mime = file.type ?: return false
        return mime.startsWith("audio/")
    }

    /** Stable positive Long from URI so playlists survive rescan */
    private fun stableId(uri: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(uri.toByteArray())
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        // Keep positive (MediaStore-like)
        return value and Long.MAX_VALUE
    }
}
