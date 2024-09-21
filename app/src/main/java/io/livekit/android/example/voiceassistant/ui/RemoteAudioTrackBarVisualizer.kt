package io.livekit.android.example.voiceassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.example.voiceassistant.audio.AudioTrackSinkFlow
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.util.LKLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.math.round
import kotlin.math.sqrt

// Odd for single bar in the middle.
private const val NUM_BARS = 15

private const val MIN_AMPLITUDE = 0.1f
private const val MAX_VOLUME = 25000.0
private const val MIN_VOLUME = MAX_VOLUME * MIN_AMPLITUDE


@Composable
fun RemoteAudioTrackBarVisualizer(audioTrackRef: TrackReference?, modifier: Modifier = Modifier) {
    LKLog.e { "recomp" }
    val amplitudes = remember {
        val emptyInts = Array(NUM_BARS) { 0.05f }
        mutableStateListOf(*emptyInts)
    }
    val audioSink = remember { AudioTrackSinkFlow() }

    // Attach to audio track if available.
    DisposableEffect(key1 = audioTrackRef) {
        val track = audioTrackRef?.publication?.track as? RemoteAudioTrack
        track?.addSink(audioSink)

        onDispose {
            track?.removeSink(audioSink)
        }
    }

    // Collect and process audio data.
    LaunchedEffect(key1 = audioTrackRef) {
        launch(Dispatchers.IO) {
            audioSink.audioFlow.collect { (buffer, _) ->
                // Calculate the volume to display as bars.
                LKLog.e { "volume calc" }
                val volume = (calculateVolume(buffer).coerceIn(MIN_VOLUME, MAX_VOLUME) / MAX_VOLUME).toFloat()

                val middle = NUM_BARS / 2
                amplitudes[middle] = volume

                var curVolume = volume
                for (i in 1..(NUM_BARS / 2)) {
                    curVolume = (curVolume * 0.8f).coerceAtLeast(MIN_AMPLITUDE)
                    amplitudes[middle - i] = curVolume
                    amplitudes[middle + i] = curVolume
                }
                LKLog.e { "volume calc end" }
            }
        }
    }

    BarVisualizer(
        brush = SolidColor(Color(0xFF65f0f5)),
        innerPadding = 10.dp,
        radius = 2.dp,
        amplitudes = amplitudes,
        modifier = modifier,
    )
}


/**
 * Determines volume of input using the root mean squared of the amplitude.
 */
fun calculateVolume(input: ByteBuffer): Double {
    var average = 0L

    try {
        input.mark()

        val bytesPerSample = 2
        val size = input.remaining() / bytesPerSample
        while (input.remaining() >= bytesPerSample) {
            val value = input.getShort()
            average += value * value
        }

        average /= size

        return round(sqrt(average.toDouble()))
    } finally {
        input.reset()
    }
}

