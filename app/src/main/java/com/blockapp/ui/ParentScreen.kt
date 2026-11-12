@file:OptIn(ExperimentalMaterial3Api::class)

package com.blockapp.ui

import android.app.TimePickerDialog
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blockapp.data.Schedule
import com.blockapp.lock.UsageTracker
import com.blockapp.remote.ConfigJson
import com.blockapp.remote.ParentLink
import com.blockapp.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes del hijo que el padre está editando. */
private data class ChildConfig(
    val net: Set<String>,
    val lock: Set<String>,
    val limits: Map<String, Int>,
    val schedOn: Boolean,
    val schedStart: Int,
    val schedEnd: Int,
    val schedDays: Set<Int>,
    val grace: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("net", JSONArray(net.sorted()))
        put("lock", JSONArray(lock.sorted()))
        val lim = JSONObject()
        for ((pkg, minutes) in limits) lim.put(pkg, minutes)
        put("limits", lim)
        put("schedOn", schedOn)
        put("schedStart", schedStart)
        put("schedEnd", schedEnd)
        put("schedDays", JSONArray(schedDays.sorted()))
        put("grace", grace)
    }

    companion object {
        fun from(o: JSONObject?): ChildConfig = ChildConfig(
            net = ConfigJson.strings(o?.optJSONArray("net")),
            lock = ConfigJson.strings(o?.optJSONArray("lock")),
            limits = o?.optJSONObject("limits")?.let { ConfigJson.intMap(it) } ?: emptyMap(),
            schedOn = o?.optBoolean("schedOn") ?: false,
            schedStart = o?.optInt("schedStart", 8 * 60) ?: 8 * 60,
            schedEnd = o?.optInt("schedEnd", 18 * 60) ?: 18 * 60,
            schedDays = o?.optJSONArray("schedDays")?.let { ConfigJson.ints(it) } ?: setOf(1, 2, 3, 4, 5),
            grace = o?.optInt("grace") ?: 0,
        )
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

/** Pantalla del móvil del padre: mando a distancia del móvil del hijo. */
@Composable
fun ParentScreen(onChangePin: () -> Unit, onSwitchRole: () -> Unit) {
    var linked by remember { mutableStateOf(ParentLink.isLinked()) }
    if (!linked) {
        LinkScreen(onLinked = { linked = true }, onSwitchRole = onSwitchRole)
    } else {
        RemoteControlScreen(onUnlinked = { linked = false }, onChangePin = onChangePin)
    }
}

@Composable
private fun LinkScreen(onLinked: () -> Unit, onSwitchRole: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Vincular con el móvil del hijo", style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    "1. En el móvil del hijo abre BlockApp, introduce el PIN parental y pulsa " +
                        "«Vincular con el móvil del padre».\n" +
                        "2. Escribe aquí el código que aparece (ej. ABCD-EFGH-…).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del hijo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Código de vinculación") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(onClick = {
                    if (ParentLink.link(code, name)) {
                        Toast.makeText(context, "Vinculado", Toast.LENGTH_SHORT).show()
                        onLinked()
                    } else {
                        error = "Código no válido: revisa que tenga 26 letras y números"
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Vincular") }
            }
            item {
                TextButton(onClick = onSwitchRole) { Text("Usar este móvil como móvil protegido") }
            }
        }
    }
}

