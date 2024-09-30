package io.livekit.android.example.voiceassistant.ui.noise

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val springAnimation = spring<Float>(
    stiffness = Spring.StiffnessHigh
)

/**
 * Draws bars evenly split across the width of the composable.
 */
@Composable
fun BarVisualizer(
    modifier: Modifier = Modifier,
    style: DrawStyle = Fill,
    brush: Brush = SolidColor(Color.Black),
    radius: Dp = 2.dp,
    innerSpacing: Dp = 1.dp,
    minHeight: Float = 0.1f,
    maxHeight: Float = 1.0f,
    /**
     * Values of the bars, between 0.0f and 1.0f, where 1.0f represents the maximum height of the composable.
     */
    amplitudes: FloatArray
) {
    val normalizedSpacing = remember(innerSpacing) { innerSpacing.coerceAtLeast(0.dp) }
    val normalizedRadius = remember(radius) { radius.coerceAtLeast(0.dp) }
    val spikesAmplitudes = amplitudes.map { animateFloatAsState(targetValue = it, animationSpec = springAnimation) }
    Canvas(
        modifier = modifier
    ) {
        if (amplitudes.isEmpty()) {
            return@Canvas
        }
        val spikeWidth = (size.width - normalizedSpacing.toPx() * (amplitudes.size - 1)) / amplitudes.size
        val spikeTotalWidth = spikeWidth + normalizedSpacing.toPx()
        spikesAmplitudes.forEachIndexed { index, amplitude ->
            val normalizedAmplitude = minHeight + (maxHeight - minHeight) * amplitude.value.coerceIn(0.0f, 1.0f)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(
                    x = index * spikeTotalWidth,
                    y = size.height * (1 - normalizedAmplitude) / 2F
                ),
                size = Size(
                    width = spikeWidth,
                    height = (size.height * normalizedAmplitude).coerceAtLeast(1.dp.toPx())
                ),
                cornerRadius = CornerRadius(normalizedRadius.toPx(), normalizedRadius.toPx()),
                style = style
            )
        }
    }
}