package com.blockapp.lock

/** Recuerda en memoria qué apps se han desbloqueado y hasta cuándo. */
object LockSession {
    private val unlockedUntil = HashMap<String, Long>()

    @Synchronized
    fun isUnlocked(pkg: String): Boolean =
        (unlockedUntil[pkg] ?: 0L) > System.currentTimeMillis()

    /** El usuario está dentro de la app: desbloqueada indefinidamente. */
    @Synchronized
    fun markInside(pkg: String) {
        unlockedUntil[pkg] = Long.MAX_VALUE
    }

    /** El usuario salió: empieza a contar el periodo de gracia. */
    @Synchronized
    fun markLeft(pkg: String, graceMs: Long) {
        if (unlockedUntil[pkg] == Long.MAX_VALUE) {
            unlockedUntil[pkg] = System.currentTimeMillis() + graceMs
        }
    }

    @Synchronized
    fun lockAll() = unlockedUntil.clear()
}
