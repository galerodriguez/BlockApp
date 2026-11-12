package com.blockapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ajustes guardados cifrados (AES-256) con EncryptedSharedPreferences. */
object Prefs {
    private const val K_NET = "blocked_net"
    private const val K_LOCK = "locked_apps"
    private const val K_HASH = "pin_hash"
    private const val K_SALT = "pin_salt"
    private const val K_FIREWALL = "firewall_on"
    private const val K_BIO = "biometric_on"
    private const val K_GRACE = "grace_seconds"
    private const val K_LOG = "intrusions"
    private const val K_LIMITS = "limits"
    private const val K_DAY = "usage_day"
    private const val K_USAGE = "usage_today"
    private const val K_BONUS = "bonus_today"
    private const val K_SCHED_ON = "schedule_on"
    private const val K_SCHED_START = "schedule_start"
    private const val K_SCHED_END = "schedule_end"
    private const val K_SCHED_DAYS = "schedule_days"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        sp = EncryptedSharedPreferences.create(
            app,
            "blockapp_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Apps sin acceso a internet. */
    var blockedNet: Set<String>
        get() = sp.getStringSet(K_NET, emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet(K_NET, value).apply()

    /** Apps que piden PIN al abrirse. */
    var lockedApps: Set<String>
        get() = sp.getStringSet(K_LOCK, emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet(K_LOCK, value).apply()

    val pinHash: String? get() = sp.getString(K_HASH, null)
    val pinSalt: String? get() = sp.getString(K_SALT, null)

    fun savePin(hash: String, salt: String) {
        sp.edit().putString(K_HASH, hash).putString(K_SALT, salt).commit()
    }

    var firewallEnabled: Boolean
        get() = sp.getBoolean(K_FIREWALL, false)
        set(value) = sp.edit().putBoolean(K_FIREWALL, value).apply()

    var biometricEnabled: Boolean
        get() = sp.getBoolean(K_BIO, true)
        set(value) = sp.edit().putBoolean(K_BIO, value).apply()

    /** Segundos que una app sigue desbloqueada tras salir de ella (0 = se bloquea al salir). */
    var graceSeconds: Int
        get() = sp.getInt(K_GRACE, 0)
        set(value) = sp.edit().putInt(K_GRACE, value).apply()

    /** Horario: si está activo, los bloqueos solo se aplican dentro de él. */
    var scheduleEnabled: Boolean
        get() = sp.getBoolean(K_SCHED_ON, false)
        set(value) = sp.edit().putBoolean(K_SCHED_ON, value).apply()

    /** Minutos desde medianoche (ej. 22:00 = 1320). */
    var scheduleStart: Int
        get() = sp.getInt(K_SCHED_START, 8 * 60)
        set(value) = sp.edit().putInt(K_SCHED_START, value).apply()

    var scheduleEnd: Int
        get() = sp.getInt(K_SCHED_END, 18 * 60)
        set(value) = sp.edit().putInt(K_SCHED_END, value).apply()

    /** Días activos, 1 = lunes … 7 = domingo. */
    var scheduleDays: Set<Int>
        get() = (sp.getStringSet(K_SCHED_DAYS, null) ?: setOf("1", "2", "3", "4", "5"))
            .mapNotNull { it.toIntOrNull() }.toSet()
        set(value) = sp.edit().putStringSet(K_SCHED_DAYS, value.map { it.toString() }.toSet()).apply()

    /** Registro de intentos fallidos, el más reciente primero. */
    val intrusions: List<String>
        get() = (sp.getString(K_LOG, "") ?: "").split("\n").filter { it.isNotBlank() }

    fun addIntrusion(where: String) {
        val ts = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val list = (listOf("$ts · $where") + intrusions).take(50)
        sp.edit().putString(K_LOG, list.joinToString("\n")).apply()
    }

    fun clearIntrusions() {
        sp.edit().remove(K_LOG).apply()
    }

    // ---------- Límites de uso diario ----------

    /** Límite diario en minutos por app. */
    var limits: Map<String, Int>
        get() = (sp.getStringSet(K_LIMITS, emptySet()) ?: emptySet()).mapNotNull {
            val parts = it.split("=")
            val minutes = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && minutes != null) parts[0] to minutes else null
        }.toMap()
        set(value) = sp.edit().putStringSet(K_LIMITS, value.map { "${it.key}=${it.value}" }.toSet()).apply()

    /** Segundos usados hoy por app. */
    fun usageToday(): Map<String, Long> = dailyMap(K_USAGE)

    /** Minutos extra concedidos hoy con el PIN. */
    fun bonusToday(): Map<String, Long> = dailyMap(K_BONUS)

    fun addUsage(pkg: String, seconds: Long) {
        val m = usageToday().toMutableMap()
        m[pkg] = (m[pkg] ?: 0L) + seconds
        saveDaily(K_USAGE, m)
    }

    fun addBonus(pkg: String, minutes: Long) {
        val m = bonusToday().toMutableMap()
        m[pkg] = (m[pkg] ?: 0L) + minutes
        saveDaily(K_BONUS, m)
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dailyMap(key: String): Map<String, Long> {
        if (sp.getString(K_DAY, null) != today()) return emptyMap()
        return (sp.getString(key, "") ?: "").split(";").mapNotNull {
            val parts = it.split("=")
            val v = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size == 2 && v != null) parts[0] to v else null
        }.toMap()
    }

    /** Guarda el mapa del día; si cambió el día, borra los datos de ayer. */
    private fun saveDaily(key: String, map: Map<String, Long>) {
        val t = today()
        val editor = sp.edit()
        if (sp.getString(K_DAY, null) != t) {
            editor.putString(K_DAY, t)
            editor.remove(if (key == K_USAGE) K_BONUS else K_USAGE)
        }
        editor.putString(key, map.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }
}
