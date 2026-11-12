package com.blockapp.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager

object Apps {
    /** Apps con icono en el menú: lista de (paquete, nombre) ordenada por nombre. */
    fun launchable(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }

    /**
     * Apps que tienen una copia en otro perfil (Dual Messenger de Samsung, "Apps duales"
     * de Xiaomi, perfil de trabajo…). Android las identifica con el mismo nombre de paquete.
     */
    fun clonedPackages(context: Context): Set<String> = try {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userManager = context.getSystemService(UserManager::class.java)
        val me = Process.myUserHandle()
        userManager.userProfiles
            .filter { it != me }
            .flatMap { user -> launcherApps.getActivityList(null, user).map { it.applicationInfo.packageName } }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
