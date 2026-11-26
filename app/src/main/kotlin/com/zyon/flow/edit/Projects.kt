/**
 * Flow — project serialisation v2.
 *
 * Everything the editor can change is persisted: cubes, effects, curves, HSL,
 * border, geometry, and the brush mask (stored as a PNG beside the photo).
 * Masks are 192x144, so a project costs ~30 KB of mask, not megabytes.
 */

package com.zyon.flow.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Project(
    val id: Long,
    val photoUri: String,
    val lutKey: String?,
    val lutIntensity: Float,
    val effectId: String,
    val effectIntensity: Float,
    val effectParam: Float,
    val presetId: String,
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val fade: Float,
    val vignette: Float,
    val grain: Float,
    val sharpen: Float,
    val texture: Float = 0f,
    val clarity: Float,
    val highlights: Float,
    val shadows: Float,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val dehaze: Float = 0f,
    val denoiseColour: Float = 0f,
    val denoiseLuma: Float = 0f,
    val sharpenMask: Float = 0f,
    val perspectiveV: Float = 0f,
    val perspectiveH: Float = 0f,
    val spotsJson: String = "[]",
    val vibrance: Float,
    val hueShift: Float,
    val rotation: Int,
    val flipH: Boolean,
    val straighten: Float,
    val aspect: Float?,
    val curvesJson: String,
    val hslJson: String,
    val borderWidth: Float,
    val borderColor: Int,
    val borderRounded: Float,
    val useMask: Boolean,
    /** User layer stack, encoded as JSON. Empty for projects made before layers. */
    val layersJson: String = "[]",
    /** Starred by the user, shown under the Favourites filter. */
    val favorite: Boolean = false,
    val updatedAt: Long,
) {
    fun toState(): EditState = EditState(
        presetId = presetId,
        lutKey = lutKey,
        lutIntensity = lutIntensity,
        effectId = effectId,
        effectIntensity = effectIntensity,
        effectParam = effectParam,
        adjustments = Adjustments(
            exposure = exposure,
            contrast = contrast,
            saturation = saturation,
            warmth = warmth,
            fade = fade,
            vignette = vignette,
            grain = grain,
            sharpen = sharpen,
            texture = texture,
            clarity = clarity,
            highlights = highlights,
            shadows = shadows,
            whites = whites,
            blacks = blacks,
            dehaze = dehaze,
            denoiseColour = denoiseColour,
            denoiseLuma = denoiseLuma,
            sharpenMask = sharpenMask,
            vibrance = vibrance,
            hueShift = hueShift,
        ),
        transform = Transform(
            rotation = rotation,
            flipH = flipH,
            straighten = straighten,
            aspect = aspect,
            perspectiveV = perspectiveV,
            perspectiveH = perspectiveH,
        ),
        spots = com.zyon.flow.edit.SpotCodec.decode(spotsJson),
        curves = CurveCodec.decodeCurves(curvesJson),
        hsl = CurveCodec.decodeHsl(hslJson),
        border = Border(width = borderWidth, color = borderColor, rounded = borderRounded),
        useMask = useMask,
        layers = LayerCodec.decode(layersJson),
    )

    fun uri(): Uri = Uri.parse(photoUri)

    companion object {
        /**
         * Re-saving an edited project must not clear its star. The editor builds a
         * fresh Project and knows nothing about the library flags, so the star is
         * carried through here.
         */
        private val favoriteCache = HashMap<Long, Boolean>()

        private fun favoriteOfId(id: Long): Boolean = favoriteCache[id] ?: false

        fun rememberFavorites(projects: List<Project>) {
            favoriteCache.clear()
            projects.forEach { favoriteCache[it.id] = it.favorite }
        }

        fun from(uri: Uri, state: EditState, id: Long = System.currentTimeMillis()): Project = Project(
            id = id,
            photoUri = uri.toString(),
            lutKey = state.lutKey,
            lutIntensity = state.lutIntensity,
            effectId = state.effectId,
            effectIntensity = state.effectIntensity,
            effectParam = state.effectParam,
            presetId = state.presetId,
            exposure = state.adjustments.exposure,
            contrast = state.adjustments.contrast,
            saturation = state.adjustments.saturation,
            warmth = state.adjustments.warmth,
            fade = state.adjustments.fade,
            vignette = state.adjustments.vignette,
            grain = state.adjustments.grain,
            sharpen = state.adjustments.sharpen,
            texture = state.adjustments.texture,
            clarity = state.adjustments.clarity,
            highlights = state.adjustments.highlights,
            shadows = state.adjustments.shadows,
            whites = state.adjustments.whites,
            blacks = state.adjustments.blacks,
            dehaze = state.adjustments.dehaze,
            denoiseColour = state.adjustments.denoiseColour,
            denoiseLuma = state.adjustments.denoiseLuma,
            sharpenMask = state.adjustments.sharpenMask,
            perspectiveV = state.transform.perspectiveV,
            perspectiveH = state.transform.perspectiveH,
            spotsJson = com.zyon.flow.edit.SpotCodec.encode(state.spots),
            vibrance = state.adjustments.vibrance,
            hueShift = state.adjustments.hueShift,
            rotation = state.transform.rotation,
            flipH = state.transform.flipH,
            straighten = state.transform.straighten,
            aspect = state.transform.aspect,
            curvesJson = CurveCodec.encodeCurves(state.curves),
            hslJson = CurveCodec.encodeHsl(state.hsl),
            borderWidth = state.border.width,
            borderColor = state.border.color,
            borderRounded = state.border.rounded,
            useMask = state.useMask,
            layersJson = LayerCodec.encode(state.layers),
            favorite = favoriteOfId(id),
            updatedAt = System.currentTimeMillis(),
        )
    }
}

