/**
 * Flow — layer serialisation.
 *
 * Layers are stored as a JSON array on the project. Decoding is defensive: a
 * project saved by an older build has no array at all, and a layer whose content
 * type was removed falls back to empty instead of failing the load.
 */

package com.zyon.flow.edit

import org.json.JSONArray
import org.json.JSONObject

object LayerCodec {

    fun encode(layers: List<PhotoLayer>): String {
        if (layers.isEmpty()) return "[]"
        val arr = JSONArray()
        layers.forEach { l ->
            arr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("content", l.content.name)
                    put("visible", l.visible)
                    put("locked", l.locked)
                    put("opacity", l.opacity.toDouble())

                    put("lutKey", l.lutKey ?: JSONObject.NULL)
                    put("lutName", l.lutName ?: JSONObject.NULL)

                    put(
                        "adj",
                        JSONObject().apply {
                            put("exposure", l.adjustments.exposure.toDouble())
                            put("contrast", l.adjustments.contrast.toDouble())
                            put("saturation", l.adjustments.saturation.toDouble())
                            put("warmth", l.adjustments.warmth.toDouble())
                            put("fade", l.adjustments.fade.toDouble())
                            put("vignette", l.adjustments.vignette.toDouble())
                            put("grain", l.adjustments.grain.toDouble())
                            put("sharpen", l.adjustments.sharpen.toDouble())
                            put("texture", l.adjustments.texture.toDouble())
                            put("clarity", l.adjustments.clarity.toDouble())
                            put("highlights", l.adjustments.highlights.toDouble())
                            put("shadows", l.adjustments.shadows.toDouble())
                            put("whites", l.adjustments.whites.toDouble())
                            put("blacks", l.adjustments.blacks.toDouble())
                            put("dehaze", l.adjustments.dehaze.toDouble())
                            put("denoiseColour", l.adjustments.denoiseColour.toDouble())
                            put("denoiseLuma", l.adjustments.denoiseLuma.toDouble())
                            put("sharpenMask", l.adjustments.sharpenMask.toDouble())
                            put("vibrance", l.adjustments.vibrance.toDouble())
                            put("hueShift", l.adjustments.hueShift.toDouble())
                        },
                    )

                    put("effectId", l.effectId)
                    put("effectIntensity", l.effectIntensity.toDouble())
                    put("effectParam", l.effectParam.toDouble())

                    l.text?.let { t ->
                        put(
                            "text",
                            JSONObject().apply {
                                put("id", t.id)
                                put("text", t.text)
                                put("x", t.x.toDouble())
                                put("y", t.y.toDouble())
                                put("size", t.size.toDouble())
                                put("color", t.color)
                                put("style", t.style.name)
                                put("fontFile", t.fontFile ?: JSONObject.NULL)
                                put("bold", t.bold)
                                put("letterSpacing", t.letterSpacing.toDouble())
                                put("shadow", t.shadow)
                                put("rotation", t.rotation.toDouble())
                                put("visible", t.visible)
                            },
                        )
                    }

                    l.sticker?.let { s ->
                        put(
                            "sticker",
                            JSONObject().apply {
                                put("id", s.id)
                                put("emoji", s.emoji)
                                put("x", s.x.toDouble())
                                put("y", s.y.toDouble())
                                put("size", s.size.toDouble())
                                put("rotation", s.rotation.toDouble())
                                put("visible", s.visible)
                            },
                        )
                    }
                },
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<PhotoLayer> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val content = runCatching {
                    LayerContent.valueOf(o.optString("content", "EMPTY"))
                }.getOrDefault(LayerContent.EMPTY)

                val adj = o.optJSONObject("adj")
                val adjustments = if (adj == null) {
                    Adjustments()
                } else {
                    Adjustments(
                        exposure = adj.optDouble("exposure", 0.0).toFloat(),
                        contrast = adj.optDouble("contrast", 0.0).toFloat(),
                        saturation = adj.optDouble("saturation", 0.0).toFloat(),
                        warmth = adj.optDouble("warmth", 0.0).toFloat(),
                        fade = adj.optDouble("fade", 0.0).toFloat(),
                        vignette = adj.optDouble("vignette", 0.0).toFloat(),
                        grain = adj.optDouble("grain", 0.0).toFloat(),
                        sharpen = adj.optDouble("sharpen", 0.0).toFloat(),
                        texture = adj.optDouble("texture", 0.0).toFloat(),
                        clarity = adj.optDouble("clarity", 0.0).toFloat(),
                        highlights = adj.optDouble("highlights", 0.0).toFloat(),
                        shadows = adj.optDouble("shadows", 0.0).toFloat(),
                        whites = adj.optDouble("whites", 0.0).toFloat(),
                        blacks = adj.optDouble("blacks", 0.0).toFloat(),
                        dehaze = adj.optDouble("dehaze", 0.0).toFloat(),
                        denoiseColour = adj.optDouble("denoiseColour", 0.0).toFloat(),
                        denoiseLuma = adj.optDouble("denoiseLuma", 0.0).toFloat(),
                        sharpenMask = adj.optDouble("sharpenMask", 0.0).toFloat(),
                        vibrance = adj.optDouble("vibrance", 0.0).toFloat(),
                        hueShift = adj.optDouble("hueShift", 0.0).toFloat(),
                    )
                }

                val textJson = o.optJSONObject("text")
                val text = textJson?.let { t ->
                    TextLayer(
                        id = t.optLong("id", 0L),
                        text = t.optString("text", "FLOW"),
                        x = t.optDouble("x", 0.5).toFloat(),
                        y = t.optDouble("y", 0.85).toFloat(),
                        size = t.optDouble("size", 0.08).toFloat(),
                        color = t.optInt("color", 0xFFFFFFFF.toInt()),
                        style = runCatching {
                            TextStyle.valueOf(t.optString("style", "SANS"))
                        }.getOrDefault(TextStyle.SANS),
                        fontFile = t.optString("fontFile", "").ifBlank { null },
                        bold = t.optBoolean("bold", true),
                        letterSpacing = t.optDouble("letterSpacing", 0.15).toFloat(),
                        shadow = t.optBoolean("shadow", true),
                        rotation = t.optDouble("rotation", 0.0).toFloat(),
                        visible = t.optBoolean("visible", true),
                    )
                }

                val stickerJson = o.optJSONObject("sticker")
                val sticker = stickerJson?.let { s ->
                    StickerLayer(
                        id = s.optLong("id", 0L),
                        emoji = s.optString("emoji", "✦"),
                        x = s.optDouble("x", 0.5).toFloat(),
                        y = s.optDouble("y", 0.5).toFloat(),
                        size = s.optDouble("size", 0.14).toFloat(),
                        rotation = s.optDouble("rotation", 0.0).toFloat(),
                        visible = s.optBoolean("visible", true),
                    )
                }

                PhotoLayer(
                    id = o.optLong("id", 0L),
                    content = content,
                    visible = o.optBoolean("visible", true),
                    locked = o.optBoolean("locked", false),
                    opacity = o.optDouble("opacity", 1.0).toFloat(),
                    lutKey = o.optString("lutKey", "").ifBlank { null },
                    lutName = o.optString("lutName", "").ifBlank { null },
                    adjustments = adjustments,
                    effectId = o.optString("effectId", "none"),
                    effectIntensity = o.optDouble("effectIntensity", 1.0).toFloat(),
                    effectParam = o.optDouble("effectParam", 0.5).toFloat(),
                    text = text,
                    sticker = sticker,
                )
            }
        }.getOrDefault(emptyList())
    }
}
