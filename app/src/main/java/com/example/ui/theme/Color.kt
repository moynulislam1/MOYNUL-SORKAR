package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Warm Family Hearth & Emerald Vault Palette
val EmeraldPrimary = Color(0xFF0F5C4D)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldPrimaryContainer = Color(0xFFCDEEE3)
val EmeraldOnPrimaryContainer = Color(0xFF062E25)

val TerracottaSecondary = Color(0xFFD96B43)
val TerracottaOnSecondary = Color(0xFFFFFFFF)
val TerracottaContainer = Color(0xFFFFDBD0)
val TerracottaOnContainer = Color(0xFF471808)

val GoldTertiary = Color(0xFFB37B2B)
val GoldOnTertiary = Color(0xFFFFFFFF)
val GoldContainer = Color(0xFFFCE7C3)
val GoldOnTertiaryContainer = Color(0xFF3A2404)

val LinenBackground = Color(0xFFFAF7F2)
val EspressoOnBackground = Color(0xFF1A1F1D)
val CreamSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFEDE8DF)
val MutedSlateText = Color(0xFF56635E)
val WarmOutline = Color(0xFFD6CEC2)

// Dark Theme Tokens
val PineNightBackground = Color(0xFF0C1513)
val PineNightSurface = Color(0xFF13201D)
val PineNightSurfaceVariant = Color(0xFF1C2E2A)
val MintDarkPrimary = Color(0xFF6CD6BA)
val MintDarkOnPrimary = Color(0xFF04372B)
val MintDarkContainer = Color(0xFF0B4F40)
val CoralDarkSecondary = Color(0xFFFFB59C)
val CoralDarkOnSecondary = Color(0xFF561F0C)
val WarmIvoryText = Color(0xFFEBE7E0)
val MutedSageText = Color(0xFFA7B5AF)
val PineOutline = Color(0xFF2D433E)

val ErrorCrimson = Color(0xFFBA1A1A)
val VerifiedGreen = Color(0xFF1B8755)
val OnlineDotGreen = Color(0xFF22C55E)
val CallEndRed = Color(0xFFD9383A)

val AvatarColorPresets = listOf(
    "#0F5C4D",
    "#D96B43",
    "#B37B2B",
    "#1B8755",
    "#2F6690",
    "#7A4E9D"
)

fun avatarColorFromHex(hex: String, fallback: Color = EmeraldPrimary): Color {
    return try {
        val clean = hex.trim()
        if (clean.startsWith("#") && (clean.length == 7 || clean.length == 9)) {
            Color(android.graphics.Color.parseColor(clean))
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

