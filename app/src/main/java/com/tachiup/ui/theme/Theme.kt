package com.tachiup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF9C6BFF)
private val PurpleDark = Color(0xFF7A4DD0)

private val DarkColors = darkColorScheme(
    primary = Purple,
    secondary = PurpleDark,
    background = Color(0xFF13111C),
    surface = Color(0xFF1E1B2E),
)

private val LightColors = lightColorScheme(
    primary = PurpleDark,
    secondary = Purple,
)

@Composable
fun TachiUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
