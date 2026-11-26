@file:OptIn(ExperimentalMaterial3Api::class)

package com.blockapp.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blockapp.data.AppSchedule
import com.blockapp.data.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

/** Elegir el horario permitido de una app. onSave(null) = quitar horario. */
@Composable
fun AppScheduleDialog(
    appLabel: String,
    current: AppSchedule?,
    onSave: (AppSchedule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val initial = current ?: AppSchedule.DEFAULT
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
    var days by remember { mutableStateOf(initial.days) }

    fun pick(currentMin: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, currentMin / 60, currentMin % 60, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horario · $appLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "La app solo se podrá usar dentro de este horario. Fuera de él pedirá el PIN parental.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(start) { start = it } }, modifier = Modifier.weight(1f)) {
                        Text("Desde ${Schedule.format(start)}")
                    }
                    OutlinedButton(onClick = { pick(end) { end = it } }, modifier = Modifier.weight(1f)) {
                        Text("Hasta ${Schedule.format(end)}")
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_LETTERS.forEachIndexed { i, d ->
                        val day = i + 1
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(d) }
                        )
                    }
                }
                Text(
                    "Si «Hasta» es menor que «Desde», cruza la medianoche (ej. 22:00 a 07:00).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(AppSchedule(start, end, days)) },
                enabled = days.isNotEmpty() && start != end
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

/** Tarjeta de «Pausa inmediata». pauseUntil en ms (0 = sin pausa). */
@Composable
fun PauseCard(pauseUntil: Long, onStart: (Int) -> Unit, onStop: () -> Unit, note: String? = null) {
    val active = pauseUntil > System.currentTimeMillis()
    SectionCard {
        Text("Pausa inmediata", style = MaterialTheme.typography.titleMedium)
        if (active) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(pauseUntil))
            Text(
                "Pausa activa hasta las $time: todas las apps bloqueadas salvo teléfono y mensajes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onStart(((pauseUntil - System.currentTimeMillis()) / 60_000L).toInt() + 30) }) {
                    Text("+30 min")
                }
                TextButton(onClick = onStop) { Text("Terminar pausa") }
            }
        } else {
            Text(
                "Bloquea todas las apps (salvo teléfono y mensajes) durante un rato: comida, estudio, dormir…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h", 480 to "8 h").forEach { (m, t) ->
                    OutlinedButton(onClick = { onStart(m) }) { Text(t) }
                }
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
