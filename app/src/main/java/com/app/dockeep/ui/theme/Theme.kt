package com.app.dockeep.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun DockeepTheme(darkTheme: Boolean = false, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = if(darkTheme) Color(0xFF396EAC) else Color(0xFF98644B),
        onPrimary = Color.White,
        primaryContainer = if(darkTheme) Color(0xFFE5EFFA) else Color(0xFFF0E3D5),
        background = if(darkTheme) Color(0xFFF8FAFD) else Color(0xFFFAF7F1),
        surface = if(darkTheme) Color(0xFFF8FAFD) else Color(0xFFFAF7F1),
        surfaceVariant = if(darkTheme) Color(0xFFEDF2F8) else Color(0xFFF0EAE1),
        onSurface = Color(0xFF302E2C),
        onSurfaceVariant = Color(0xFF706B65),
        secondaryContainer = if(darkTheme) Color(0xFFE5EFFA) else Color(0xFFF0E3D5)
    )
    val view = LocalView.current
    if(!view.isInEditMode) SideEffect { (view.context as? Activity)?.window?.let {
        WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = true
    } }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