@Composable
private fun RemoteControlScreen(onUnlinked: () -> Unit, onChangePin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(ParentLink.cachedStatus()) }
    var cfg by remember { mutableStateOf(ChildConfig.from(status?.optJSONObject("cfg"))) }
    var dirty by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var limitFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmUnlink by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    fun applyStatus(s: JSONObject?) {
        if (s == null) return
        status = s
        if (!dirty) cfg = ChildConfig.from(s.optJSONObject("cfg"))
    }

    // Al abrir (o al pulsar Actualizar): pide el estado al hijo y lo consulta durante 2 minutos
    LaunchedEffect(refreshTick) {
        loading = true
        withContext(Dispatchers.IO) { ParentLink.requestStatus() }
        applyStatus(withContext(Dispatchers.IO) { ParentLink.fetchStatus() })
        repeat(8) {
            delay(15_000)
            applyStatus(withContext(Dispatchers.IO) { ParentLink.fetchStatus() })
            if (it == 4) loading = false
        }
        loading = false
    }

    fun send() {
        sending = true
        val json = cfg.toJson()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ParentLink.sendConfig(json) }
            sending = false
            if (ok) {
                dirty = false
                Toast.makeText(context, "Enviado. El móvil del hijo lo aplicará en 1 o 2 minutos.", Toast.LENGTH_LONG).show()
                refreshTick++
            } else {
                Toast.makeText(context, "No se pudo enviar. Revisa la conexión.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun sendBonus(pkg: String, label: String, minutes: Long) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ParentLink.sendBonus(pkg, minutes) }
            Toast.makeText(
                context,
                if (ok) "+$minutes min para $label enviados" else "No se pudo enviar",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    val s = status
    val apps: List<Pair<String, String>> = remember(s) {
        val arr = s?.optJSONArray("apps") ?: return@remember emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONArray(i) ?: return@mapNotNull null
            a.optString(0) to a.optString(1)
        }
    }
    val usage = remember(s) { ConfigJson.longMap(s?.optJSONObject("usage")) }
    val bonus = remember(s) { ConfigJson.longMap(s?.optJSONObject("bonus")) }
    val clones = remember(s) { ConfigJson.strings(s?.optJSONArray("clones")) }
    val intrusions = remember(s) { ConfigJson.strings(s?.optJSONArray("intrusions")).toList() }
    val labelOf: (String) -> String = { pkg -> apps.firstOrNull { it.first == pkg }?.second ?: pkg }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Móvil de ${Prefs.parentChildName}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Estado de la conexión
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (s == null) {
                                Text("Esperando al móvil del hijo…", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Tiene que tener internet y el «Bloqueo con PIN» activado. Puede tardar 1–2 min.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(s.optString("device"), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Actualizado " + DateUtils.getRelativeTimeSpanString(s.optLong("ts")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!s.optBoolean("firewall")) {
                                    Text(
                                        "El firewall está apagado en su móvil: «Sin red» no tendrá efecto.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        if (loading) CircularProgressIndicator()
                        else TextButton(onClick = { refreshTick++ }) { Text("Actualizar") }
                    }
                }
            }

            // Enviar cambios
            if (dirty) {
                item {
                    Button(onClick = { send() }, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
                        Text(if (sending) "Enviando…" else "Enviar cambios al móvil del hijo")
                    }
                }
            }

            // Uso de hoy
            if (s != null && cfg.limits.isNotEmpty()) {
                item {
                    SectionCard {
                        Text("Uso de hoy", style = MaterialTheme.typography.titleMedium)
                        cfg.limits.entries.sortedBy { labelOf(it.key).lowercase() }.forEach { (pkg, limitMin) ->
                            val label = labelOf(pkg)
                            val used = usage[pkg] ?: 0L
                            val allowed = (limitMin + (bonus[pkg] ?: 0L)) * 60L
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${UsageTracker.format(used)} de ${UsageTracker.format(allowed)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (used >= allowed) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { sendBonus(pkg, label, 15) }) { Text("+15 min") }
                                TextButton(onClick = { sendBonus(pkg, label, 30) }) { Text("+30") }
                            }
                        }
                    }
                }
            }

            // Horario
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Horario", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (!cfg.schedOn) "Bloqueos activos siempre"
                                else "Solo de ${Schedule.format(cfg.schedStart)} a ${Schedule.format(cfg.schedEnd)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = cfg.schedOn, onCheckedChange = {
                            cfg = cfg.copy(schedOn = it); dirty = true
                        })
                    }
                    if (cfg.schedOn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                pickTime(cfg.schedStart) { cfg = cfg.copy(schedStart = it); dirty = true }
                            }, modifier = Modifier.weight(1f)) { Text("Desde ${Schedule.format(cfg.schedStart)}") }
                            OutlinedButton(onClick = {
                                pickTime(cfg.schedEnd) { cfg = cfg.copy(schedEnd = it); dirty = true }
                            }, modifier = Modifier.weight(1f)) { Text("Hasta ${Schedule.format(cfg.schedEnd)}") }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                                val day = i + 1
                                FilterChip(
                                    selected = day in cfg.schedDays,
                                    onClick = { cfg = cfg.copy(schedDays = cfg.schedDays.toggle(day)); dirty = true },
                                    label = { Text(d) }
                                )
                            }
                        }
                    }
                    Text("Volver a bloquear", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "Al salir", 30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (sec, text) ->
                            FilterChip(
                                selected = cfg.grace == sec,
                                onClick = { cfg = cfg.copy(grace = sec); dirty = true },
                                label = { Text(text) }
                            )
                        }
                    }
                }
            }

            // Intentos fallidos
            if (intrusions.isNotEmpty()) {
                item {
                    SectionCard {
                        Text("Intentos de PIN fallidos", style = MaterialTheme.typography.titleMedium)
                        intrusions.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            // Copias de apps
            if (clones.isNotEmpty()) {
                item {
                    SectionCard {
                        Text("Apps duplicadas en su móvil", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${clones.joinToString(", ") { labelOf(it) }}: PIN y límite se aplican también a la copia. " +
                                "«Sin red» no puede quitarle internet a la copia; si hace falta, bórrala en su móvil " +
                                "y pon PIN a «Ajustes».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Lista de apps
            if (apps.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val filtered = if (query.isBlank()) apps
                else apps.filter { it.second.contains(query.trim(), ignoreCase = true) }
                items(filtered, key = { it.first }) { (pkg, label) ->
                    val limitMin = cfg.limits[pkg]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (pkg in clones) {
                                Text(
                                    "Tiene copia (PIN y límite incluidos)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = pkg in cfg.net,
                                    onClick = { cfg = cfg.copy(net = cfg.net.toggle(pkg)); dirty = true },
                                    label = { Text("Sin red") }
                                )
                                FilterChip(
                                    selected = pkg in cfg.lock,
                                    onClick = { cfg = cfg.copy(lock = cfg.lock.toggle(pkg)); dirty = true },
                                    label = { Text("PIN") }
                                )
                                FilterChip(
                                    selected = limitMin != null,
                                    onClick = { limitFor = pkg to label },
                                    label = {
                                        Text(if (limitMin == null) "Límite" else "⏱ ${UsageTracker.format(limitMin * 60L)}")
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (s != null) {
                item { Text("El móvil del hijo todavía no ha enviado su lista de apps.") }
            }

            // Ajustes del mando
            item {
                SectionCard {
                    Text("Este móvil", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                        Text("Cambiar el PIN de este móvil")
                    }
                    TextButton(onClick = { confirmUnlink = true }) { Text("Desvincular del móvil del hijo") }
                }
            }

            if (dirty) item { Box(Modifier.padding(bottom = 48.dp)) {} }
        }
    }

    limitFor?.let { (pkg, label) ->
        LimitDialog(
            appLabel = label,
            current = cfg.limits[pkg],
            onPick = { minutes ->
                cfg = cfg.copy(limits = minutes?.let { m -> cfg.limits + (pkg to m) } ?: (cfg.limits - pkg))
                dirty = true
                limitFor = null
            },
            onDismiss = { limitFor = null }
        )
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("¿Desvincular?") },
            text = { Text("Dejarás de controlar ese móvil. Sus bloqueos actuales se mantienen.") },
            confirmButton = {
                TextButton(onClick = {
                    ParentLink.unlink()
                    confirmUnlink = false
                    onUnlinked()
                }) { Text("Desvincular") }
            },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancelar") } }
        )
    }
}
