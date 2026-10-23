/**
 * Flow — LUT pack loader.
 *
 * The pack is shipped in assets as a single binary (see tools/build_lutpack.py):
 *   "FLOWLUT1" + u32 count + entries { u16 lens, key/name/group, u32 len, u16 RGB data }
 *
 * Loading is lazy: only the metadata is parsed at startup, and a small LRU keeps
 * the most recently used cubes decoded. 269 cubes would otherwise cost ~16 MB of
 * floats.
 */

package com.zyon.flow.edit

import android.content.Context
import androidx.collection.LruCache

class LutEntry(
    val key: String,
    val name: String,
    val group: String,
    val offset: Long,
    val length: Int,
)

object LutPack {

    private const val MAGIC = "FLOWLUT1"
    private var entriesCache: List<LutEntry>? = null
    private var lutCache: LruCache<String, Lut>? = null

    /** Metadata only — cheap, called on startup. */
    fun entries(context: Context): List<LutEntry> {
        entriesCache?.let { return it }
        val list = ArrayList<LutEntry>(300)
        context.assets.open("luts.bin").use { input ->
            val magic = ByteArray(8)
            if (input.read(magic) != 8) return emptyList()
            if (String(magic, Charsets.US_ASCII) != MAGIC) return emptyList()

            val count = readU32(input)
            var pos = 8L + 4
            repeat(count) {
                val keyLen = readU16(input)
                val nameLen = readU16(input)
                val groupLen = readU16(input)
                val key = readString(input, keyLen)
                val name = readString(input, nameLen)
                val group = readString(input, groupLen)
                val dataLen = readU32(input)
                val headerSize = 6 + keyLen + nameLen + groupLen + 4
                list.add(LutEntry(key, name, group, pos + headerSize, dataLen))
                input.skip(dataLen.toLong())
                pos += headerSize + dataLen
            }
        }
        entriesCache = list
        return list
    }

    /** Decode one cube on demand, cached. */
    fun load(context: Context, key: String): Lut? {
        val cache = lutCache ?: LruCache<String, Lut>(6).also { lutCache = it }
        cache.get(key)?.let { return it }

        val entry = entries(context).firstOrNull { it.key == key } ?: return null
        val bytes = ByteArray(entry.length)
        context.assets.open("luts.bin").use { input ->
            input.skip(entry.offset)
            var read = 0
            while (read < entry.length) {
                val n = input.read(bytes, read, entry.length - read)
                if (n <= 0) break
                read += n
            }
            if (read < entry.length) return null
        }

        // u16 LE interleaved RGB -> floats in 0..1 (layout matches Lut.sample)
        val n = entry.length / 2
        val floats = FloatArray(n)
        for (i in 0 until n) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() and 0xFF
            floats[i] = ((hi shl 8) or lo) / 65535f
        }
        val lut = Lut(17, floats)
        cache.put(key, lut)
        return lut
    }

    /** Representative colour for a swatch, taken from the mid-grey entry. */
    fun swatch(context: Context, key: String): Int {
        val lut = load(context, key) ?: return 0xFF808080.toInt()
        val n = 17
        val mid = ((n / 2) * n + (n / 2)) * n + (n / 2)
        val idx = mid * 3
        if (idx + 2 >= lut.data.size) return 0xFF808080.toInt()
        val r = (lut.data[idx].coerceIn(0f, 1f) * 255).toInt()
        val g = (lut.data[idx + 1].coerceIn(0f, 1f) * 255).toInt()
        val b = (lut.data[idx + 2].coerceIn(0f, 1f) * 255).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun readU16(input: java.io.InputStream): Int {
        val a = input.read()
        val b = input.read()
        if (a < 0 || b < 0) return 0
        return (a and 0xFF) or ((b and 0xFF) shl 8)
    }

    private fun readU32(input: java.io.InputStream): Int {
        val a = readU16(input)
        val b = readU16(input)
        return a or (b shl 16)
    }

    private fun readString(input: java.io.InputStream, len: Int): String {
        if (len <= 0) return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n <= 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }
}
