package com.foxplayer.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class MusicFolder(
    val uri: String,
    val name: String
)

class FolderStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("fox_folders", Context.MODE_PRIVATE)

    fun getFolders(): List<MusicFolder> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        MusicFolder(
                            uri = obj.getString("uri"),
                            name = obj.optString("name", "Carpeta")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<MusicFolder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(
                JSONObject()
                    .put("uri", f.uri)
                    .put("name", f.name)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun addFolder(uri: Uri, name: String): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val list = getFolders().toMutableList()
            val uriStr = uri.toString()
            if (list.none { it.uri == uriStr }) {
                list.add(MusicFolder(uri = uriStr, name = name.ifBlank { "Carpeta" }))
                saveFolders(list)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun removeFolder(uriStr: String) {
        try {
            val uri = Uri.parse(uriStr)
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        saveFolders(getFolders().filterNot { it.uri == uriStr })
    }

    /** true = scan entire device library; false = only selected folders */
    fun isScanAll(): Boolean = getFolders().isEmpty()

    companion object {
        private const val KEY = "music_folders_json"
    }
}
