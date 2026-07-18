package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = OkxGreen,
    secondary = MutedText,
    tertiary = ValueRed,
    background = PureBlack,
    surface = DarkGreyCard,
    onPrimary = PureBlack,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme = DarkColorScheme // Keep dark mode identical for consistent branding

@Composable
fun MyApplicationTheme(
  primaryColor: Color = OkxGreen,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme.copy(
    primary = primaryColor
  )
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
