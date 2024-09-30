package io.livekit.android.example.voiceassistant.ui.vad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.types.TrackReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val ON = 1.0f
private const val OFF = 0.3f

@Beta
@Composable
fun VoiceAssistantBarVisualizer(
    agentState: String?,
    audioTrackRef: TrackReference?,
    modifier: Modifier = Modifier,
    barCount: Int = 15,
    loPass: Int = 50,
    hiPass: Int = 150,
    brush: Brush = SolidColor(Color.Black),
) {
    val alphas = rememberVoiceAssistantAlphas(agentState = agentState, barCount = barCount)
    AudioBarVisualizer(
        audioTrackRef = audioTrackRef,
        modifier = modifier,
        barCount = barCount,
        loPass = loPass,
        hiPass = hiPass,
        style = Fill,
        brush = brush,
        alphas = alphas
    )
}

@Composable
private fun rememberVoiceAssistantAlphas(
    agentState: String?,
    barCount: Int,
): FloatArray {
    var sequenceIndex by remember(agentState, barCount) {
        mutableIntStateOf(0)
    }
    val sequences = remember(agentState, barCount) {
        when (agentState) {
            "connecting",
            "initializing" -> generateConnectingSequenceBar(barCount)

            "listening",
            "thinking" -> generateListeningSequenceBar(barCount)

            "speaking" -> generateSpeakingSequenceBar(barCount)
            else -> listOf(FloatArray(0))
        }
    }
    val intervalMs = remember(agentState) {
        when (agentState) {
            "connecting" -> 600L / barCount
            "initializing" -> 1200L / barCount

            "listening" -> 500L
            "thinking" -> 150L

            "speaking" -> -1L
            else -> -1L
        }
    }
    LaunchedEffect(sequences, intervalMs) {
        if (intervalMs < 0) {
            return@LaunchedEffect
        }

        while (isActive) {
            delay(intervalMs)
            if (sequenceIndex == Int.MAX_VALUE) {
                sequenceIndex = Int.MIN_VALUE
            }
            sequenceIndex++
        }
    }

    return sequences[sequenceIndex % sequences.size]
}

private fun generateSpeakingSequenceBar(barCount: Int): List<FloatArray> {
    val allSequence = FloatArray(barCount) { i -> ON }
    return listOf(allSequence)
}

private fun generateConnectingSequenceBar(barCount: Int): List<FloatArray> {
    val sequences = mutableListOf<FloatArray>()
    for (i in 0 until barCount) {
        val sequence = FloatArray(barCount) { j ->
            if (j == i || j == barCount - 1 - i) {
                ON
            } else {
                OFF
            }
        }
        sequences.add(sequence)
    }
    return sequences
}

private fun generateListeningSequenceBar(barCount: Int): List<FloatArray> {
    val center = barCount / 2
    val middleBarSequence = FloatArray(barCount) { i ->
        if (i == center) {
            ON
        } else {
            OFF
        }
    }
    val goneSequence = FloatArray(barCount) { OFF }

    return listOf(middleBarSequence, goneSequence)
}