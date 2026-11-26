/**
 * Flow — albums.
 *
 * An album is a name and a set of project ids. Deliberately not a folder: photos
 * live in the library and an album only references them, so deleting an album
 * never touches a photo and one photo can sit in several albums.
 */

package com.zyon.flow.edit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Album(
    val id: Long,
    val name: String,
    val projectIds: Set<Long> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
)

object AlbumStore {

    private fun file(context: Context) = File(context.filesDir, "albums.json")

    fun load(context: Context): List<Album> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ids = o.optJSONArray("ids") ?: JSONArray()
                Album(
                    id = o.optLong("id", 0L),
                    name = o.optString("name", "Album"),
                    projectIds = (0 until ids.length()).map { ids.optLong(it, 0L) }.toSet(),
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, albums: List<Album>) {
        val arr = JSONArray()
        albums.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("createdAt", a.createdAt)
                    put("ids", JSONArray().apply { a.projectIds.forEach { put(it) } })
                },
            )
        }
        runCatching { file(context).writeText(arr.toString()) }
    }

    fun create(context: Context, name: String): List<Album> {
        val albums = load(context) + Album(id = System.currentTimeMillis(), name = name.ifBlank { "Album" })
        save(context, albums)
        return albums
    }

    fun delete(context: Context, id: Long): List<Album> {
        val albums = load(context).filterNot { it.id == id }
        save(context, albums)
        return albums
    }

    fun add(context: Context, albumId: Long, projectIds: Collection<Long>): List<Album> {
        val albums = load(context).map {
            if (it.id == albumId) it.copy(projectIds = it.projectIds + projectIds) else it
        }
        save(context, albums)
        return albums
    }

    fun remove(context: Context, albumId: Long, projectIds: Collection<Long>): List<Album> {
        val albums = load(context).map {
            if (it.id == albumId) it.copy(projectIds = it.projectIds - projectIds.toSet()) else it
        }
        save(context, albums)
        return albums
    }
}
