/**
 * Flow — photo store.
 *
 * Picker URIs (content://media/picker/…) are granted only to the activity that
 * received them. Once the app restarts the grant is gone, so reopening a saved
 * project threw SecurityException. The fix is to copy the photo into app storage
 * on import and keep working from our own file, which is also faster to decode
 * and survives the picker being replaced.
 */

package com.zyon.flow.edit

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object PhotoStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    private fun keyOf(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(uri.toString().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(24)
    }

    /**
     * Copy a picked photo into internal storage and return its local URI.
     * Falls back to the original URI if the copy fails, so import never dies.
     */
    suspend fun import(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        // Already ours?
        if (uri.scheme == "file") {
            val f = uri.path?.let { File(it) }
            if (f != null && f.exists()) return@withContext uri
        }
        val target = File(dir(context), "${keyOf(uri)}.jpg")
        if (target.exists() && target.length() > 0) return@withContext Uri.fromFile(target)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.exists() && target.length() > 0) Uri.fromFile(target) else uri
        } catch (t: Throwable) {
            uri
        }
    }

    /** True when the stored reference still points at something readable. */
    fun exists(stored: String): Boolean = runCatching {
        val u = Uri.parse(stored)
        if (u.scheme == "file") {
            u.path?.let { File(it).exists() } == true
        } else {
            false
        }
    }.getOrDefault(false)

    fun folderSize(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Delete the app-local copy behind a project. Only touches files inside our
     * own photos/ folder, so a project pointing at an outside URI is left alone.
     */
    fun delete(context: Context, stored: String) {
        runCatching {
            val u = Uri.parse(stored)
            if (u.scheme != "file") return
            val path = u.path ?: return
            val f = File(path)
            val root = dir(context).canonicalPath
            if (f.canonicalPath.startsWith(root) && f.exists()) f.delete()
        }
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
