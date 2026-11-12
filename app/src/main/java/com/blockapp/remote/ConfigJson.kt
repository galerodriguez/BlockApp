package com.blockapp.remote

import com.blockapp.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

/** Convierte los ajustes de bloqueo a JSON y viceversa (para enviarlos entre móviles). */
object ConfigJson {
    fun current(): JSONObject = JSONObject().apply {
        put("net", JSONArray(Prefs.blockedNet.sorted()))
        put("lock", JSONArray(Prefs.lockedApps.sorted()))
        val lim = JSONObject()
        for ((pkg, minutes) in Prefs.limits) lim.put(pkg, minutes)
        put("limits", lim)
        put("schedOn", Prefs.scheduleEnabled)
        put("schedStart", Prefs.scheduleStart)
        put("schedEnd", Prefs.scheduleEnd)
        put("schedDays", JSONArray(Prefs.scheduleDays.sorted()))
        put("grace", Prefs.graceSeconds)
    }

    fun apply(o: JSONObject) {
        if (o.has("net")) Prefs.blockedNet = strings(o.optJSONArray("net"))
        if (o.has("lock")) Prefs.lockedApps = strings(o.optJSONArray("lock"))
        o.optJSONObject("limits")?.let { Prefs.limits = intMap(it) }
        if (o.has("schedOn")) Prefs.scheduleEnabled = o.optBoolean("schedOn")
        if (o.has("schedStart")) Prefs.scheduleStart = o.optInt("schedStart")
        if (o.has("schedEnd")) Prefs.scheduleEnd = o.optInt("schedEnd")
        if (o.has("schedDays")) Prefs.scheduleDays = ints(o.optJSONArray("schedDays"))
        if (o.has("grace")) Prefs.graceSeconds = o.optInt("grace")
    }

    fun strings(a: JSONArray?): Set<String> {
        if (a == null) return emptySet()
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
    }

    fun ints(a: JSONArray?): Set<Int> {
        if (a == null) return emptySet()
        return (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.toSet()
    }

    fun intMap(o: JSONObject): Map<String, Int> {
        val m = HashMap<String, Int>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = o.optInt(k, -1)
            if (v > 0) m[k] = v
        }
        return m
    }

    fun longMap(o: JSONObject?): Map<String, Long> {
        if (o == null) return emptyMap()
        val m = HashMap<String, Long>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = o.optLong(k)
        }
        return m
    }
}
