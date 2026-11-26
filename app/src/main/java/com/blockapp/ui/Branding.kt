@file:OptIn(ExperimentalMaterial3Api::class)

package com.blockapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockapp.R

/** Logo + título de cabecera. */
@Composable
fun BrandHeader(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Selector de modo (automático/claro/oscuro) y color. */
@Composable
fun AppearancePicker() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Apariencia", style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.values().forEach { m ->
                FilterChip(
                    selected = ThemeSettings.mode == m,
                    onClick = { ThemeSettings.setMode(m) },
                    label = { Text(m.label) }
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeColor.available().forEach { c ->
                val selected = ThemeSettings.color == c
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ring = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                    Box(
                        Modifier.size(36.dp).then(ring).padding(4.dp).clip(CircleShape)
                            .background(
                                if (c == ThemeColor.WALLPAPER) MaterialTheme.colorScheme.tertiary else c.swatch
                            )
                            .clickable { ThemeSettings.setColor(c) }
                    )
                    Text(c.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
