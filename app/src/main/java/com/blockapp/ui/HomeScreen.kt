@file:OptIn(ExperimentalMaterial3Api::class)

package com.blockapp.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.blockapp.data.Prefs
import com.blockapp.data.Schedule
import com.blockapp.lock.UsageTracker
import com.blockapp.update.UpdateCard
import com.blockapp.data.Apps
import com.blockapp.data.Usage
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blockapp.remote.RemoteSync
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import com.blockapp.security.Biometric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

private fun Set<String>.toggle(item: String) = if (item in this) this - item else this + item

private fun Set<Int>.toggleDay(day: Int) = if (day in this) this - day else this + day

private suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.Default) {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map {
            AppEntry(
                pkg = it.packageName,
                label = pm.getApplicationLabel(it).toString(),
                icon = pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun HomeScreen(
    a11yOn: Boolean,
    firewallOn: Boolean,
    onOpenA11y: () -> Unit,
    onFirewallToggle: (Boolean) -> Unit,
    adminOn: Boolean,
    onAdminToggle: (Boolean) -> Unit,
    onBlockedNetChanged: () -> Unit,
    onChangePin: () -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var blocked by remember { mutableStateOf(Prefs.blockedNet) }
    var locked by remember { mutableStateOf(Prefs.lockedApps) }
    var biometric by remember { mutableStateOf(Prefs.biometricEnabled) }
    var grace by remember { mutableIntStateOf(Prefs.graceSeconds) }
    var intrusions by remember { mutableStateOf(Prefs.intrusions) }
    var showLog by remember { mutableStateOf(false) }
    val bioHardware = remember { Biometric.hardwareReady(context) }
    var limits by remember { mutableStateOf(Prefs.limits) }
    var usage by remember { mutableStateOf(UsageTracker.snapshot()) }
    var limitFor by remember { mutableStateOf<AppEntry?>(null) }
    var remoteCode by remember { mutableStateOf(RemoteSync.code()) }
    var confirmNewCode by remember { mutableStateOf(false) }
    val clones = remember { Apps.clonedPackages(context) }
    var filter by remember { mutableStateOf(AppFilter.of(Prefs.listFilter)) }
    var sort by remember { mutableStateOf(AppSort.of(Prefs.listSort)) }

    // Uso de los últimos 7 días (permiso «Acceso al uso»); se recarga al volver a la app
    var weekly by remember { mutableStateOf<Map<String, Long>?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        weekly = withContext(Dispatchers.IO) { Usage.lastDays(context) }
    }

    // Refresca el uso de hoy cada 30 s mientras la pantalla está abierta
    LaunchedEffect(Unit) {
        while (true) {
            usage = UsageTracker.snapshot()
            delay(30_000)
        }
    }

    var schedOn by remember { mutableStateOf(Prefs.scheduleEnabled) }
    var schedStart by remember { mutableIntStateOf(Prefs.scheduleStart) }
    var schedEnd by remember { mutableIntStateOf(Prefs.scheduleEnd) }
    var schedDays by remember { mutableStateOf(Prefs.scheduleDays) }

    // El firewall se recarga para aplicar el horario nuevo al instante
    fun scheduleChanged() = onBlockedNetChanged()

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    LaunchedEffect(Unit) { apps = loadApps(context) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("BlockApp", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }

            item { UpdateCard() }

            // Estado del bloqueo con PIN
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bloqueo con PIN", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (a11yOn) "Activo · ${locked.size} apps protegidas"
                                else "Activa «BlockApp» en Accesibilidad",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!a11yOn) Button(onClick = onOpenA11y) { Text("Activar") }
                    }
                }
            }

            // Estado del firewall
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Firewall", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (firewallOn) "Activo · ${blocked.size} apps sin internet" else "Desactivado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = firewallOn, onCheckedChange = onFirewallToggle)
                    }
                }
            }

            // Uso de hoy de las apps con límite
            if (limits.isNotEmpty()) {
                item {
                    SectionCard {
                        Text("Uso de hoy", style = MaterialTheme.typography.titleMedium)
                        limits.entries.sortedBy { it.key }.forEach { (pkg, limitMin) ->
                            val name = apps?.firstOrNull { it.pkg == pkg }?.label ?: pkg
                            val used = usage[pkg] ?: 0L
                            val allowed = limitMin * 60L
                            val over = used >= allowed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${UsageTracker.format(used)} / ${UsageTracker.format(allowed)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (over) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (used.toFloat() / allowed).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "Cuando se agota, la app se bloquea hasta mañana. Con tu PIN das ${UsageTracker.BONUS_MINUTES} min más.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                if (!schedOn) "Bloqueos activos siempre"
                                else "Solo de ${Schedule.format(schedStart)} a ${Schedule.format(schedEnd)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = schedOn, onCheckedChange = {
                            schedOn = it
                            Prefs.scheduleEnabled = it
                            scheduleChanged()
                        })
                    }
                    if (schedOn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                pickTime(schedStart) { schedStart = it; Prefs.scheduleStart = it; scheduleChanged() }
                            }, modifier = Modifier.weight(1f)) { Text("Desde ${Schedule.format(schedStart)}") }
                            OutlinedButton(onClick = {
                                pickTime(schedEnd) { schedEnd = it; Prefs.scheduleEnd = it; scheduleChanged() }
                            }, modifier = Modifier.weight(1f)) { Text("Hasta ${Schedule.format(schedEnd)}") }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { i, d ->
                                val day = i + 1
                                FilterChip(
                                    selected = day in schedDays,
                                    onClick = {
                                        schedDays = schedDays.toggleDay(day)
                                        Prefs.scheduleDays = schedDays
                                        scheduleChanged()
                                    },
                                    label = { Text(d) }
                                )
                            }
                        }
                        Text(
                            "Si «Hasta» es menor que «Desde», el horario cruza la medianoche (ej. 22:00 a 07:00).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Anti-desinstalación
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Impedir desinstalación", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (adminOn) "Activo · protege también «Ajustes» con PIN"
                                else "Cualquiera podría desinstalar BlockApp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = adminOn, onCheckedChange = onAdminToggle)
                    }
                }
            }

            // Control desde el móvil del padre
            item {
                SectionCard {
                    Text("Control desde el móvil del padre", style = MaterialTheme.typography.titleMedium)
                    val code = remoteCode
                    if (code == null) {
                        Text(
                            "Permite cambiar bloqueos, horarios y límites desde otro móvil. " +
                                "Los datos viajan cifrados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = {
                            remoteCode = RemoteSync.pair()
                            RemoteSync.syncSoon(context)
                        }) { Text("Vincular con el móvil del padre") }
                    } else {
                        Text(
                            "En el móvil del padre: instala BlockApp, elige «Controlar el móvil de un hijo» " +
                                "y escribe este código:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            code,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        if (!a11yOn) {
                            Text(
                                "Necesita el «Bloqueo con PIN» (Accesibilidad) activado para recibir los cambios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmNewCode = true }) { Text("Código nuevo") }
                            TextButton(onClick = {
                                RemoteSync.unpair()
                                remoteCode = null
                            }) { Text("Desvincular") }
                        }
                    }
                }
            }

            // Copias de apps (Dual Messenger, apps duales…)
            if (clones.isNotEmpty()) {
                item {
                    SectionCard {
                        val names = clones.map { pkg -> apps?.firstOrNull { it.pkg == pkg }?.label ?: pkg }
                        Text("Apps duplicadas detectadas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Copias: ${names.joinToString(", ")}.\n" +
                                "• PIN y límite diario se aplican también a la copia (el tiempo de las dos se suma).\n" +
                                "• «Sin red» NO puede quitarle internet a la copia: Android no lo permite sin root. " +
                                "Si hace falta, borra la copia (en Samsung: Ajustes › Funciones avanzadas › Dual Messenger) " +
                                "y pon PIN a «Ajustes» para que no se pueda volver a crear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Ajustes
            item {
                SectionCard {
                    Text("Ajustes", style = MaterialTheme.typography.titleMedium)
                    if (bioHardware) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Desbloqueo con huella", Modifier.weight(1f))
                            Switch(checked = biometric, onCheckedChange = {
                                biometric = it
                                Prefs.biometricEnabled = it
                            })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Volver a bloquear", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "Al salir", 30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (sec, text) ->
                            FilterChip(
                                selected = grace == sec,
                                onClick = { grace = sec; Prefs.graceSeconds = sec },
                                label = { Text(text) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Intentos fallidos: ${intrusions.size}", Modifier.weight(1f))
                        if (intrusions.isNotEmpty()) {
                            TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Ocultar" else "Ver") }
                        }
                    }
                    if (showLog) {
                        intrusions.take(20).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            Prefs.clearIntrusions()
                            intrusions = emptyList()
                            showLog = false
                        }) { Text("Borrar registro") }
                    }
                    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) { Text("Cambiar PIN parental") }
                }
            }

            val sortUsage = weekly ?: usage
            val usageLabel = if (weekly != null) "${Usage.DAYS} días" else "Hoy"
            item {
                AppListControls(
                    query = query,
                    onQuery = { query = it },
                    filter = filter,
                    onFilter = { filter = it; Prefs.listFilter = it.name },
                    sort = sort,
                    onSort = { sort = it; Prefs.listSort = it.name },
                    counts = filterCounts(blocked, locked, limits),
                    usageNote = if (weekly == null) "Permitir «Acceso al uso» para ordenar por el uso de la semana" else null,
                    onUsageNoteClick = if (weekly == null) onOpenUsageAccess else null,
                )
            }

            val list = apps
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val filtered = arrangeApps(
                    items = list,
                    pkgOf = { it.pkg },
                    labelOf = { it.label },
                    query = query,
                    filter = filter,
                    sort = sort,
                    net = blocked,
                    lock = locked,
                    limits = limits,
                    usage = sortUsage,
                )
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No hay apps con este filtro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(filtered, key = { it.pkg }) { app ->
                    AppRow(
                        app = app,
                        netBlocked = app.pkg in blocked,
                        isLocked = app.pkg in locked,
                        limitMin = limits[app.pkg],
                        usedSeconds = usage[app.pkg] ?: 0L,
                        hasClone = app.pkg in clones,
                        usageText = sortUsage[app.pkg]?.let { "$usageLabel: ${UsageTracker.format(it)}" },
                        onLimitClick = { limitFor = app },
                        onToggleNet = {
                            blocked = blocked.toggle(app.pkg)
                            Prefs.blockedNet = blocked
                            onBlockedNetChanged()
                        },
                        onToggleLock = {
                            locked = locked.toggle(app.pkg)
                            Prefs.lockedApps = locked
                        }
                    )
                }
            }
        }
    }

    if (confirmNewCode) {
        AlertDialog(
            onDismissRequest = { confirmNewCode = false },
            title = { Text("¿Crear un código nuevo?") },
            text = { Text("El móvil del padre dejará de controlar este móvil hasta que escriba el código nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    remoteCode = RemoteSync.pair()
                    RemoteSync.syncSoon(context)
                    confirmNewCode = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { confirmNewCode = false }) { Text("Cancelar") } }
        )
    }

    limitFor?.let { app ->
        LimitDialog(
            appLabel = app.label,
            current = limits[app.pkg],
            onPick = { minutes ->
                limits = minutes?.let { m -> limits + (app.pkg to m) } ?: (limits - app.pkg)
                Prefs.limits = limits
                limitFor = null
            },
            onDismiss = { limitFor = null }
        )
    }
}

