package com.blockapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blockapp.data.Prefs
import com.blockapp.lock.AppLockService
import com.blockapp.security.Biometric
import com.blockapp.security.PinManager
import com.blockapp.ui.BlockAppTheme
import com.blockapp.ui.HomeScreen
import com.blockapp.ui.ParentScreen
import com.blockapp.ui.RoleScreen
import com.blockapp.ui.PinScreen
import com.blockapp.ui.SetupPinFlow
import com.blockapp.vpn.FirewallVpnService

class MainActivity : AppCompatActivity() {

    private var hasPin by mutableStateOf(false)
    private var role by mutableStateOf<String?>(null)
    private var authed by mutableStateOf(false)
    private var changingPin by mutableStateOf(false)
    private var authError by mutableStateOf<String?>(null)
    private var a11yOn by mutableStateOf(false)
    private var firewallOn by mutableStateOf(false)
    private var adminOn by mutableStateOf(false)

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }

    /** Evita pedir el PIN otra vez al volver de Ajustes o del diálogo de VPN. */
    private var keepSession = false
    private var autoPrompted = false

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            enableFirewall()
        } else {
            firewallOn = false
            Toast.makeText(this, "Sin el permiso de VPN el firewall no puede funcionar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPin = PinManager.hasPin()
        // Instalaciones anteriores (ya con PIN) eran siempre el móvil protegido
        if (Prefs.role == null && hasPin) Prefs.role = Prefs.ROLE_CHILD
        role = Prefs.role
        firewallOn = Prefs.firewallEnabled

        setContent {
            BlockAppTheme {
                when {
                    role == null -> RoleScreen(onPick = {
                        Prefs.role = it
                        role = it
                    })

                    !hasPin -> SetupPinFlow(onDone = {
                        PinManager.setPin(it)
                        hasPin = true
                        authed = true
                    })

                    !authed -> PinScreen(
                        title = "BlockApp",
                        subtitle = if (role == Prefs.ROLE_PARENT) "Introduce el PIN de este móvil" else "Introduce el PIN parental",
                        error = authError,
                        onBiometric = if (Biometric.available(this@MainActivity)) ::promptBiometric else null,
                        onSubmit = ::checkPin
                    )

                    changingPin -> SetupPinFlow(
                        onDone = {
                            PinManager.setPin(it)
                            changingPin = false
                            Toast.makeText(this@MainActivity, "PIN actualizado", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { changingPin = false }
                    )

                    role == Prefs.ROLE_PARENT -> ParentScreen(
                        onChangePin = { changingPin = true },
                        onSwitchRole = {
                            Prefs.role = Prefs.ROLE_CHILD
                            role = Prefs.ROLE_CHILD
                        }
                    )

                    else -> HomeScreen(
                        a11yOn = a11yOn,
                        firewallOn = firewallOn,
                        onOpenA11y = ::openAccessibilitySettings,
                        onFirewallToggle = ::toggleFirewall,
                        adminOn = adminOn,
                        onAdminToggle = ::toggleAdmin,
                        onBlockedNetChanged = {
                            if (Prefs.firewallEnabled) FirewallVpnService.start(this@MainActivity)
                        },
                        onChangePin = { changingPin = true }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        keepSession = false
        a11yOn = isAccessibilityEnabled()
        firewallOn = Prefs.firewallEnabled
        adminOn = dpm.isAdminActive(adminComponent)
        if (hasPin && !authed && !autoPrompted && Biometric.available(this)) {
            autoPrompted = true
            promptBiometric()
        }
    }

    override fun onStop() {
        super.onStop()
        // Al salir de la app, se vuelve a pedir el PIN
        if (!keepSession && !isChangingConfigurations) {
            authed = false
            changingPin = false
            autoPrompted = false
        }
        keepSession = false
    }

    private fun checkPin(pin: String): Boolean {
        val result = PinManager.attempt(pin, "BlockApp")
        authError = PinManager.message(result)
        val ok = result is PinManager.Result.Ok
        if (ok) authed = true
        return ok
    }

    private fun promptBiometric() {
        Biometric.prompt(this, "Abrir BlockApp") {
            authed = true
            authError = null
        }
    }

    private fun toggleFirewall(on: Boolean) {
        if (on) {
            val consent = VpnService.prepare(this)
            if (consent != null) {
                keepSession = true
                vpnConsent.launch(consent)
            } else {
                enableFirewall()
            }
        } else {
            Prefs.firewallEnabled = false
            firewallOn = false
            FirewallVpnService.stop(this)
        }
    }

    private fun toggleAdmin(on: Boolean) {
        if (on) {
            keepSession = true
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Impide que BlockApp se desinstale sin tu PIN."
                    )
            )
        } else {
            dpm.removeActiveAdmin(adminComponent)
            adminOn = false
        }
    }

    private fun enableFirewall() {
        Prefs.firewallEnabled = true
        firewallOn = true
        FirewallVpnService.start(this)
    }

    private fun openAccessibilitySettings() {
        keepSession = true
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Busca «BlockApp – Bloqueo de apps» y actívalo", Toast.LENGTH_LONG).show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, AppLockService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), ignoreCase = true) ||
                it.equals(me.flattenToShortString(), ignoreCase = true)
        }
    }
}
