package com.blockapp.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * Uso de las apps según Android («Acceso al uso»). Da el tiempo real de los últimos días
 * aunque BlockApp no estuviera activo. Requiere que el usuario conceda el permiso.
 */
object Usage {
    const val DAYS = 7

    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Segundos en pantalla por app en los últimos [days] días, o null sin permiso. Bloqueante. */
    fun lastDays(context: Context, days: Int = DAYS): Map<String, Long>? {
        if (!hasAccess(context)) return null
        return try {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val start = end - days * 24L * 60 * 60 * 1000
            usm.queryAndAggregateUsageStats(start, end)
                .mapValues { it.value.totalTimeInForeground / 1000 }
                .filterValues { it > 0 }
        } catch (_: Exception) {
            null
        }
    }
}
