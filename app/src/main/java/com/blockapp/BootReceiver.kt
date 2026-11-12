package com.blockapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.blockapp.data.Prefs
import com.blockapp.vpn.FirewallVpnService

/** Vuelve a encender el firewall al reiniciar el móvil o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.firewallEnabled && VpnService.prepare(context) == null) {
            FirewallVpnService.start(context)
        }
    }
}
