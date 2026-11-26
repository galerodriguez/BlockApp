package com.blockapp.data

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * «Pausa inmediata»: durante X minutos se bloquean TODAS las apps salvo las
 * imprescindibles (teléfono, mensajes, pantalla de inicio). El PIN parental
 * permite abrir una app concreta igualmente.
 */
object Pause {
    private val ALWAYS_ALLOWED = setOf(
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    fun isActive(): Boolean = System.currentTimeMillis() < Prefs.pauseUntil

    fun start(minutes: Int) {
        Prefs.pauseUntil = System.currentTimeMillis() + minutes * 60_000L
    }

    fun stop() {
        Prefs.pauseUntil = 0L
    }

    /** Apps que nunca se bloquean durante la pausa. */
    fun exempt(context: Context): Set<String> {
        val out = HashSet<String>(ALWAYS_ALLOWED)
        out += context.packageName
        try {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it }
        } catch (_: Exception) {
        }
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { out += it }
        } catch (_: Exception) {
        }
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, 0).forEach { out += it.activityInfo.packageName }
        } catch (_: Exception) {
        }
        return out
    }
}
