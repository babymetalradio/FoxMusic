package com.foxplayer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaylistStore(context: Context) {

    private val prefs = context.getSharedPreferences("fox_playlists", Context.MODE_PRIVATE)

    fun getAll(): List<Playlist> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val idsArr = obj.optJSONArray("songIds") ?: JSONArray()
                    val ids = buildList {
                        for (j in 0 until idsArr.length()) {
                            add(idsArr.getLong(j))
                        }
                    }
                    add(
                        Playlist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            songIds = ids
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            val ids = JSONArray()
            pl.songIds.forEach { ids.put(it) }
            obj.put("songIds", ids)
            arr.put(obj)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun create(name: String): Playlist {
        val list = getAll().toMutableList()
        val pl = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Nueva playlist" },
            songIds = emptyList()
        )
        list.add(pl)
        saveAll(list)
        return pl
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun rename(id: String, newName: String) {
        saveAll(
            getAll().map {
                if (it.id == id) it.copy(name = newName.trim().ifBlank { it.name }) else it
            }
        )
    }

    fun addSong(playlistId: String, songId: Long) {
        saveAll(
            getAll().map { pl ->
                if (pl.id == playlistId && songId !in pl.songIds) {
                    pl.copy(songIds = pl.songIds + songId)
                } else pl
            }
        )
    }

    fun removeSong(playlistId: String, songId: Long) {
        saveAll(
            getAll().map { pl ->
                if (pl.id == playlistId) pl.copy(songIds = pl.songIds.filterNot { it == songId })
                else pl
            }
        )
    }

    fun resolveSongs(playlist: Playlist, allSongs: List<Song>): List<Song> {
        val map = allSongs.associateBy { it.id }
        return playlist.songIds.mapNotNull { map[it] }
    }

    companion object {
        private const val KEY = "playlists_json"
    }
}
