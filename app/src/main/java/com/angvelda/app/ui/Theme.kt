package com.angvelda.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.angvelda.app.Prefs

@Composable
fun AngVeldaTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) { 0 -> isSystemInDarkTheme(); 1 -> true; else -> false }
    val accent = Color(prefs.accent)
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
    val scheme = if (dark) {
        val base = Color(0xFF14101C)
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.28f).compositeOver(base), onPrimaryContainer = Color(0xFFF1ECFF),
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.30f).compositeOver(base), onSecondaryContainer = Color(0xFFF1ECFF),
            tertiary = Color(0xFFC4B5FD),
            background = Color(0xFF0D0A14), onBackground = Color(0xFFEAE6F5),
            surface = Color(0xFF14101C), onSurface = Color(0xFFEAE6F5),
            surfaceVariant = Color(0xFF211B2E), onSurfaceVariant = Color(0xFFB9B1CC),
            surfaceContainerLowest = Color(0xFF0A0710), surfaceContainerLow = Color(0xFF110D19),
            surfaceContainer = Color(0xFF171220), surfaceContainerHigh = Color(0xFF1D1728),
            surfaceContainerHighest = Color(0xFF241D32),
            outline = Color(0xFF6E6585), outlineVariant = Color(0xFF3A3150), error = Color(0xFFFF6B7A)
        )
    } else {
        val base = Color(0xFFF7F4FD)
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.18f).compositeOver(base), onPrimaryContainer = Color(0xFF1B1530),
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.20f).compositeOver(base), onSecondaryContainer = Color(0xFF1B1530),
            background = Color(0xFFF7F4FD), onBackground = Color(0xFF1B1530),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B1530),
            surfaceVariant = Color(0xFFEDE8F8), onSurfaceVariant = Color(0xFF4C4663),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9F6FE),
            surfaceContainer = Color(0xFFF2EEFA), surfaceContainerHigh = Color(0xFFECE6F7),
            surfaceContainerHighest = Color(0xFFE6E0F3),
            outline = Color(0xFF857D9C), outlineVariant = Color(0xFFCFC8E0), error = Color(0xFFBA1A2B)
        )
    }
    val d = LocalDensity.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * prefs.textScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
