package io.livekit.android.example.voiceassistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun BarVisualizer(
    modifier: Modifier = Modifier,
    style: DrawStyle = Fill,
    brush: Brush = SolidColor(Color.Black),
    radius: Dp = 2.dp,
    innerPadding: Dp = 1.dp,
    amplitudes: List<Float> = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f)
) {
    val _padding = remember(innerPadding) { innerPadding.coerceAtLeast(0.dp) }
    val _radius = remember(radius) { radius.coerceAtLeast(0.dp) }
    val spikesAmplitudes = remember(amplitudes) { amplitudes }
    Canvas(
        modifier = modifier
    ) {
        val spikeWidth = (size.width / amplitudes.size) - _padding.toPx()
        val spikeTotalWidth = spikeWidth + _padding.toPx()
        spikesAmplitudes.forEachIndexed { index, amplitude ->
            drawRoundRect(
                brush = brush,
                topLeft = Offset(
                    x = index * spikeTotalWidth + _padding.toPx() / 2,
                    y = size.height * (1 - amplitude) / 2F
                ),
                size = Size(
                    width = spikeWidth,
                    height = size.height * amplitude
                ),
                cornerRadius = CornerRadius(_radius.toPx(), _radius.toPx()),
                style = style
            )
        }
    }
}

@Preview(
    showSystemUi = true,
    showBackground = true,
)
@Composable
fun BarVisualizerPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        BarVisualizer()
    }
}