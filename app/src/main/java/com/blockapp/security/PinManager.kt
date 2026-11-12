package com.blockapp.security

import android.util.Base64
import com.blockapp.data.Prefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** El PIN nunca se guarda: solo su hash PBKDF2 con sal aleatoria. */
object PinManager {
    private const val ITERATIONS = 20_000
    private const val MAX_FAILS = 5
    private const val LOCKOUT_MS = 30_000L

    private var fails = 0
    private var lockedUntil = 0L

    sealed interface Result {
        object Ok : Result
        object Wrong : Result
        class LockedOut(val seconds: Int) : Result
    }

    fun hasPin(): Boolean = Prefs.pinHash != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Prefs.savePin(hash(pin, salt), Base64.encodeToString(salt, Base64.NO_WRAP))
    }

    /** Comprueba el PIN, registra fallos y aplica un bloqueo de 30 s tras 5 errores. */
    @Synchronized
    fun attempt(pin: String, where: String): Result {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return Result.LockedOut(((lockedUntil - now) / 1000 + 1).toInt())
        if (matches(pin)) {
            fails = 0
            return Result.Ok
        }
        fails++
        Prefs.addIntrusion(where)
        if (fails >= MAX_FAILS) {
            fails = 0
            lockedUntil = now + LOCKOUT_MS
            return Result.LockedOut((LOCKOUT_MS / 1000).toInt())
        }
        return Result.Wrong
    }

    fun message(result: Result): String? = when (result) {
        is Result.Ok -> null
        is Result.Wrong -> "PIN incorrecto"
        is Result.LockedOut -> "Demasiados intentos. Espera ${result.seconds} s"
    }

    private fun matches(pin: String): Boolean {
        val stored = Prefs.pinHash ?: return false
        val salt = Prefs.pinSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
