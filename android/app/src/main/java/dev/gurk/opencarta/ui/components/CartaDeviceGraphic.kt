package dev.gurk.opencarta.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.gurk.opencarta.ui.theme.CartaColors

@Composable
fun CartaDeviceGraphic(
    modifier: Modifier = Modifier,
    ledColors: List<Color> = listOf(CartaColors.Mint, CartaColors.Mint),
    ledsEnabled: Boolean = true,
    isHeating: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ledGlow")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryGlow = when {
        isHeating -> CartaColors.Danger
        ledsEnabled && ledColors.isNotEmpty() -> ledColors.first()
        else -> CartaColors.Mint.copy(alpha = 0.2f)
    }
    val secondaryGlow = when {
        isHeating -> CartaColors.Warning
        ledsEnabled && ledColors.size > 1 -> ledColors[1]
        else -> primaryGlow
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle ambient aura behind glass/base
        if (ledsEnabled || isHeating) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.22f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.45f, h * 0.48f),
                    radius = w * 0.6f
                ),
                radius = w * 0.6f,
                center = Offset(w * 0.45f, h * 0.48f)
            )
        }

        // Glass top cone
        val glassPath = Path().apply {
            moveTo(w * 0.37f, h * 0.08f)
            quadraticTo(w * 0.43f, h * 0.04f, w * 0.49f, h * 0.08f)
            lineTo(w * 0.62f, h * 0.28f)
            lineTo(w * 0.60f, h * 0.50f)
            lineTo(w * 0.25f, h * 0.50f)
            lineTo(w * 0.24f, h * 0.28f)
            close()
        }
        drawPath(
            path = glassPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF283438),
                    Color(0xFF141A1D),
                    Color(0xFF222E32)
                ),
                start = Offset(w * 0.24f, h * 0.1f),
                end = Offset(w * 0.62f, h * 0.5f)
            )
        )
        drawPath(
            path = glassPath,
            color = Color(0xFF47565C),
            style = Stroke(width = 2f)
        )

        // Inner glass percolator pillar
        drawRoundRect(
            color = Color(0xFF3A484D).copy(alpha = 0.65f),
            topLeft = Offset(w * 0.37f, h * 0.18f),
            size = Size(w * 0.12f, h * 0.28f),
            cornerRadius = CornerRadius(12f, 12f)
        )

        // Glowing glass base collar
        if (ledsEnabled || isHeating) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(primaryGlow.copy(alpha = 0.45f * pulse), secondaryGlow.copy(alpha = 0.45f * pulse))
                ),
                topLeft = Offset(w * 0.25f, h * 0.44f),
                size = Size(w * 0.35f, h * 0.06f)
            )
        }

        // Atomizer & carb cap on right
        drawRoundRect(
            color = Color(0xFF202427),
            topLeft = Offset(w * 0.63f, h * 0.36f),
            size = Size(w * 0.19f, h * 0.15f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = Color(0xFF343A3F),
            topLeft = Offset(w * 0.65f, h * 0.32f),
            size = Size(w * 0.15f, h * 0.06f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Main ergonomic body
        val bodyPath = Path().apply {
            moveTo(w * 0.23f, h * 0.50f)
            lineTo(w * 0.80f, h * 0.50f)
            quadraticTo(w * 0.85f, h * 0.58f, w * 0.71f, h * 0.66f)
            quadraticTo(w * 0.65f, h * 0.72f, w * 0.67f, h * 0.89f)
            lineTo(w * 0.21f, h * 0.89f)
            quadraticTo(w * 0.18f, h * 0.70f, w * 0.23f, h * 0.50f)
            close()
        }
        drawPath(
            path = bodyPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF23272A),
                    Color(0xFF131618),
                    Color(0xFF1D2124)
                ),
                start = Offset(w * 0.2f, h * 0.5f),
                end = Offset(w * 0.75f, h * 0.9f)
            )
        )
        drawPath(
            path = bodyPath,
            color = Color(0xFF343A3F),
            style = Stroke(width = 2f)
        )

        // Subtle curved grip lines
        drawLine(
            color = Color(0xFF2C3236),
            start = Offset(w * 0.22f, h * 0.64f),
            end = Offset(w * 0.64f, h * 0.75f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFF212629),
            start = Offset(w * 0.22f, h * 0.73f),
            end = Offset(w * 0.65f, h * 0.82f),
            strokeWidth = 2f
        )

        // Small "V" button emblem
        val vPath = Path().apply {
            moveTo(w * 0.34f, h * 0.54f)
            lineTo(w * 0.375f, h * 0.59f)
            lineTo(w * 0.41f, h * 0.54f)
        }
        drawPath(
            path = vPath,
            color = Color(0xFF8A9094),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Glowing LED base ring
        val baseRingColor = if (ledsEnabled || isHeating) primaryGlow else Color(0xFF2C3236)
        val baseRingColor2 = if (ledsEnabled || isHeating) secondaryGlow else Color(0xFF2C3236)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    baseRingColor.copy(alpha = if (ledsEnabled || isHeating) pulse else 1f),
                    baseRingColor2.copy(alpha = if (ledsEnabled || isHeating) pulse else 1f)
                )
            ),
            topLeft = Offset(w * 0.21f, h * 0.855f),
            size = Size(w * 0.46f, h * 0.025f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Foot base
        drawRoundRect(
            color = Color(0xFF0E1012),
            topLeft = Offset(w * 0.22f, h * 0.88f),
            size = Size(w * 0.44f, h * 0.035f),
            cornerRadius = CornerRadius(8f, 8f)
        )
    }
}

