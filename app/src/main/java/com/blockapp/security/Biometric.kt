package com.blockapp.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.blockapp.data.Prefs

object Biometric {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun hardwareReady(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun available(context: Context): Boolean = Prefs.biometricEnabled && hardwareReady(context)

    fun prompt(
        activity: FragmentActivity,
        subtitle: String,
        onClosed: () -> Unit = {},
        onSuccess: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onClosed()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onClosed()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BlockApp")
            .setSubtitle(subtitle)
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
