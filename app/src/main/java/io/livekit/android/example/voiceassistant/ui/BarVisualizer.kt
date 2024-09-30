package io.livekit.android.example.voiceassistant.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.livekit.android.util.LKLog

/**
 * Draws bars evenly split across the width of the composable.
 */
@Composable
fun BarVisualizer(
    modifier: Modifier = Modifier,
    style: DrawStyle = Fill,
    brush: Brush = SolidColor(Color.Black),
    radius: Dp = 2.dp,
    innerPadding: Dp = 1.dp,
    /**
     * Values of the bars, between 0.0f and 1.0f, where 1.0f represents the maximum height of the composable.
     */
    amplitudes: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f)
) {

    LKLog.e { "bar recomp" }
    val _padding = remember(innerPadding) { innerPadding.coerceAtLeast(0.dp) }
    val _radius = remember(radius) { radius.coerceAtLeast(0.dp) }
    val spikesAmplitudes = amplitudes.toMutableList()
        .map { animateFloatAsState(targetValue = it) }
    Canvas(
        modifier = modifier
    ) {
        LKLog.e { "drawing" }
        val spikeWidth = (size.width / amplitudes.size) - _padding.toPx()
        val spikeTotalWidth = spikeWidth + _padding.toPx()
        spikesAmplitudes.forEachIndexed { index, amplitude ->
            drawRoundRect(
                brush = brush,
                topLeft = Offset(
                    x = index * spikeTotalWidth + _padding.toPx() / 2,
                    y = size.height * (1 - amplitude.value) / 2F
                ),
                size = Size(
                    width = spikeWidth,
                    height = (size.height * amplitude.value).coerceAtLeast(1.dp.toPx())
                ),
                cornerRadius = CornerRadius(_radius.toPx(), _radius.toPx()),
                style = style
            )
        }
        LKLog.e { "drawingend" }
    }
}

@Preview(
    showSystemUi = true,
    showBackground = true,
)
@Composable
fun BarVisualizerPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        BarVisualizer(
            modifier = Modifier
                .height(100.dp)
                .fillMaxWidth()
        )
    }
}