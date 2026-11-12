package com.blockapp.remote

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.blockapp.data.Apps
import com.blockapp.data.Prefs
import com.blockapp.lock.UsageTracker
import com.blockapp.vpn.FirewallVpnService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Lado del HIJO: recibe las órdenes del móvil del padre y le envía el estado
 * (apps instaladas, ajustes, uso de hoy, intentos fallidos).
 * Lo llama el servicio de accesibilidad, que está siempre en marcha.
 */
object RemoteSync {
    private const val POLL_EVERY_MS = 60_000L
    private const val STATUS_MIN_GAP_MS = 60_000L
    private const val STATUS_REFRESH_MS = 6 * 60 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var lastPoll = 0L
    @Volatile private var running = false

    fun key(): ByteArray? = RemoteCrypto.keyFromString(Prefs.remoteKey)

    fun isPaired(): Boolean = key() != null

    /** Código que hay que escribir en el móvil del padre, o null si no está vinculado. */
    fun code(): String? = key()?.let { RemoteCrypto.encodeCode(it) }

    /** Crea una clave nueva (invalida cualquier vinculación anterior) y devuelve el código. */
    fun pair(): String {
        val key = RemoteCrypto.newKey()
        Prefs.remoteKey = RemoteCrypto.keyToString(key)
        Prefs.remoteSince = (System.currentTimeMillis() / 1000).toString()
        Prefs.remoteLastTs = 0L
        Prefs.statusDirty = true
        lastPoll = 0L
        return RemoteCrypto.encodeCode(key)
    }

    fun unpair() {
        Prefs.remoteKey = null
    }

    /** Se llama cada pocos segundos; solo usa la red una vez por minuto. */
    fun tick(context: Context) {
        val key = key() ?: return
        val now = System.currentTimeMillis()
        if (running || now - lastPoll < POLL_EVERY_MS) return
        lastPoll = now
        running = true
        val app = context.applicationContext
        executor.execute {
            try {
                runOnce(app, key)
            } catch (_: Exception) {
                // Sin conexión o respuesta inesperada: se reintenta en el siguiente minuto
            } finally {
                running = false
            }
        }
    }

    /** Fuerza una sincronización en cuanto sea posible. */
    fun syncSoon(context: Context) {
        lastPoll = 0L
        tick(context)
    }

    private fun runOnce(context: Context, key: ByteArray) {
        var changed = false
        var wantStatus = false

        for (m in Ntfy.poll(RemoteCrypto.topic(key, "c"), Prefs.remoteSince)) {
            if (m.id.isNotBlank()) Prefs.remoteSince = m.id
            val text = Ntfy.body(m)?.let { RemoteCrypto.open(key, it) } ?: continue
            val o = JSONObject(text)
            val ts = o.optLong("ts")
            if (ts <= Prefs.remoteLastTs) continue
            Prefs.remoteLastTs = ts
            when (o.optString("type")) {
                "config" -> {
                    o.optJSONObject("cfg")?.let { ConfigJson.apply(it) }
                    changed = true
                }
                "bonus" -> {
                    val pkg = o.optString("pkg")
                    val minutes = o.optLong("min", UsageTracker.BONUS_MINUTES)
                    if (pkg.isNotBlank() && minutes in 1..600) {
                        UsageTracker.grantBonusMinutes(pkg, minutes)
                        changed = true
                    }
                }
                "status" -> wantStatus = true
            }
        }

        if (changed) {
            Prefs.statusDirty = true
            // Recarga el firewall con la lista nueva
            main.post {
                try {
                    if (Prefs.firewallEnabled && VpnService.prepare(context) == null) {
                        FirewallVpnService.start(context)
                    }
                } catch (_: Exception) {
                }
            }
        }

        val now = System.currentTimeMillis()
        val sinceLast = now - Prefs.remoteLastStatus
        val due = wantStatus || sinceLast > STATUS_REFRESH_MS || (Prefs.statusDirty && sinceLast > STATUS_MIN_GAP_MS)
        if (due) {
            val body = RemoteCrypto.seal(key, statusJson(context).toString())
            if (Ntfy.publish(RemoteCrypto.topic(key, "s"), body)) {
                Prefs.statusDirty = false
                Prefs.remoteLastStatus = now
            }
        }
    }

    private fun statusJson(context: Context): JSONObject = JSONObject().apply {
        put("ts", System.currentTimeMillis())
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("cfg", ConfigJson.current())
        put("firewall", Prefs.firewallEnabled)
        put("a11y", true) // si este código corre, el servicio de accesibilidad está activo

        val usage = JSONObject()
        for ((pkg, sec) in UsageTracker.snapshot()) usage.put(pkg, sec)
        put("usage", usage)

        val bonus = JSONObject()
        for ((pkg, min) in Prefs.bonusToday()) bonus.put(pkg, min)
        put("bonus", bonus)

        put("intrusions", JSONArray(Prefs.intrusions.take(20)))
        put("clones", JSONArray(Apps.clonedPackages(context).sorted()))

        val apps = JSONArray()
        for ((pkg, label) in Apps.launchable(context)) apps.put(JSONArray().put(pkg).put(label))
        put("apps", apps)
    }
}
