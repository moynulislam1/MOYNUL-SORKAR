package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

@Composable
fun FamilyWelcomeArtwork(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val container = MaterialTheme.colorScheme.primaryContainer

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
    ) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = 0.14f),
                    container.copy(alpha = 0.65f),
                    secondary.copy(alpha = 0.14f)
                )
            ),
            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
            size = size
        )

        // Subtle decorative orbits
        drawCircle(
            color = primary.copy(alpha = 0.10f),
            radius = h * 0.62f,
            center = Offset(w * 0.85f, h * 0.25f)
        )
        drawCircle(
            color = secondary.copy(alpha = 0.12f),
            radius = h * 0.44f,
            center = Offset(w * 0.16f, h * 0.85f)
        )

        // Stylized cozy home roof + heart bubble in center-left
        val cx = w * 0.26f
        val cy = h * 0.52f
        val housePath = Path().apply {
            moveTo(cx, cy - 36.dp.toPx())
            lineTo(cx - 38.dp.toPx(), cy - 4.dp.toPx())
            lineTo(cx - 28.dp.toPx(), cy - 4.dp.toPx())
            lineTo(cx - 28.dp.toPx(), cy + 30.dp.toPx())
            lineTo(cx + 28.dp.toPx(), cy + 30.dp.toPx())
            lineTo(cx + 28.dp.toPx(), cy - 4.dp.toPx())
            lineTo(cx + 38.dp.toPx(), cy - 4.dp.toPx())
            close()
        }
        drawPath(housePath, color = primary)

        // Warm glowing doorway / hearth
        drawRoundRect(
            color = secondary,
            topLeft = Offset(cx - 10.dp.toPx(), cy + 4.dp.toPx()),
            size = Size(20.dp.toPx(), 26.dp.toPx()),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )

        // Floating message bubbles on the right
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(w * 0.48f, h * 0.22f),
            size = Size(w * 0.36f, 28.dp.toPx()),
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
        )
        drawRoundRect(
            color = primary.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.52f, h * 0.22f + 10.dp.toPx()),
            size = Size(w * 0.22f, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        drawRoundRect(
            color = secondary,
            topLeft = Offset(w * 0.56f, h * 0.54f),
            size = Size(w * 0.34f, 30.dp.toPx()),
            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.60f, h * 0.54f + 11.dp.toPx()),
            size = Size(w * 0.20f, 8.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Gold verification spark dot
        drawCircle(
            color = tertiary,
            radius = 6.dp.toPx(),
            center = Offset(w * 0.45f, h * 0.72f)
        )
    }
}
