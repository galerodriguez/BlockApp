package com.blockapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_PIN = 8

/** Teclado numérico reutilizable. onSubmit devuelve true si el PIN se aceptó. */
@Composable
fun PinScreen(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    icon: ImageBitmap? = null,
    onBiometric: (() -> Unit)? = null,
    onSubmit: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: " ",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(if (onBiometric != null) "bio" else "", "0", "del")
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        val base = Modifier.size(72.dp).clip(CircleShape)
                        val styled = if (key.length == 1) {
                            base.background(MaterialTheme.colorScheme.surfaceVariant)
                        } else base.background(Color.Transparent)
                        val clickableMod = if (key.isEmpty()) styled else styled.clickable {
                            when (key) {
                                "bio" -> onBiometric?.invoke()
                                "del" -> pin = pin.dropLast(1)
                                else -> if (pin.length < MAX_PIN) pin += key
                            }
                        }
                        Box(clickableMod, contentAlignment = Alignment.Center) {
                            when (key) {
                                "bio" -> Text("👆", fontSize = 26.sp)
                                "del" -> Text("⌫", fontSize = 24.sp)
                                "" -> {}
                                else -> Text(key, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onSubmit(pin)
                    pin = ""
                },
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) { Text("Aceptar") }
        }
    }
}

/** Crear o cambiar el PIN (pide confirmarlo dos veces). */
@Composable
fun SetupPinFlow(onDone: (String) -> Unit, onCancel: (() -> Unit)? = null) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (onCancel != null) BackHandler { onCancel() }

    PinScreen(
        title = if (first == null) "Crea el PIN parental" else "Confirma el PIN parental",
        subtitle = "De 4 a 8 dígitos. Solo quien lo sepa podrá cambiar los ajustes o abrir las apps bloqueadas.",
        error = error,
        onSubmit = { pin ->
            when {
                first == null -> { first = pin; error = null; true }
                pin == first -> { onDone(pin); true }
                else -> { first = null; error = "Los PIN no coinciden. Vuelve a empezar."; false }
            }
        }
    )
}
