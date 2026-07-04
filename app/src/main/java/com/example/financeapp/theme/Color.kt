package com.example.financeapp.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Light Colors
val LightPrimary = Color(0xFF005AC1)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD8E2FF)
val LightSecondary = Color(0xFF535E78)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFFDFBFF)
val LightSurface = Color(0xFFFDFBFF)
val LightOnSurface = Color(0xFF1A1B1E)

// Dark Colors
val DarkPrimary = Color(0xFFADC6FF)
val DarkOnPrimary = Color(0xFF002E69)
val DarkPrimaryContainer = Color(0xFF004494)
val DarkSecondary = Color(0xFFBBC6E4)
val DarkOnSecondary = Color(0xFF253048)
val DarkBackground = Color(0xFF1A1B1E)
val DarkSurface = Color(0xFF1A1B1E)
val DarkOnSurface = Color(0xFFE3E2E6)

// Neutral Accents
val AccentGold = Color(0xFFFFB300)
val AccentRed = Color(0xFFD32F2F)
val AccentGreen = Color(0xFF2E7D32)

@Preview(showBackground = true)
@Composable
fun ColorPalettePreview() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = listOf(
            "LightPrimary" to LightPrimary,
            "LightPrimaryContainer" to LightPrimaryContainer,
            "LightSecondary" to LightSecondary,
            "DarkPrimary" to DarkPrimary,
            "DarkPrimaryContainer" to DarkPrimaryContainer
        )
        colors.forEach { (name, color) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).background(color))
                Text(text = name)
            }
        }
    }
}
