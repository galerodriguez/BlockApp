@file:OptIn(ExperimentalMaterial3Api::class)

package com.blockapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppFilter(val label: String) {
    ALL("Todas"),
    CONFIGURED("Con ajustes"),
    NET("Sin red"),
    PIN("PIN"),
    LIMIT("Límite");

    companion object {
        fun of(name: String?): AppFilter = values().firstOrNull { it.name == name } ?: ALL
    }
}

enum class AppSort(val label: String) {
    NAME("A-Z"),
    MOST("Más usadas"),
    LEAST("Menos usadas");

    companion object {
        fun of(name: String?): AppSort = values().firstOrNull { it.name == name } ?: NAME
    }
}

/**
 * Filtra y ordena la lista de apps. Las apps con algún ajuste (Sin red, PIN o Límite)
 * van siempre arriba; dentro de cada grupo se aplica el orden elegido.
 */
fun <T> arrangeApps(
    items: List<T>,
    pkgOf: (T) -> String,
    labelOf: (T) -> String,
    query: String,
    filter: AppFilter,
    sort: AppSort,
    net: Set<String>,
    lock: Set<String>,
    limits: Map<String, Int>,
    usage: Map<String, Long>,
): List<T> {
    val q = query.trim()
    fun configured(p: String) = p in net || p in lock || p in limits

    val filtered = items.filter { item ->
        val p = pkgOf(item)
        val matches = q.isEmpty() || labelOf(item).contains(q, ignoreCase = true) || p.contains(q, ignoreCase = true)
        matches && when (filter) {
            AppFilter.ALL -> true
            AppFilter.CONFIGURED -> configured(p)
            AppFilter.NET -> p in net
            AppFilter.PIN -> p in lock
            AppFilter.LIMIT -> p in limits
        }
    }

    val configuredFirst: Comparator<T> = compareByDescending<T> { configured(pkgOf(it)) }
    val byName: Comparator<T> = compareBy<T> { labelOf(it).lowercase() }
    val byOrder: Comparator<T> = when (sort) {
        AppSort.NAME -> byName
        AppSort.MOST -> compareByDescending<T> { usage[pkgOf(it)] ?: 0L }.then(byName)
        AppSort.LEAST -> compareBy<T> { usage[pkgOf(it)] ?: 0L }.then(byName)
    }
    return filtered.sortedWith(configuredFirst.then(byOrder))
}

/** Buscador + filtros + orden de la lista de apps. */
@Composable
fun AppListControls(
    query: String,
    onQuery: (String) -> Unit,
    filter: AppFilter,
    onFilter: (AppFilter) -> Unit,
    sort: AppSort,
    onSort: (AppSort) -> Unit,
    counts: Map<AppFilter, Int>,
    usageNote: String?,
    onUsageNoteClick: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Apps", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Buscar app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppFilter.values().forEach { f ->
                val n = counts[f]
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(if (n != null && f != AppFilter.ALL) "${f.label} ($n)" else f.label) }
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ordenar:", style = MaterialTheme.typography.bodyMedium)
            AppSort.values().forEach { s ->
                FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(s.label) })
            }
        }
        if (usageNote != null) {
            if (onUsageNoteClick != null) {
                TextButton(onClick = onUsageNoteClick) { Text(usageNote) }
            } else {
                Text(
                    usageNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Cuántas apps hay en cada filtro (para mostrarlo en los botones). */
fun filterCounts(net: Set<String>, lock: Set<String>, limits: Map<String, Int>): Map<AppFilter, Int> = mapOf(
    AppFilter.CONFIGURED to (net + lock + limits.keys).size,
    AppFilter.NET to net.size,
    AppFilter.PIN to lock.size,
    AppFilter.LIMIT to limits.size,
)
