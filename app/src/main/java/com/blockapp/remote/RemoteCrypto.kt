package com.blockapp.remote

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado extremo a extremo entre el móvil del hijo y el del padre.
 * La clave (128 bits) solo existe en los dos móviles: se comparte con el
 * código de vinculación. ntfy.sh solo ve texto cifrado.
 */
object RemoteCrypto {
    /** Base32 sin caracteres confusos (sin I, O, 0, 1). */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newKey(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun keyToString(key: ByteArray): String = Base64.encodeToString(key, Base64.NO_WRAP)

    fun keyFromString(s: String?): ByteArray? = try {
        s?.let { Base64.decode(it, Base64.NO_WRAP) }?.takeIf { it.size == 16 }
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Código para escribir en el otro móvil, ej. ABCD-EFGH-…  (26 caracteres). */
    fun encodeCode(key: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in key) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString().chunked(4).joinToString("-")
    }

    fun decodeCode(code: String): ByteArray? {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        if (clean.length != 26) return null
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        val bytes = out.toByteArray()
        return if (bytes.size >= 16) bytes.copyOf(16) else null
    }

    /** Nombre del canal en ntfy, derivado de la clave (no revela la clave). */
    fun topic(key: ByteArray, suffix: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key + "blockapp-topic".toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return "blockapp_${hex}_$suffix"
    }

    fun seal(key: ByteArray, text: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(bos.toByteArray()), Base64.NO_WRAP)
    }

    /** Descifra; devuelve null si el mensaje no es nuestro o fue manipulado. */
    fun open(key: ByteArray, data: String): String? = try {
        val raw = Base64.decode(data.trim(), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, 12))
        val plain = cipher.doFinal(raw, 12, raw.size - 12)
        GZIPInputStream(plain.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        null
    }
}
