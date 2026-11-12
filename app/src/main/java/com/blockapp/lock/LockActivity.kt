package com.blockapp.lock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.blockapp.data.Prefs
import com.blockapp.security.Biometric
import com.blockapp.security.PinManager
import com.blockapp.ui.BlockAppTheme
import com.blockapp.ui.PinScreen

/**
 * Pantalla que tapa la app:
 *  - MODE_PIN: app protegida, se abre con el PIN.
 *  - MODE_LIMIT: se acabó el tiempo diario; el PIN da 15 min más.
 */
class LockActivity : AppCompatActivity() {

    private var targetPkg = ""
    private var mode by mutableStateOf(MODE_PIN)
    private var label by mutableStateOf("")
    private var icon by mutableStateOf<ImageBitmap?>(null)
    private var error by mutableStateOf<String?>(null)
    private var autoPrompted = false
    private var promptOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!readTarget(intent)) {
            finish()
            return
        }
        // "Atrás" no deja pasar: manda al inicio
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            BlockAppTheme {
                val isLimit = mode == MODE_LIMIT
                val limitMin = Prefs.limits[targetPkg] ?: 0
                PinScreen(
                    title = if (isLimit) "Se acabó el tiempo de $label" else "$label está bloqueada",
                    subtitle = if (isLimit) {
                        "Límite diario: ${UsageTracker.format(limitMin * 60L)}. " +
                            "Vuelve mañana o introduce el PIN para ${UsageTracker.BONUS_MINUTES} min más."
                    } else "Introduce tu PIN para continuar",
                    error = error,
                    icon = icon,
                    onBiometric = if (Biometric.available(this@LockActivity)) ::promptBiometric else null,
                    onSubmit = ::tryPin
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val oldPkg = targetPkg
        val oldMode = mode
        setIntent(intent)
        if (readTarget(intent) && (targetPkg != oldPkg || mode != oldMode)) {
            error = null
            autoPrompted = false
        }
    }

    override fun onResume() {
        super.onResume()
        visibleFor = targetPkg
        if (!autoPrompted && Biometric.available(this)) {
            autoPrompted = true
            promptBiometric()
        }
    }

    override fun onPause() {
        super.onPause()
        // Con el diálogo de huella abierto seguimos tapando la app
        if (!promptOpen && visibleFor == targetPkg) visibleFor = null
    }

    override fun onStop() {
        super.onStop()
        if (visibleFor == targetPkg) visibleFor = null
        // Si el usuario se va sin desbloquear, la pantalla se descarta
        if (!isChangingConfigurations && !isFinishing) finish()
    }

    private fun readTarget(i: Intent?): Boolean {
        val pkg = i?.getStringExtra(EXTRA_PKG) ?: return false
        targetPkg = pkg
        mode = i.getStringExtra(EXTRA_MODE) ?: MODE_PIN
        try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            label = packageManager.getApplicationLabel(info).toString()
            icon = packageManager.getApplicationIcon(info).toBitmap(128, 128).asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            label = pkg
            icon = null
        }
        return true
    }

    private fun tryPin(pin: String): Boolean {
        val result = PinManager.attempt(pin, label)
        error = PinManager.message(result)
        if (result is PinManager.Result.Ok) unlock()
        return result is PinManager.Result.Ok
    }

    private fun promptBiometric() {
        val what = if (mode == MODE_LIMIT) "Dar ${UsageTracker.BONUS_MINUTES} min más a $label" else "Desbloquear $label"
        promptOpen = true
        Biometric.prompt(this, what, onClosed = { promptOpen = false }) { unlock() }
    }

    private fun unlock() {
        if (mode == MODE_LIMIT) UsageTracker.grantBonus(targetPkg)
        LockSession.markInside(targetPkg)
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PKG = "target_package"
        const val EXTRA_MODE = "mode"
        const val MODE_PIN = "pin"
        const val MODE_LIMIT = "limit"

        /** App que la pantalla de bloqueo está tapando ahora mismo (para no relanzarla). */
        @Volatile
        var visibleFor: String? = null
    }
}
