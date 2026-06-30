package com.quorvia.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = QuorviaBlue,
    secondary = QuorviaSignal,
    surface = QuorviaSurface,
    onSurface = QuorviaInk,
)

@Composable
fun QuorviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        content = content,
    )
}

