package com.foxplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class MusicLibrary(private val context: Context) {

    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff", "alac"
    )

    private val cachePrefs = context.getSharedPreferences("fox_library_cache", Context.MODE_PRIVATE)

    suspend fun loadSongs(folderStore: FolderStore = FolderStore(context)): List<Song> =
        withContext(Dispatchers.IO) {
            val folders = folderStore.getFolders()
            if (folders.isEmpty()) {
                loadFromMediaStore()
            } else {
                loadFromFoldersFast(folders)
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

    private suspend fun loadFromFoldersFast(folders: List<MusicFolder>): List<Song> =
        coroutineScope {
            val cacheKey = folders.joinToString("|") { it.uri }
            val cached = readCache(cacheKey)
            if (cached != null) return@coroutineScope cached

            // 1) Collect all audio DocumentFiles quickly (no metadata)
            val fileEntries = mutableListOf<Triple<DocumentFile, String, String>>()
            folders.forEach { folder ->
                val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return@forEach
                collectAudioFiles(root, folder.name, fileEntries)
            }

            // 2) Parse metadata in parallel (limited concurrency), NO embedded art
            val semaphore = Semaphore(6)
            val songs = fileEntries.map { (file, folderLabel, parentName) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        parseAudioFileFast(file, folderLabel, parentName)
                    }
                }
            }.awaitAll().filterNotNull()

            val sorted = songs.sortedBy { it.title.lowercase() }
            writeCache(cacheKey, sorted)
            sorted
        }

    private fun collectAudioFiles(
        dir: DocumentFile,
        folderLabel: String,
        out: MutableList<Triple<DocumentFile, String, String>>
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                collectAudioFiles(file, folderLabel, out)
            } else if (file.isFile && isAudioFile(file)) {
                out.add(Triple(file, folderLabel, dir.name ?: folderLabel))
            }
        }
    }

    /** Fast path: title/artist/album/duration only — skip embedded pictures */
    private fun parseAudioFileFast(
        file: DocumentFile,
        folderLabel: String,
        parentDirName: String
    ): Song? {
        val uri = file.uri
        val uriStr = uri.toString()
        val fallbackName = file.name?.substringBeforeLast('.') ?: "Desconocido"

        var title = fallbackName
        var artist = folderLabel
        var album = parentDirName
        var duration = 0L

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

            if (duration in 1 until 10_000) return null
        } catch (_: Exception) {
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
            artworkUri = null
        )
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val ext = name.substringAfterLast('.', "")
        if (ext in audioExtensions) return true
        val mime = file.type ?: return false
        return mime.startsWith("audio/")
    }

    private fun stableId(uri: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(uri.toByteArray())
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        return value and Long.MAX_VALUE
    }

    private fun readCache(key: String): List<Song>? {
        val raw = cachePrefs.getString("songs_$key", null) ?: return null
        val savedKey = cachePrefs.getString("key", null)
        if (savedKey != key) return null
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Song(
                            id = o.getLong("id"),
                            title = o.getString("title"),
                            artist = o.getString("artist"),
                            album = o.optString("album", ""),
                            duration = o.optLong("duration", 0L),
                            uri = o.getString("uri"),
                            albumId = 0L,
                            artworkUri = o.optString("artworkUri", null).takeIf { !it.isNullOrBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(key: String, songs: List<Song>) {
        try {
            val arr = JSONArray()
            songs.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("title", s.title)
                        .put("artist", s.artist)
                        .put("album", s.album)
                        .put("duration", s.duration)
                        .put("uri", s.uri)
                        .put("artworkUri", s.artworkUri ?: "")
                )
            }
            cachePrefs.edit()
                .putString("key", key)
                .putString("songs_$key", arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clearCache() {
        cachePrefs.edit().clear().apply()
    }
}
