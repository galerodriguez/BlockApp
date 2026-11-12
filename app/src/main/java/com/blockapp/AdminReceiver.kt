package com.blockapp

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Administrador de dispositivo: mientras está activo, BlockApp no se puede desinstalar.
 * Para quitarlo hay que entrar en Ajustes (que conviene proteger con PIN).
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Si desactivas esto, BlockApp podrá desinstalarse y dejará de proteger tus apps."
}
