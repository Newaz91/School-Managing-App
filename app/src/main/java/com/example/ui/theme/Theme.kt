package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = TerracottaSecondary,
    onSecondary = OnTerracottaSecondary,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = OchreTertiary,
    onTertiary = OnOchreTertiary,
    tertiaryContainer = OchreContainer,
    onTertiaryContainer = OnOchreContainer,
    background = LedgerBackground,
    onBackground = LedgerOnSurface,
    surface = LedgerSurface,
    onSurface = LedgerOnSurface,
    surfaceVariant = LedgerSurfaceContainer,
    onSurfaceVariant = LedgerOnSurfaceVariant,
    surfaceContainerLowest = OnEmeraldPrimary,
    surfaceContainerLow = LedgerSurfaceLow,
    surfaceContainer = LedgerSurfaceContainer,
    surfaceContainerHigh = LedgerSurfaceHigh,
    outline = LedgerOutline,
    outlineVariant = LedgerOutlineVariant,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = OnEmeraldDarkPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = OnEmeraldDarkContainer,
    secondary = TerracottaDarkSecondary,
    onSecondary = OnTerracottaDarkSecondary,
    secondaryContainer = TerracottaDarkContainer,
    onSecondaryContainer = OnTerracottaDarkContainer,
    tertiary = OchreDarkTertiary,
    onTertiary = OnOchreDarkTertiary,
    tertiaryContainer = OchreDarkContainer,
    onTertiaryContainer = OnOchreDarkContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ShikkhaKhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
