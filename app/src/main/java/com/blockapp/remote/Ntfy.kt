package com.blockapp.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente mínimo de ntfy.sh (servicio público de mensajes, sin cuenta).
 * Todas las llamadas son bloqueantes: usar siempre fuera del hilo principal.
 */
object Ntfy {
    private const val BASE = "https://ntfy.sh"

    data class Msg(val id: String, val time: Long, val message: String?, val attachmentUrl: String?)

    fun publish(topic: String, body: String): Boolean {
        val conn = URL("$BASE/$topic").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            // No mostrar como notificación a nadie que esté suscrito
            conn.setRequestProperty("X-Priority", "1")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    /** Mensajes del canal posteriores a [since] ("all", "12h", un id o un timestamp). */
    fun poll(topic: String, since: String): List<Msg> {
        val url = "$BASE/$topic/json?poll=1&since=" + URLEncoder.encode(since, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) return emptyList()
            conn.inputStream.bufferedReader().readLines().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val o = JSONObject(line)
                if (o.optString("event") != "message") return@mapNotNull null
                Msg(
                    id = o.optString("id"),
                    time = o.optLong("time"),
                    message = if (o.has("message")) o.optString("message") else null,
                    attachmentUrl = o.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /** Contenido del mensaje. Si superaba 4 KB, ntfy lo guarda como adjunto. */
    fun body(m: Msg): String? {
        val url = m.attachmentUrl ?: return m.message
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