@Composable
fun LimitDialog(appLabel: String, current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf<Pair<Int?, String>>(
        null to "Sin límite",
        15 to "15 min",
        30 to "30 min",
        60 to "1 hora",
        120 to "2 horas",
        180 to "3 horas"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Límite diario · $appLabel") },
        text = {
            Column {
                options.forEach { (minutes, text) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(minutes) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == minutes, onClick = { onPick(minutes) })
                        Text(text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    netBlocked: Boolean,
    isLocked: Boolean,
    limitMin: Int?,
    usedSeconds: Long,
    hasClone: Boolean,
    usageText: String?,
    onLimitClick: () -> Unit,
    onToggleNet: () -> Unit,
    onToggleLock: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (usageText != null && limitMin == null) {
                        Text(
                            usageText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasClone) {
                        Text(
                            "También la copia (PIN y límite)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (limitMin != null) {
                        val over = usedSeconds >= limitMin * 60L
                        Text(
                            "Hoy: ${UsageTracker.format(usedSeconds)} de ${UsageTracker.format(limitMin * 60L)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                Modifier.padding(start = 52.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = netBlocked, onClick = onToggleNet, label = { Text("Sin red") })
                FilterChip(selected = isLocked, onClick = onToggleLock, label = { Text("PIN") })
                FilterChip(
                    selected = limitMin != null,
                    onClick = onLimitClick,
                    label = { Text(if (limitMin == null) "Límite" else "⏱ ${UsageTracker.format(limitMin * 60L)}") }
                )
            }
        }
    }
}
