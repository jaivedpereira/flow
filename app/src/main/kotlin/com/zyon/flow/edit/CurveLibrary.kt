/**
 * Flow — curve preset library.
 *
 * Saved curve sets are the tonal equivalent of a LUT: cheap to store, instant to
 * apply, and portable between photos. Stored as JSON in SharedPreferences.
 */

package com.zyon.flow.edit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CurvePreset(
    val name: String,
    val curvesJson: String,
) {
    fun toCurves(): Curves = CurveCodec.decodeCurves(curvesJson)
}

object CurveLibrary {

    private const val PREFS = "flow_curves"
    private const val KEY = "presets"

    /** The built-ins are code, the user's are stored — one list for the UI. */
    val BuiltIn: List<CurvePreset> = listOf(
        CurvePreset(
            "Neutral",
            CurveCodec.encodeCurves(Curves()),
        ),
        CurvePreset(
            "Film S",
            CurveCodec.encodeCurves(
                Curves(
                    master = Curve(
                        listOf(
                            CurvePoint(0f, 0f),
                            CurvePoint(0.25f, 0.19f),
                            CurvePoint(0.75f, 0.82f),
                            CurvePoint(1f, 1f),
                        ),
                    ),
                ),
            ),
        ),
        CurvePreset(
            "Lifted Black",
            CurveCodec.encodeCurves(
                Curves(master = Curve(listOf(CurvePoint(0f, 0.10f), CurvePoint(1f, 0.98f)))),
            ),
        ),
        CurvePreset(
            "Faded White",
            CurveCodec.encodeCurves(
                Curves(master = Curve(listOf(CurvePoint(0f, 0.04f), CurvePoint(1f, 0.93f)))),
            ),
        ),
        CurvePreset(
            "Punch",
            CurveCodec.encodeCurves(
                Curves(
                    master = Curve(
                        listOf(
                            CurvePoint(0f, 0f),
                            CurvePoint(0.3f, 0.24f),
                            CurvePoint(0.7f, 0.78f),
                            CurvePoint(1f, 1f),
                        ),
                    ),
                ),
            ),
        ),
        CurvePreset(
            "Cool Shadows",
            CurveCodec.encodeCurves(
                Curves(
                    blue = Curve(listOf(CurvePoint(0f, 0.10f), CurvePoint(1f, 1f))),
                    red = Curve(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0.96f))),
                ),
            ),
        ),
        CurvePreset(
            "Warm Highlights",
            CurveCodec.encodeCurves(
                Curves(
                    red = Curve(listOf(CurvePoint(0f, 0.02f), CurvePoint(1f, 1f))),
                    blue = Curve(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0.94f))),
                ),
            ),
        ),
        CurvePreset(
            "Matte",
            CurveCodec.encodeCurves(
                Curves(
                    master = Curve(
                        listOf(
                            CurvePoint(0f, 0.13f),
                            CurvePoint(0.5f, 0.52f),
                            CurvePoint(1f, 0.90f),
                        ),
                    ),
                ),
            ),
        ),
    )

    fun load(context: Context): List<CurvePreset> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CurvePreset(o.getString("name"), o.getString("curves"))
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, presets: List<CurvePreset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("curves", p.curvesJson)
                },
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun add(context: Context, name: String, curves: Curves): List<CurvePreset> {
        val next = load(context) + CurvePreset(name, CurveCodec.encodeCurves(curves))
        save(context, next)
        return next
    }

    fun remove(context: Context, name: String): List<CurvePreset> {
        val next = load(context).filterNot { it.name == name }
        save(context, next)
        return next
    }
}
