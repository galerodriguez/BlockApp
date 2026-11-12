package com.blockapp.remote

import com.blockapp.data.Prefs
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

    private fun send(type: String, fill: JSONObject.() -> Unit): Boolean {
        val key = key() ?: return false
        val o = JSONObject().put("type", type).put("ts", System.currentTimeMillis())
        o.fill()
        return Ntfy.publish(RemoteCrypto.topic(key, "c"), RemoteCrypto.seal(key, o.toString()))
    }
}
