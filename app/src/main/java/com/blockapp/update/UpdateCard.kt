package com.blockapp.update

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tarjeta de actualización. Comprueba GitHub al mostrarse; si hay versión nueva,
 * ofrece descargarla e instalarla. Si no la hay, muestra solo la versión actual
 * con un botón para volver a comprobar.
 */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { Updater.installedVersionCode(context) }
    val installedName = remember { Updater.installedVersionName(context) }

    var latest by remember { mutableStateOf<Updater.Release?>(null) }
    var checking by remember { mutableStateOf(true) }
    var checkTick by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }

    LaunchedEffect(checkTick) {
        checking = true
        latest = withContext(Dispatchers.IO) { Updater.latest() }
        checking = false
    }

    fun startUpdate() {
        if (!Updater.canInstall(context)) {
            Toast.makeText(
                context,
                "Activa «Permitir de esta fuente» para BlockApp y vuelve a pulsar Actualizar",
                Toast.LENGTH_LONG
            ).show()
            Updater.openInstallPermission(context)
            return
        }
        downloading = true
        progress = -1
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                Updater.download(context) { pct -> scope.launch { progress = pct } }
            }
            downloading = false
            if (file == null) {
                Toast.makeText(context, "No se pudo descargar. Revisa la conexión.", Toast.LENGTH_LONG).show()
            } else {
                Updater.install(context, file)
            }
        }
    }

    val release = latest
    val available = release != null && release.versionCode > installed

    if (!available) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (checking) "Versión $installedName · comprobando…" else "Versión $installedName · al día",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!checking) TextButton(onClick = { checkTick++ }) { Text("Buscar actualización") }
        }
        return
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Actualización disponible", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tienes la versión $installedName. Hay una nueva: ${release?.name}.",
                style = MaterialTheme.typography.bodySmall
            )
            if (downloading) {
                if (progress in 0..100) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Descargando… $progress %", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Descargando…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Button(onClick = { startUpdate() }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar ahora") }
            }
        }
    }
}
