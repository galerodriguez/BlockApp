package com.blockapp.lock

import com.blockapp.data.Prefs

/**
 * Cuenta cuánto tiempo está cada app en primer plano (con la pantalla encendida).
 * Lo alimenta el servicio de accesibilidad; no necesita permisos extra.
 */
object UsageTracker {
    /** Minutos que da el PIN cuando se acaba el tiempo. */
    const val BONUS_MINUTES = 15L

    private var pkg: String? = null
    private var since = 0L // 0 = en pausa (pantalla apagada)

    @Synchronized
    fun onForeground(newPkg: String) {
        flush()
        pkg = newPkg
        since = System.currentTimeMillis()
    }

    @Synchronized
    fun pause() {
        flush()
        since = 0L
    }

    @Synchronized
    fun resume() {
        if (pkg != null && since == 0L) since = System.currentTimeMillis()
    }

    /** Guarda el tiempo acumulado de la sesión actual. */
    @Synchronized
    fun flush() {
        val p = pkg ?: return
        if (since == 0L) return
        val elapsed = (System.currentTimeMillis() - since) / 1000
        if (elapsed > 0) {
            Prefs.addUsage(p, elapsed)
            since += elapsed * 1000
        }
    }

    @Synchronized
    fun usedSeconds(p: String): Long {
        var s = Prefs.usageToday()[p] ?: 0L
        if (p == pkg && since > 0) s += (System.currentTimeMillis() - since) / 1000
        return s
    }

    /** Uso de hoy de todas las apps, incluida la sesión en curso. */
    @Synchronized
    fun snapshot(): Map<String, Long> {
        val m = Prefs.usageToday().toMutableMap()
        val p = pkg
        if (p != null && since > 0) m[p] = (m[p] ?: 0L) + (System.currentTimeMillis() - since) / 1000
        return m
    }

    /** Segundos permitidos hoy (límite + extras), o null si la app no tiene límite. */
    fun allowedSeconds(p: String): Long? {
        val limit = Prefs.limits[p] ?: return null
        return (limit + (Prefs.bonusToday()[p] ?: 0L)) * 60L
    }

    fun isOverLimit(p: String): Boolean {
        val allowed = allowedSeconds(p) ?: return false
        return usedSeconds(p) >= allowed
    }

    fun grantBonus(p: String) = Prefs.addBonus(p, BONUS_MINUTES)

    /** Tiempo extra concedido a distancia desde el móvil del padre. */
    fun grantBonusMinutes(p: String, minutes: Long) = Prefs.addBonus(p, minutes)

    fun format(seconds: Long): String {
        val totalMin = seconds / 60
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }
}