/** JSON codecs for the structured pieces, kept out of the data class. */
object CurveCodec {

    fun encodeCurves(c: Curves): String = JSONObject().apply {
        put("m", curve(c.master))
        put("r", curve(c.red))
        put("g", curve(c.green))
        put("b", curve(c.blue))
    }.toString()

    private fun curve(c: Curve): JSONArray = JSONArray().apply {
        c.points.forEach { p -> put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
    }

    fun decodeCurves(json: String?): Curves {
        if (json.isNullOrBlank()) return Curves()
        return runCatching {
            val o = JSONObject(json)
            Curves(
                master = curveOf(o.optJSONArray("m")),
                red = curveOf(o.optJSONArray("r")),
                green = curveOf(o.optJSONArray("g")),
                blue = curveOf(o.optJSONArray("b")),
            )
        }.getOrDefault(Curves())
    }

    private fun curveOf(arr: JSONArray?): Curve {
        if (arr == null || arr.length() < 2) return Curve()
        val pts = (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONArray(i) ?: return@mapNotNull null
            CurvePoint(p.optDouble(0, 0.0).toFloat(), p.optDouble(1, 0.0).toFloat())
        }
        return if (pts.size >= 2) Curve(pts) else Curve()
    }

    fun encodeHsl(h: Hsl): String = JSONObject().apply {
        h.shifts.forEach { (band, s) ->
            if (!s.isNeutral) {
                put(
                    band.name,
                    JSONObject().apply {
                        put("h", s.hue.toDouble())
                        put("s", s.saturation.toDouble())
                        put("l", s.luminance.toDouble())
                    },
                )
            }
        }
    }.toString()

    fun decodeHsl(json: String?): Hsl {
        if (json.isNullOrBlank() || json == "{}") return Hsl()
        return runCatching {
            val o = JSONObject(json)
            val map = mutableMapOf<HslBand, HslShift>()
            HslBand.entries.forEach { band ->
                o.optJSONObject(band.name)?.let { s ->
                    map[band] = HslShift(
                        hue = s.optDouble("h", 0.0).toFloat(),
                        saturation = s.optDouble("s", 0.0).toFloat(),
                        luminance = s.optDouble("l", 0.0).toFloat(),
                    )
                }
            }
            Hsl(map)
        }.getOrDefault(Hsl())
    }
}

object ProjectStore {
    private const val PREFS = "flow_projects"
    private const val KEY = "projects"

