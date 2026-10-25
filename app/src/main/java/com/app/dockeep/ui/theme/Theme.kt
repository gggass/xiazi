package com.app.dockeep.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Stored DARK = blue, LIGHT = warm white. Both are light themes.
private fun vaultColors(blue: Boolean): ColorScheme {
    fun tone(cool: Long, warm: Long) = Color(if (blue) cool else warm)
    return lightColorScheme(
        primary = tone(0xFF2864C5, 0xFF8C6048), onPrimary = Color.White,
        primaryContainer = tone(0xFFE8F1FF, 0xFFF3E9DE),
        onPrimaryContainer = tone(0xFF214D89, 0xFF604330),
        inversePrimary = tone(0xFFB3D0FF, 0xFFE7C0A5),
        secondary = tone(0xFF53739C, 0xFF786858), onSecondary = Color.White,
        secondaryContainer = tone(0xFFEDF4FF, 0xFFF3EBE1),
        onSecondaryContainer = tone(0xFF254C80, 0xFF604330),
        tertiary = tone(0xFF386C86, 0xFF7B7052), onTertiary = Color.White,
        tertiaryContainer = tone(0xFFEAF4FA, 0xFFF2EEDF),
        onTertiaryContainer = tone(0xFF294658, 0xFF51492F),
        background = tone(0xFFF7F9FD, 0xFFFAF7F1),
        onBackground = tone(0xFF24354B, 0xFF352E28),
        surface = Color.White, onSurface = tone(0xFF24354B, 0xFF352E28),
        surfaceVariant = tone(0xFFF0F5FC, 0xFFF4EFE7),
        onSurfaceVariant = tone(0xFF5B6C82, 0xFF796E62),
        surfaceTint = tone(0xFF2864C5, 0xFF8C6048),
        outline = tone(0xFF8292A8, 0xFF998D7F),
        outlineVariant = tone(0xFFE2EAF5, 0xFFE8DFD3),
        inverseSurface = tone(0xFF28374B, 0xFF3D352D), inverseOnSurface = Color(0xFFF6F8FC),
        surfaceBright = Color.White, surfaceDim = tone(0xFFE8EEF7, 0xFFEBE3D8),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tone(0xFFF7F9FD, 0xFFFAF7F1),
        surfaceContainer = tone(0xFFF0F5FC, 0xFFF4EFE7),
        surfaceContainerHigh = tone(0xFFEAF2FD, 0xFFF1E8DD),
        surfaceContainerHighest = tone(0xFFE3EDFA, 0xFFECE1D4)
    )
}

@Composable
fun DockeepTheme(darkTheme: Boolean = true, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = remember(darkTheme) { vaultColors(darkTheme) }
    val view = LocalView.current
    if(!view.isInEditMode) SideEffect { (view.context as? Activity)?.window?.let {
        WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = true
    } }
    MaterialTheme(colorScheme = colorScheme, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
