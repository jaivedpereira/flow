/**
 * Flow — user font library.
 *
 * Users copy their own .ttf/.otf files into the app and pick them for captions.
 * Files live in files/fonts; the layer stores only the file name so a project
 * survives the bundle being reinstalled.
 */

package com.zyon.flow.edit

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

data class FontEntry(
    val name: String,
    /** File name inside files/fonts, or null for the built-in families. */
    val file: String? = null,
    val builtIn: Boolean = false,
)

object FontStore {

    private const val DIR = "fonts"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Copy a picked font into app storage and return its file name. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val display = uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
        val ext = display.substringAfterLast('.', "ttf").lowercase()
        require(ext in setOf("ttf", "otf", "ttc")) { "unsupported font type" }
        val safeName = display.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir(context), safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        // Reject anything the framework cannot parse, so the UI never shows a dead entry.
        Typeface.createFromFile(target)
        target.name
    }.getOrNull()

    fun list(context: Context): List<FontEntry> {
        val custom = dir(context).listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedBy { it.name.lowercase() }
            ?.map { FontEntry(it.name.substringBeforeLast('.'), it.name) }
            ?: emptyList()
        val builtIn = listOf(
            FontEntry("Serif", null, true),
            FontEntry("Sans", null, true),
            FontEntry("Mono", null, true),
        )
        return builtIn + custom
    }

    /** Resolve a stored font name to a Typeface, falling back to Sans. */
    fun typeface(context: Context, fileName: String?, bold: Boolean): Typeface {
        val weight = if (bold) Typeface.BOLD else Typeface.NORMAL
        if (fileName == null) return Typeface.create(Typeface.SANS_SERIF, weight)
        return runCatching {
            val f = File(dir(context), fileName)
            if (!f.exists()) return Typeface.create(Typeface.SANS_SERIF, weight)
            // Custom files carry their own weight; createFromFile ignores the style arg.
            Typeface.createFromFile(f)
        }.getOrDefault(Typeface.create(Typeface.SANS_SERIF, weight))
    }

    fun delete(context: Context, fileName: String): Boolean =
        runCatching { File(dir(context), fileName).delete() }.getOrDefault(false)

    fun size(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L
}
