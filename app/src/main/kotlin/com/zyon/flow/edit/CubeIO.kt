/**
 * Flow — .cube LUT import / export.
 *
 * Reading a .cube gives access to every LUT published on the web (free packs,
 * Resolve exports, Lightroom presets converted to cube). Writing one lets the
 * user take their Flow look into any other tool — no lock-in.
 */

package com.zyon.flow.edit

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStreamWriter

object CubeIO {

    data class ParsedCube(
        val size: Int,
        val data: FloatArray,
        val title: String?,
        val domainMin: FloatArray,
        val domainMax: FloatArray,
    )

    /**
     * Parse a .cube file. Supports LUT_3D_SIZE, TITLE, DOMAIN_MIN/MAX and the
     * raw "r g b" rows. 1D LUTs are rejected with a clear reason.
     */
    fun parse(stream: InputStream): Result<ParsedCube> = runCatching {
        var size = 0
        var title: String? = null
        val domainMin = floatArrayOf(0f, 0f, 0f)
        val domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>(64 * 64 * 64 * 3)

        stream.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val upper = line.uppercase()
                when {
                    upper.startsWith("LUT_3D_SIZE") -> {
                        size = line.split(Regex("\\s+"))[1].toInt()
                    }
                    upper.startsWith("LUT_1D_SIZE") -> {
                        error("1D LUTs are not supported — export a 3D cube instead")
                    }
                    upper.startsWith("TITLE") -> {
                        title = line.substringAfter(" ", "").trim().removeSurrounding("\"")
                    }
                    upper.startsWith("DOMAIN_MIN") -> {
                        val p = line.split(Regex("\\s+"))
                        for (i in 0..2) domainMin[i] = p[i + 1].toFloat()
                    }
                    upper.startsWith("DOMAIN_MAX") -> {
                        val p = line.split(Regex("\\s+"))
                        for (i in 0..2) domainMax[i] = p[i + 1].toFloat()
                    }
                    else -> {
                        val p = line.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                        if (p.size >= 3) {
                            values.add(p[0].toFloatOrNull() ?: return@forEach)
                            values.add(p[1].toFloatOrNull() ?: return@forEach)
                            values.add(p[2].toFloatOrNull() ?: return@forEach)
                        }
                    }
                }
            }
        }

        require(size >= 2) { "missing or invalid LUT_3D_SIZE" }
        val expected = size * size * size * 3
        require(values.size == expected) {
            "expected $expected floats for size $size, got ${values.size}"
        }

        // Rescale from the declared domain into 0..1 so Lut can index it directly.
        val arr = FloatArray(expected)
        for (i in 0 until expected) {
            val ch = i % 3
            val lo = domainMin[ch]
            val hi = domainMax[ch]
            val span = (hi - lo).let { if (kotlin.math.abs(it) < 1e-6f) 1f else it }
            arr[i] = ((values[i] - lo) / span).coerceIn(0f, 1f)
        }

        ParsedCube(size, arr, title, domainMin, domainMax)
    }

    /** Write [lut] as a .cube at 6 decimals — accurate enough, still readable. */
    fun export(lut: Lut, output: OutputStreamWriter, title: String) {
        output.use { out ->
            out.write("# Exported by Flow\n")
            out.write("TITLE \"$title\"\n")
            out.write("LUT_3D_SIZE ${lut.size}\n")
            val n = lut.size * lut.size * lut.size
            for (i in 0 until n) {
                val r = lut.data[i * 3]
                val g = lut.data[i * 3 + 1]
                val b = lut.data[i * 3 + 2]
                // Locale.US is mandatory: a pt-BR phone would write "0,123456",
                // which no other tool can parse.
                out.write(String.format(java.util.Locale.US, "%.6f %.6f %.6f\n", r, g, b))
            }
        }
    }

    /** Read an arbitrary .cube the user picked and hand back a ready [Lut]. */
    fun importFrom(context: Context, uri: Uri): Result<Lut> = runCatching {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("cannot open the selected file")
        val parsed = parse(stream).getOrThrow()
        Lut(parsed.size, parsed.data)
    }

    /** A small file name for a LUT, used in the UI. */
    fun displayName(uri: Uri): String {
        val last = uri.lastPathSegment ?: "imported.cube"
        return last.substringBeforeLast('.').ifBlank { "Imported" }
    }
}
