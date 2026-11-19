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
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = TerracottaOnSecondary,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = TerracottaOnContainer,
    tertiary = GoldTertiary,
    onTertiary = GoldOnTertiary,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = GoldOnTertiaryContainer,
    background = LinenBackground,
    onBackground = EspressoOnBackground,
    surface = CreamSurface,
    onSurface = EspressoOnBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = WarmOutline,
    error = ErrorCrimson
)

private val DarkColorScheme = darkColorScheme(
    primary = MintDarkPrimary,
    onPrimary = MintDarkOnPrimary,
    primaryContainer = MintDarkContainer,
    onPrimaryContainer = EmeraldPrimaryContainer,
    secondary = CoralDarkSecondary,
    onSecondary = CoralDarkOnSecondary,
    secondaryContainer = TerracottaOnContainer,
    onSecondaryContainer = TerracottaContainer,
    tertiary = GoldContainer,
    onTertiary = GoldOnTertiaryContainer,
    background = PineNightBackground,
    onBackground = WarmIvoryText,
    surface = PineNightSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = PineNightSurfaceVariant,
    onSurfaceVariant = MutedSageText,
    outline = PineOutline,
    error = ErrorCrimson
)

val FamilyAppsShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FamilyAppsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FamilyAppsTypography,
        shapes = FamilyAppsShapes,
        content = content
    )
}
