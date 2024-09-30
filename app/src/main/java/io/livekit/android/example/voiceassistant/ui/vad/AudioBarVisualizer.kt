package io.livekit.android.example.voiceassistant.ui.vad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.example.voiceassistant.audio.AudioFormat
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import livekit.org.webrtc.AudioTrackSink
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt


/**
 * @param loPass the start index of the FFT samples to use (inclusive). 0 <= loPass < [hiPass].
 * @param hiPass the end index of the FFT samples to use (exclusive). [loPass] < hiPass <= [FFTAudioAnalyzer.SAMPLE_SIZE].
 */
@Beta
@Composable
fun AudioBarVisualizer(
    audioTrackRef: TrackReference?,
    modifier: Modifier = Modifier,
    barCount: Int = 15,
    loPass: Int = 50,
    hiPass: Int = 150,
    style: DrawStyle = Fill,
    brush: Brush = SolidColor(Color.Black),
    alphas: FloatArray? = null,
) {
    val audioSink = remember(audioTrackRef) { AudioTrackSinkFlow() }
    val audioProcessor = remember(audioTrackRef) { FFTAudioAnalyzer() }
    val fftFlow = audioProcessor.fftFlow
    var amplitudes by remember(audioTrackRef, barCount) { mutableStateOf(FloatArray(barCount)) }

    // Attach the sink to the track.
    DisposableEffect(key1 = audioTrackRef) {
        val track = audioTrackRef?.publication?.track as? RemoteAudioTrack
        track?.addSink(audioSink)

        onDispose {
            track?.removeSink(audioSink)
            audioProcessor.release()
        }
    }

    // Configure audio processor as needed.
    LaunchedEffect(key1 = audioTrackRef) {
        audioSink.audioFormat.collect {
            audioProcessor.configure(it)
        }
    }

    // Collect audio bytes and pass to audio fft analyzer
    LaunchedEffect(key1 = audioTrackRef) {
        launch(Dispatchers.IO) {
            audioSink.audioFlow.collect { (buffer, _) ->
                audioProcessor.queueInput(buffer)
            }
        }
    }

    // Process audio bytes into desired bars
    LaunchedEffect(audioTrackRef, barCount) {

        val averages = FloatArray(barCount)
        launch(Dispatchers.IO) {
            fftFlow.collect { fft ->
                val sliced = fft.slice(loPass until hiPass)
                amplitudes = calculateAmplitudeBarsFromFFT(sliced, averages, barCount)
            }
        }
    }

    BarVisualizer(
        amplitudes = amplitudes,
        modifier = modifier,
        style = style,
        brush = brush,
        minHeight = 0.3f,
        alphas = alphas,
    )
}

private const val MIN_CONST = 2f
private const val MAX_CONST = 25f

private fun calculateAmplitudeBarsFromFFT(
    fft: List<Float>,
    averages: FloatArray,
    barCount: Int,
): FloatArray {

    val amplitudes = FloatArray(barCount)
    if (fft.isEmpty()) {
        return amplitudes
    }

    // We average out the values over 3 occurences (plus the current one), so big jumps are smoothed out
    // Iterate over the entire FFT result array.
    for (barIndex in 0 until barCount) {

        // Note: each FFT is a real and imaginary pair.
        // Scale down by 2 and scale back up to ensure we get an even number.
        val prevLimit = (round(fft.size.toFloat() / 2 * barIndex / barCount).toInt() * 2)
            .coerceIn(0, fft.size - 1)
        val nextLimit = (round(fft.size.toFloat() / 2 * (barIndex + 1) / barCount).toInt() * 2)
            .coerceIn(0, fft.size - 1)

        var accum = 0f
        // Here we iterate within this single band
        for (i in prevLimit until nextLimit step 2) {
            // Convert real and imaginary part to get energy

            val realSq = fft[i]
                .toDouble()
                .pow(2.0)
            val imaginarySq = fft[i + 1]
                .toDouble()
                .pow(2.0)
            val raw = sqrt(realSq + imaginarySq).toFloat()

            accum += raw
        }

        // A window might be empty which would result in a 0 division
        if ((nextLimit - prevLimit) != 0) {
            accum /= (nextLimit - prevLimit)
        } else {
            accum = 0.0f
        }

        val smoothingFactor = 5
        var avg = averages[barIndex]
        avg += (accum - avg / smoothingFactor)
        averages[barIndex] = avg

        var amplitude = avg.coerceIn(MIN_CONST, MAX_CONST)
        amplitude -= MIN_CONST
        amplitude /= (MAX_CONST - MIN_CONST)
        amplitudes[barIndex] = amplitude
    }

    return amplitudes
}

class AudioTrackSinkFlow : AudioTrackSink {
    val audioFormat = MutableStateFlow(AudioFormat(16, 48000, 1))
    val audioFlow = MutableSharedFlow<Pair<ByteBuffer, Int>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long
    ) {
        val curAudioFormat = audioFormat.value
        if (curAudioFormat.bitsPerSample != bitsPerSample ||
            curAudioFormat.sampleRate != sampleRate ||
            curAudioFormat.numberOfChannels != numberOfChannels
        ) {
            audioFormat.tryEmit(AudioFormat(bitsPerSample, sampleRate, numberOfChannels))
        }
        audioFlow.tryEmit(audioData to numberOfFrames)
    }
}

