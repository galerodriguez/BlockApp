package com.blockapp.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.blockapp.data.Pause
import com.blockapp.data.Prefs
import com.blockapp.data.Schedule
import com.blockapp.remote.RemoteSync
import com.blockapp.vpn.FirewallVpnService

/**
 * Servicio de accesibilidad: solo escucha qué app pasa a primer plano
 * (no lee el contenido de la pantalla). Con eso:
 *  - pide el PIN en las apps protegidas,
 *  - cuenta el tiempo de uso y bloquea al superar el límite diario.
 */
class AppLockService : AccessibilityService() {

    private var currentPkg: String? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Cada 15 s guarda el uso y comprueba si la app abierta superó su límite. */
    private val tick = object : Runnable {
        override fun run() {
            UsageTracker.flush()
            val power = getSystemService(PowerManager::class.java)
            if (power.isInteractive) currentPkg?.let { checkAndLock(it) }
            FirewallVpnService.ensureRunning(this@AppLockService)
            RemoteSync.tick(this@AppLockService)
            handler.postDelayed(this, 15_000)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LockSession.lockAll()   // al apagar la pantalla se vuelve a bloquear todo
                    UsageTracker.pause()    // y no cuenta tiempo de uso
                }
                Intent.ACTION_USER_PRESENT -> {
                    UsageTracker.resume()
                    currentPkg?.let { checkAndLock(it) }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val pkgFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(this, packageReceiver, pkgFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.postDelayed(tick, 15_000)
        RemoteSync.syncSoon(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return

        // Ignorar nuestra propia app y ventanas que no son actividades (teclado, diálogos…)
        if (pkg == packageName || !isActivity(pkg, cls)) return

        val previous = currentPkg
        if (previous != pkg) {
            if (previous != null) LockSession.markLeft(previous, Prefs.graceSeconds * 1000L)
            UsageTracker.onForeground(pkg)
        }
        currentPkg = pkg
        checkAndLock(pkg)
    }

    private fun checkAndLock(pkg: String) {
        if (LockActivity.visibleFor == pkg) return // ya se está mostrando

        when {
            UsageTracker.isOverLimit(pkg) ->
                launchLock(pkg, LockActivity.MODE_LIMIT)

            Pause.isActive() && pkg !in pauseExempt() && !LockSession.isUnlocked(pkg) ->
                launchLock(pkg, LockActivity.MODE_PAUSE)

            Prefs.appSchedules[pkg]?.allowsNow() == false && !LockSession.isUnlocked(pkg) ->
                launchLock(pkg, LockActivity.MODE_SCHEDULE)

            pkg in Prefs.lockedApps && Schedule.isActive() && !LockSession.isUnlocked(pkg) ->
                launchLock(pkg, LockActivity.MODE_PIN)
        }
    }

    private var exemptCache: Set<String> = emptySet()
    private var exemptAt = 0L

    /** Apps permitidas durante la pausa (se recalcula cada 5 min). */
    private fun pauseExempt(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - exemptAt > 5 * 60_000L || exemptCache.isEmpty()) {
            exemptCache = Pause.exempt(this)
            exemptAt = now
        }
        return exemptCache
    }

    /** Apps recién instaladas: PIN automático (si está activado) y aviso al padre. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg == packageName) return
            val pm = packageManager
            // Solo apps con icono en el menú (no componentes del sistema)
            if (pm.getLaunchIntentForPackage(pkg) == null) return
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            val lock = Prefs.autoLockNew
            if (lock) Prefs.lockedApps = Prefs.lockedApps + pkg
            RemoteSync.notifyNewApp(this@AppLockService, pkg, label, lock)
        }
    }

    private fun launchLock(pkg: String, mode: String) {
        startActivity(
            Intent(this, LockActivity::class.java)
                .putExtra(LockActivity.EXTRA_PKG, pkg)
                .putExtra(LockActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isActivity(pkg: String, cls: String): Boolean = try {
        packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        UsageTracker.pause()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
