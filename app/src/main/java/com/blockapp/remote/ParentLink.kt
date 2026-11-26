package com.blockapp.remote

import com.blockapp.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lado del PADRE: guarda la vinculación con el móvil del hijo,
 * descarga su estado y le envía órdenes. Funciones bloqueantes (usar en Dispatchers.IO).
 */
object ParentLink {
    fun key(): ByteArray? = RemoteCrypto.keyFromString(Prefs.parentKey)

    fun isLinked(): Boolean = key() != null

    /** Devuelve false si el código no es válido. */
    fun link(code: String, childName: String): Boolean {
        val key = RemoteCrypto.decodeCode(code) ?: return false
        Prefs.parentKey = RemoteCrypto.keyToString(key)
        Prefs.parentChildName = childName.ifBlank { "Hijo" }
        Prefs.parentStatus = null
        Prefs.parentEventsSince = (System.currentTimeMillis() / 1000).toString()
        Prefs.parentPending = "[]"
        Prefs.parentNewApps = "[]"
        return true
    }

    fun unlink() {
        Prefs.parentKey = null
        Prefs.parentStatus = null
    }

    /** Último estado guardado en este móvil (puede ser antiguo). */
    fun cachedStatus(): JSONObject? = try {
        Prefs.parentStatus?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }

    /** Descarga el estado más reciente que haya publicado el hijo (últimas 12 h). */
    fun fetchStatus(): JSONObject? {
        val key = key() ?: return null
        val msgs = Ntfy.poll(RemoteCrypto.topic(key, "s"), "12h")
        var best: JSONObject? = cachedStatus()
        for (m in msgs.asReversed()) {
            val text = Ntfy.body(m)?.let { RemoteCrypto.open(key, it) } ?: continue
            val o = try { JSONObject(text) } catch (_: Exception) { continue }
            if (o.optLong("ts") > (best?.optLong("ts") ?: 0L)) {
                best = o
                Prefs.parentStatus = text
            }
            break
        }
        return best
    }

    fun requestStatus(): Boolean = send("status") {}

    fun sendConfig(cfg: JSONObject): Boolean = send("config") { put("cfg", cfg) }

    fun sendBonus(pkg: String, minutes: Long): Boolean = send("bonus") {
        put("pkg", pkg)
        put("min", minutes)
    }

    fun sendPause(minutes: Int): Boolean = send("pause") { put("min", minutes) }

    fun sendTimeDenied(pkg: String): Boolean = send("time_denied") { put("pkg", pkg) }

    /**
     * Descarga los avisos nuevos del hijo (solicitudes de tiempo, apps nuevas),
     * los guarda y los devuelve para notificar. Bloqueante.
     */
    @Synchronized
    fun fetchEvents(): List<JSONObject> {
        val key = key() ?: return emptyList()
        val since = Prefs.parentEventsSince ?: "12h"
        val out = ArrayList<JSONObject>()
        for (m in Ntfy.poll(RemoteCrypto.topic(key, "e"), since)) {
            if (m.id.isNotBlank()) Prefs.parentEventsSince = m.id
            val text = Ntfy.body(m)?.let { RemoteCrypto.open(key, it) } ?: continue
            val o = try { JSONObject(text) } catch (_: Exception) { continue }
            out += o
            when (o.optString("type")) {
                "time_request" -> {
                    val pkg = o.optString("pkg")
                    val list = pending().filter { it.optString("pkg") != pkg } + o
                    savePending(list)
                }
                "new_app" -> {
                    val list = (listOf(o) + newApps().filter { it.optString("pkg") != o.optString("pkg") }).take(20)
                    Prefs.parentNewApps = JSONArray(list).toString()
                }
            }
        }
        return out
    }

    fun pending(): List<JSONObject> = parseList(Prefs.parentPending)

    fun removePending(pkg: String) = savePending(pending().filter { it.optString("pkg") != pkg })

    private fun savePending(list: List<JSONObject>) {
        Prefs.parentPending = JSONArray(list).toString()
    }

    fun newApps(): List<JSONObject> = parseList(Prefs.parentNewApps)

    fun dismissNewApp(pkg: String) {
        Prefs.parentNewApps = JSONArray(newApps().filter { it.optString("pkg") != pkg }).toString()
    }

    private fun parseList(json: String): List<JSONObject> = try {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun send(type: String, fill: JSONObject.() -> Unit): Boolean {
        val key = key() ?: return false
        val o = JSONObject().put("type", type).put("ts", System.currentTimeMillis())
        o.fill()
        return Ntfy.publish(RemoteCrypto.topic(key, "c"), RemoteCrypto.seal(key, o.toString()))
    }
}
