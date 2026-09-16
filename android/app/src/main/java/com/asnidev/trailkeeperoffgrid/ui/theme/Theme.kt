package com.asnidev.trailkeeperoffgrid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.asnidev.trailkeeperoffgrid.data.ThemePrefs

/** One accent choice, with the primary/on-primary pair for each base mode
 * (a light-mode accent usually needs to be darker than its dark-mode twin
 * to keep enough contrast against a pale background). */
data class AccentSwatch(
    val key: String,
    val label: String,
    val swatch: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryLight: Color,
    val onPrimaryLight: Color,
)

// Moss-on-birch is the app's original identity; the rest are alternatives
// built the same way, one darker/lighter pair each.
val ACCENT_SWATCHES = listOf(
    AccentSwatch(
        key = "moss", label = "Moss",
        swatch = Color(0xFF9CC17F),
        primaryDark = Color(0xFF9CC17F), onPrimaryDark = Color(0xFF16210D),
        primaryLight = Color(0xFF4C6B3C), onPrimaryLight = Color(0xFFFFFFFF),
    ),
    AccentSwatch(
        key = "slate", label = "Slate",
        swatch = Color(0xFF7FA8C1),
        primaryDark = Color(0xFF7FA8C1), onPrimaryDark = Color(0xFF0D1B21),
        primaryLight = Color(0xFF2F6D7A), onPrimaryLight = Color(0xFFFFFFFF),
    ),
    AccentSwatch(
        key = "amber", label = "Amber",
        swatch = Color(0xFFD6A64B),
        primaryDark = Color(0xFFD6A64B), onPrimaryDark = Color(0xFF241C0A),
        primaryLight = Color(0xFF8A6A1E), onPrimaryLight = Color(0xFFFFFFFF),
    ),
    AccentSwatch(
        key = "clay", label = "Clay",
        swatch = Color(0xFFE0916E),
        primaryDark = Color(0xFFE0916E), onPrimaryDark = Color(0xFF2A1509),
        primaryLight = Color(0xFF9C4A2E), onPrimaryLight = Color(0xFFFFFFFF),
    ),
    AccentSwatch(
        key = "plum", label = "Plum",
        swatch = Color(0xFFB98BC9),
        primaryDark = Color(0xFFB98BC9), onPrimaryDark = Color(0xFF1F1424),
        primaryLight = Color(0xFF6B3F7A), onPrimaryLight = Color(0xFFFFFFFF),
    ),
)

fun accentSwatch(key: String): AccentSwatch =
    ACCENT_SWATCHES.firstOrNull { it.key == key } ?: ACCENT_SWATCHES.first()

private fun darkScheme(accent: AccentSwatch): ColorScheme = darkColorScheme(
    primary = accent.primaryDark,
    onPrimary = accent.onPrimaryDark,
    secondary = Color(0xFF98A188),
    background = Color(0xFF141712),
    surface = Color(0xFF1E241C),
    surfaceVariant = Color(0xFF2C3327),
    onBackground = Color(0xFFE7EADD),
    onSurface = Color(0xFFE7EADD),
    onSurfaceVariant = Color(0xFF98A188),
    outline = Color(0xFF3B4433),
    error = Color(0xFFE27A5C),
    onError = Color(0xFF16210D),
)

private fun lightScheme(accent: AccentSwatch): ColorScheme = lightColorScheme(
    primary = accent.primaryLight,
    onPrimary = accent.onPrimaryLight,
    secondary = Color(0xFF5B6350),
    background = Color(0xFFF5F3EA),
    surface = Color(0xFFEEEBE0),
    surfaceVariant = Color(0xFFDEDACB),
    onBackground = Color(0xFF1E241C),
    onSurface = Color(0xFF1E241C),
    onSurfaceVariant = Color(0xFF4A5240),
    outline = Color(0xFFC7C2AE),
    error = Color(0xFFB23B3B),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun TrailkeeperTheme(content: @Composable () -> Unit) {
    val mode by ThemePrefs.modeFlow().collectAsState(initial = ThemePrefs.mode())
    val accentKey by ThemePrefs.accentFlow().collectAsState(initial = ThemePrefs.accent())
    val useDark = when (mode) {
        ThemePrefs.Mode.DARK -> true
        ThemePrefs.Mode.LIGHT -> false
        ThemePrefs.Mode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = accentSwatch(accentKey)
    val colorScheme = if (useDark) darkScheme(accent) else lightScheme(accent)

    MaterialTheme(colorScheme = colorScheme) {
        // Surface sets LocalContentColor; without it, uncoloured Text falls
        // back to Compose's hardcoded black. (Same note as SharpRight.)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
