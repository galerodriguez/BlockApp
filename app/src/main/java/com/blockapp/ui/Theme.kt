package com.blockapp.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.blockapp.data.Prefs

enum class ThemeMode(val label: String) { SYSTEM("Automático"), LIGHT("Claro"), DARK("Oscuro") }

/** Colores disponibles («skins»). */
enum class ThemeColor(
    val label: String,
    val swatch: Color,
    val light: Palette?,
    val dark: Palette?,
) {
    BRAND(
        "Índigo", Color(0xFF4F46E5),
        Palette(0xFF4F46E5, 0xFFFFFFFF, 0xFFE0E0FF, 0xFF110566),
        Palette(0xFFC0C1FF, 0xFF1E1A8F, 0xFF3730A3, 0xFFE0E0FF),
    ),
    BLUE(
        "Azul", Color(0xFF1E5BD7),
        Palette(0xFF1E5BD7, 0xFFFFFFFF, 0xFFD9E2FF, 0xFF001946),
        Palette(0xFFB0C6FF, 0xFF002C70, 0xFF00429D, 0xFFD9E2FF),
    ),
    GREEN(
        "Verde", Color(0xFF1B6D3A),
        Palette(0xFF1B6D3A, 0xFFFFFFFF, 0xFFA4F4B4, 0xFF00210C),
        Palette(0xFF89D79A, 0xFF003918, 0xFF005227, 0xFFA4F4B4),
    ),
    VIOLET(
        "Violeta", Color(0xFF7E3FB8),
        Palette(0xFF7E3FB8, 0xFFFFFFFF, 0xFFF2DAFF, 0xFF2D0050),
        Palette(0xFFE0B6FF, 0xFF4A0080, 0xFF64249E, 0xFFF2DAFF),
    ),
    ORANGE(
        "Naranja", Color(0xFF9A4600),
        Palette(0xFF9A4600, 0xFFFFFFFF, 0xFFFFDBC8, 0xFF321200),
        Palette(0xFFFFB68B, 0xFF522300, 0xFF753400, 0xFFFFDBC8),
    ),
    RED(
        "Rojo", Color(0xFFB3261E),
        Palette(0xFFB3261E, 0xFFFFFFFF, 0xFFF9DEDC, 0xFF410E0B),
        Palette(0xFFF2B8B5, 0xFF601410, 0xFF8C1D18, 0xFFF9DEDC),
    ),
    WALLPAPER("Fondo de pantalla", Color(0xFF888888), null, null);

    companion object {
        fun available(): List<ThemeColor> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) values().toList()
            else values().filter { it != WALLPAPER }
    }
}

class Palette(primary: Long, onPrimary: Long, container: Long, onContainer: Long) {
    val primary = Color(primary)
    val onPrimary = Color(onPrimary)
    val container = Color(container)
    val onContainer = Color(onContainer)
}

/** Ajustes de apariencia; al cambiarlos, toda la app se repinta al instante. */
object ThemeSettings {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
    var color by mutableStateOf(ThemeColor.BRAND)
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        mode = ThemeMode.values().firstOrNull { it.name == Prefs.themeMode } ?: ThemeMode.SYSTEM
        color = ThemeColor.values().firstOrNull { it.name == Prefs.themeColor } ?: ThemeColor.BRAND
    }

    fun changeMode(m: ThemeMode) {
        mode = m
        Prefs.themeMode = m.name
    }

    fun changeColor(c: ThemeColor) {
        color = c
        Prefs.themeColor = c.name
    }
}

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.container,
        onPrimaryContainer = p.onContainer,
        secondaryContainer = p.container,
        onSecondaryContainer = p.onContainer,
        inversePrimary = p.primary,
        surfaceTint = p.primary,
    )
}

@Composable
fun BlockAppTheme(content: @Composable () -> Unit) {
    ThemeSettings.load()
    val dark = when (ThemeSettings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val c = ThemeSettings.color
    val colors = if (c == ThemeColor.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val palette = (if (dark) c.dark else c.light) ?: (if (dark) ThemeColor.BRAND.dark!! else ThemeColor.BRAND.light!!)
        scheme(palette, dark)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