    fun load(context: Context): List<Project> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> project(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    private fun project(o: JSONObject): Project = Project(
        id = o.getLong("id"),
        photoUri = o.getString("photoUri"),
        lutKey = if (o.isNull("lutKey")) null else o.optString("lutKey", null),
        lutIntensity = o.optDouble("lutIntensity", 1.0).toFloat(),
        effectId = o.optString("effectId", "none"),
        effectIntensity = o.optDouble("effectIntensity", 1.0).toFloat(),
        effectParam = o.optDouble("effectParam", 0.5).toFloat(),
        presetId = o.optString("presetId", "original"),
        exposure = o.optDouble("exposure", 0.0).toFloat(),
        contrast = o.optDouble("contrast", 0.0).toFloat(),
        saturation = o.optDouble("saturation", 0.0).toFloat(),
        warmth = o.optDouble("warmth", 0.0).toFloat(),
        fade = o.optDouble("fade", 0.0).toFloat(),
        vignette = o.optDouble("vignette", 0.0).toFloat(),
        grain = o.optDouble("grain", 0.0).toFloat(),
        sharpen = o.optDouble("sharpen", 0.0).toFloat(),
            texture = o.optDouble("texture", 0.0).toFloat(),
        clarity = o.optDouble("clarity", 0.0).toFloat(),
        highlights = o.optDouble("highlights", 0.0).toFloat(),
        shadows = o.optDouble("shadows", 0.0).toFloat(),
        whites = o.optDouble("whites", 0.0).toFloat(),
        blacks = o.optDouble("blacks", 0.0).toFloat(),
        dehaze = o.optDouble("dehaze", 0.0).toFloat(),
        denoiseColour = o.optDouble("denoiseColour", 0.0).toFloat(),
        denoiseLuma = o.optDouble("denoiseLuma", 0.0).toFloat(),
        sharpenMask = o.optDouble("sharpenMask", 0.0).toFloat(),
        perspectiveV = o.optDouble("perspectiveV", 0.0).toFloat(),
        perspectiveH = o.optDouble("perspectiveH", 0.0).toFloat(),
        spotsJson = o.optString("spotsJson", "[]"),
        vibrance = o.optDouble("vibrance", 0.0).toFloat(),
        hueShift = o.optDouble("hueShift", 0.0).toFloat(),
        rotation = o.optInt("rotation", 0),
        flipH = o.optBoolean("flipH", false),
        straighten = o.optDouble("straighten", 0.0).toFloat(),
        aspect = if (o.isNull("aspect")) null else o.optDouble("aspect", 1.0).toFloat(),
        curvesJson = o.optString("curves", ""),
        hslJson = o.optString("hsl", ""),
        borderWidth = o.optDouble("borderWidth", 0.0).toFloat(),
        borderColor = o.optInt("borderColor", 0xFFFFFFFF.toInt()),
        borderRounded = o.optDouble("borderRounded", 0.0).toFloat(),
        useMask = o.optBoolean("useMask", false),
        layersJson = o.optString("layersJson", "[]"),
        favorite = o.optBoolean("favorite", false),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    fun save(context: Context, projects: List<Project>) {
        val arr = JSONArray()
        projects.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("photoUri", p.photoUri)
                    put("lutKey", p.lutKey ?: JSONObject.NULL)
                    put("lutIntensity", p.lutIntensity.toDouble())
                    put("effectId", p.effectId)
                    put("effectIntensity", p.effectIntensity.toDouble())
                    put("effectParam", p.effectParam.toDouble())
                    put("presetId", p.presetId)
                    put("exposure", p.exposure.toDouble())
                    put("contrast", p.contrast.toDouble())
                    put("saturation", p.saturation.toDouble())
                    put("warmth", p.warmth.toDouble())
                    put("fade", p.fade.toDouble())
                    put("vignette", p.vignette.toDouble())
                    put("grain", p.grain.toDouble())
                    put("sharpen", p.sharpen.toDouble())
                    put("texture", p.texture.toDouble())
                    put("clarity", p.clarity.toDouble())
                    put("highlights", p.highlights.toDouble())
                    put("shadows", p.shadows.toDouble())
                    put("whites", p.whites.toDouble())
                    put("blacks", p.blacks.toDouble())
                    put("dehaze", p.dehaze.toDouble())
                    put("denoiseColour", p.denoiseColour.toDouble())
                    put("denoiseLuma", p.denoiseLuma.toDouble())
                    put("sharpenMask", p.sharpenMask.toDouble())
                    put("perspectiveV", p.perspectiveV.toDouble())
                    put("perspectiveH", p.perspectiveH.toDouble())
                    put("spotsJson", p.spotsJson)
                    put("vibrance", p.vibrance.toDouble())
                    put("hueShift", p.hueShift.toDouble())
                    put("rotation", p.rotation)
                    put("flipH", p.flipH)
                    put("straighten", p.straighten.toDouble())
                    put("aspect", p.aspect?.toDouble() ?: JSONObject.NULL)
                    put("curves", p.curvesJson)
                    put("hsl", p.hslJson)
                    put("borderWidth", p.borderWidth.toDouble())
                    put("borderColor", p.borderColor)
                    put("borderRounded", p.borderRounded.toDouble())
                    put("useMask", p.useMask)
                    put("layersJson", p.layersJson)
                    put("favorite", p.favorite)
                    put("updatedAt", p.updatedAt)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(context: Context, project: Project): List<Project> {
        val current = load(context)
        val next = (current.filterNot { it.photoUri == project.photoUri } + project)
            .sortedByDescending { it.updatedAt }
        save(context, next)
        return next
    }

    /** Star or unstar a photo without touching anything else. */
    fun toggleFavorite(context: Context, id: Long): List<Project> {
        val next = load(context).map { if (it.id == id) it.copy(favorite = !it.favorite) else it }
        save(context, next)
        Project.rememberFavorites(next)
        return next
    }

    fun remove(context: Context, id: Long): List<Project> {
        MaskStore.delete(context, id)
        val next = load(context).filterNot { it.id == id }
        save(context, next)
        return next
    }
}

/** Brush masks live beside the photos, one PNG per project id. */
object MaskStore {
    private fun dir(context: Context): File =
        File(context.filesDir, "masks").apply { mkdirs() }

    private fun file(context: Context, id: Long): File = File(dir(context), "$id.png")

    fun save(context: Context, id: Long, mask: MaskData) {
        if (!mask.hasContent()) {
            delete(context, id)
            return
        }
        runCatching {
            val w = mask.width
            val h = mask.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val px = IntArray(w * h)
            for (i in px.indices) {
                val v = (mask.values[i].coerceIn(0f, 1f) * 255f).toInt()
                px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            file(context, id).outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
    }

    fun load(context: Context, id: Long): MaskData? {
        val f = file(context, id)
        if (!f.exists()) return null
        return runCatching {
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            val mask = MaskData(bmp.width, bmp.height)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            var any = false
            for (i in px.indices) {
                val v = ((px[i] shr 16) and 0xFF) / 255f
                mask.values[i] = v
                if (v > 0.002f) any = true
            }
            mask.setHasContent(any)
            bmp.recycle()
            mask
        }.getOrNull()
    }

    fun delete(context: Context, id: Long) {
        runCatching { file(context, id).delete() }
    }
}
