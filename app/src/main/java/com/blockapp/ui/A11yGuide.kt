package com.blockapp.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Instructivo para activar el servicio de accesibilidad.
 * En Android 13+ las apps instaladas fuera de la Play Store aparecen en gris
 * («Controlado por un ajuste restringido») hasta permitirlo a mano.
 */
@Composable
fun A11yGuideDialog(
    onOpenA11y: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activar el bloqueo con PIN") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Step("1", "Abre Accesibilidad y busca «BlockApp – Bloqueo de apps».")
                Step("2", "Si se puede tocar, actívalo y acepta. ¡Listo!")
                if (restricted) {
                    Text(
                        "Si aparece en gris («Controlado por un ajuste restringido»):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Step("3", "Tócalo una vez aunque esté gris. Saldrá «A la app se le negó el acceso»: pulsa Cerrar.")
                    Step("4", "Abre la info de BlockApp (botón de abajo).")
                    Step("5", "Arriba a la derecha toca los tres puntos ⋮ › «Permitir ajustes restringidos» y confirma con tu PIN o huella.")
                    Step("6", "Vuelve a Accesibilidad: BlockApp ya no estará gris. Actívalo.")
                    Text(
                        "Si no ves los tres puntos, repite el paso 3 y vuelve a mirar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onOpenA11y, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir Accesibilidad")
                }
                if (restricted) {
                    OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir info de BlockApp")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun Step(n: String, text: String) {
    Text("$n. $text", style = MaterialTheme.typography.bodyMedium)
}
