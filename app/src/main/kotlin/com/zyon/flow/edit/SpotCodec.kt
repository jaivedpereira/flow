/** Flow — spot list serialisation. */

package com.zyon.flow.edit

import org.json.JSONArray
import org.json.JSONObject

object SpotCodec {

    fun encode(spots: List<Spot>): String {
        if (spots.isEmpty()) return "[]"
        val arr = JSONArray()
        spots.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("x", s.x.toDouble())
                    put("y", s.y.toDouble())
                    put("r", s.radius.toDouble())
                    put("ox", s.offsetX.toDouble())
                    put("oy", s.offsetY.toDouble())
                },
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<Spot> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Spot(
                    id = o.optLong("id", 0L),
                    x = o.optDouble("x", 0.5).toFloat(),
                    y = o.optDouble("y", 0.5).toFloat(),
                    radius = o.optDouble("r", 0.045).toFloat(),
                    offsetX = o.optDouble("ox", 0.0).toFloat(),
                    offsetY = o.optDouble("oy", -0.10).toFloat(),
                )
            }
        }.getOrDefault(emptyList())
    }
}
