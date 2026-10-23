/**
 * Flow — saved recipes: a named preset + adjustments bundle.
 * Stored locally as JSON in SharedPreferences; never leaves the device.
 */

package com.zyon.flow.edit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Recipe(
    val name: String,
    val presetId: String,
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val fade: Float,
    val vignette: Float,
    val grain: Float,
    val sharpen: Float,
    val clarity: Float,
    val splitShadow: Float,
    val splitHighlight: Float,
) {
    fun toState(): EditState = EditState(
        presetId = presetId,
        adjustments = Adjustments(
            exposure = exposure,
            contrast = contrast,
            saturation = saturation,
            warmth = warmth,
            fade = fade,
            vignette = vignette,
            grain = grain,
            sharpen = sharpen,
            clarity = clarity,
        ),
        splitShadow = splitShadow,
        splitHighlight = splitHighlight,
    )

    companion object {
        fun from(name: String, state: EditState): Recipe = Recipe(
            name = name,
            presetId = state.presetId,
            exposure = state.adjustments.exposure,
            contrast = state.adjustments.contrast,
            saturation = state.adjustments.saturation,
            warmth = state.adjustments.warmth,
            fade = state.adjustments.fade,
            vignette = state.adjustments.vignette,
            grain = state.adjustments.grain,
            sharpen = state.adjustments.sharpen,
            clarity = state.adjustments.clarity,
            splitShadow = state.splitShadow,
            splitHighlight = state.splitHighlight,
        )
    }
}

object RecipeStore {
    private const val PREFS = "flow_recipes"
    private const val KEY = "recipes"

    fun load(context: Context): List<Recipe> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Recipe(
                    name = o.getString("name"),
                    presetId = o.getString("presetId"),
                    exposure = o.getDouble("exposure").toFloat(),
                    contrast = o.getDouble("contrast").toFloat(),
                    saturation = o.getDouble("saturation").toFloat(),
                    warmth = o.getDouble("warmth").toFloat(),
                    fade = o.getDouble("fade").toFloat(),
                    vignette = o.getDouble("vignette").toFloat(),
                    grain = o.getDouble("grain").toFloat(),
                    sharpen = o.getDouble("sharpen").toFloat(),
                    clarity = o.getDouble("clarity").toFloat(),
                    splitShadow = o.getDouble("splitShadow").toFloat(),
                    splitHighlight = o.getDouble("splitHighlight").toFloat(),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, recipes: List<Recipe>) {
        val arr = JSONArray()
        recipes.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("name", r.name)
                    put("presetId", r.presetId)
                    put("exposure", r.exposure.toDouble())
                    put("contrast", r.contrast.toDouble())
                    put("saturation", r.saturation.toDouble())
                    put("warmth", r.warmth.toDouble())
                    put("fade", r.fade.toDouble())
                    put("vignette", r.vignette.toDouble())
                    put("grain", r.grain.toDouble())
                    put("sharpen", r.sharpen.toDouble())
                    put("clarity", r.clarity.toDouble())
                    put("splitShadow", r.splitShadow.toDouble())
                    put("splitHighlight", r.splitHighlight.toDouble())
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
