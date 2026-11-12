package com.blockapp.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.blockapp.data.Prefs
import com.blockapp.data.Schedule
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Firewall sin root: crea una VPN local en la que SOLO entran las apps bloqueadas.
 * Sus paquetes se leen y se descartan, así que se quedan sin internet.
 * El resto de apps no pasan por la VPN y funcionan con normalidad.
 *
 * Fuera del horario (o sin apps elegidas) la VPN queda "en reposo": solo contiene
 * a BlockApp, que no usa internet, así nada se bloquea pero el servicio sigue vivo
 * para reactivarse cuando empiece el horario.
 */
class FirewallVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var drain: Thread? = null
    private var appliedActive: Boolean? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scheduleTick = object : Runnable {
        override fun run() {
            if (Schedule.isActive() != appliedActive) startVpn()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (!startVpn()) {
            shutdown()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(scheduleTick)
        handler.postDelayed(scheduleTick, 30_000)
        return START_STICKY
    }

    @Synchronized
    private fun startVpn(): Boolean {
        closeTun()
        val active = Schedule.isActive()
        val blocked = if (active) Prefs.blockedNet - packageName else emptySet()

        val builder = Builder()
            .setSession("BlockApp Firewall")
            .addAddress("10.111.222.1", 32)
            .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        var added = 0
        for (pkg in blocked) {
            try {
                builder.addAllowedApplication(pkg)
                added++
            } catch (_: PackageManager.NameNotFoundException) {
                // App desinstalada: se ignora
            }
        }
        // ¡Importante! Una VPN sin apps permitidas capturaría TODO el tráfico del móvil.
        // En reposo metemos solo a BlockApp, que no tiene tráfico.
        if (added == 0) builder.addAllowedApplication(packageName)

        val fd = builder.establish() ?: return false // null si falta el permiso VPN
        tun = fd
        appliedActive = active
        drain = Thread({ drainPackets(fd) }, "vpn-drain").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /** Lee los paquetes y no hace nada con ellos: nunca salen a internet. */
    private fun drainPackets(fd: ParcelFileDescriptor) {
        val channel = FileInputStream(fd.fileDescriptor).channel
        val buffer = ByteBuffer.allocate(32767)
        try {
            while (!Thread.currentThread().isInterrupted) {
                buffer.clear()
                if (channel.read(buffer) <= 0) Thread.sleep(50)
            }
        } catch (_: Exception) {
            // Interfaz cerrada o hilo interrumpido
        }
    }

    @Synchronized
    private fun closeTun() {
        drain?.interrupt()
        drain = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    private fun shutdown() {
        handler.removeCallbacks(scheduleTick)
        closeTun()
        stopSelf()
    }

    override fun onRevoke() {
        // Otra VPN tomó el control o el usuario la desactivó en Ajustes
        Prefs.firewallEnabled = false
        handler.removeCallbacks(scheduleTick)
        closeTun()
        super.onRevoke()
    }

    override fun onDestroy() {
        handler.removeCallbacks(scheduleTick)
        closeTun()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.blockapp.vpn.START"
        private const val ACTION_STOP = "com.blockapp.vpn.STOP"

        /** Arranca o recarga el firewall con la lista y el horario actuales. */
        fun start(context: Context) {
            context.startService(Intent(context, FirewallVpnService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FirewallVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
