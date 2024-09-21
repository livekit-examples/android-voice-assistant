package io.livekit.android.example.voiceassistant.ui.visualizer

import android.media.audiofx.Visualizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.livekit.android.util.LKLog
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

// Taken from: https://en.wikipedia.org/wiki/Preferred_number#Audio_frequencies
private val FREQUENCY_BAND_LIMITS = arrayOf(
    20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630,
    800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000,
    12500, 16000, 20000
)
private val BANDS = FREQUENCY_BAND_LIMITS.size
private val maxConst = 25_000 // Reference max value for accum magnitude
private val smoothingFactor = 3

@Composable
fun AndroidVisualizer(modifier: Modifier = Modifier, audioSessionId: Int) {
    if (audioSessionId <= 0) {
        return
    }
    val visualizer = remember(audioSessionId) { Visualizer(audioSessionId) }
    val amplitudes = remember { mutableStateListOf(*Array(FREQUENCY_BAND_LIMITS.size) { 0f }) }
    val previousValues = remember { FloatArray(BANDS * smoothingFactor) }
    val amplitudesState = remember {
        mutableStateOf(-1 to amplitudes)
    }
    var fftBytes by remember { mutableStateOf<ByteArray?>(null) }
    DisposableEffect(visualizer) {

        LKLog.e { "max capture rate: ${Visualizer.getMaxCaptureRate()}" }
        var result = visualizer.setDataCaptureListener(
            object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                }

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fftBytes = fft ?: return
                    var currentFftPosition = 0
                    var currentFrequencyBandLimitIndex = 0
                    val SIZE = fft.size / 2 + 1
                    LKLog.e { "FFT Size: ${fft.size}" }
                    LKLog.e { "sample rate: ${visualizer?.samplingRate}" }
                    LKLog.e { "capturesize: ${visualizer?.captureSize}" }
                    LKLog.e { "measurement: ${visualizer?.measurementMode}" }
                    LKLog.e { "scalingmode: ${visualizer?.scalingMode}" }

                    val n: Int = fft.size

                    val magnitudes = FloatArray(n / 2 + 1)
                    val phases = FloatArray(n / 2 + 1)
                    magnitudes[0] = abs(fft[0].toFloat())// DC
                    magnitudes[n / 2] = abs(fft[1].toFloat()) // Nyquist
                    phases[0] = 0f
                    phases[n / 2] = 0f

                    for (k in 1 until n / 2) {
                        val i = k * 2
                        LKLog.e { "$k: ${fft[i]}, ${fft[i + 1]}" }
                        magnitudes[k] = hypot(fft[i].toDouble(), fft[i + 1].toDouble()).toFloat()
                        phases[k] = atan2(fft[i + 1].toDouble(), fft[i].toDouble()).toFloat()
                    }

                    while (currentFftPosition < SIZE) {
                        var accum = 0f

                        // We divide the bands by frequency.
                        // Check until which index we need to stop for the current band
                        val nextLimitAtPosition =
                            floor(FREQUENCY_BAND_LIMITS[currentFrequencyBandLimitIndex] / 20_000.toFloat() * SIZE).toInt()

                        synchronized(fft) {
                            // Here we iterate within this single band
                            for (j in 0 until (nextLimitAtPosition - currentFftPosition)) {
                                // Convert real and imaginary part to get energy

                                val realSq = magnitudes[currentFftPosition + j]
                                    .toDouble()
                                    .pow(2.0)
                                val imaginarySq = phases[currentFftPosition + j]
                                    .toDouble()
                                    .pow(2.0)
                                val raw = (realSq + imaginarySq).toFloat()

                                // Hamming window (by frequency band instead of frequency, otherwise it would prefer 10kHz, which is too high)
                                // The window mutes down the very high and the very low frequencies, usually not hearable by the human ear
                                val m = BANDS / 2
                                val windowed = raw * (0.54f - 0.46f * cos(2 * Math.PI * currentFrequencyBandLimitIndex / (m + 1))).toFloat()
                                accum += windowed
                            }
                        }

                        // A window might be empty which would result in a 0 division
                        if (nextLimitAtPosition - currentFftPosition != 0) {
                            accum /= (nextLimitAtPosition - currentFftPosition)
                        } else {
                            accum = 0.0f
                        }
                        currentFftPosition = nextLimitAtPosition

                        // Here we do the smoothing
                        // If you increase the smoothing factor, the high shoots will be toned down, but the
                        // 'movement' in general will decrease too
                        var smoothedAccum = accum
                        for (i in 0 until smoothingFactor) {
                            smoothedAccum += previousValues[i * BANDS + currentFrequencyBandLimitIndex]
                            if (i != smoothingFactor - 1) {
                                previousValues[i * BANDS + currentFrequencyBandLimitIndex] =
                                    previousValues[(i + 1) * BANDS + currentFrequencyBandLimitIndex]
                            } else {
                                previousValues[i * BANDS + currentFrequencyBandLimitIndex] = accum
                            }
                        }

                        smoothedAccum /= (smoothingFactor + 1) // +1 because it also includes the current value

                        //LKLog.e { "smoothedAccum: $smoothedAccum" }

                        amplitudes[currentFrequencyBandLimitIndex] = (smoothedAccum / maxConst).coerceIn(0f, 1f)
                        currentFrequencyBandLimitIndex++
                    }
                }
            },
            Visualizer.getMaxCaptureRate(),
            false,
            true,
        )
        LKLog.e { "setDataCapture: $result" }
        result = visualizer.setCaptureSize(1024)
        LKLog.e { "setCaptureSize: $result" }
        visualizer.setEnabled(true)
        onDispose {
            visualizer.setDataCaptureListener(null, 0, false, false)
            visualizer.setEnabled(false)
            visualizer.release()
        }
    }

    BarVisualizer(amplitudes = amplitudes.toList(), modifier = modifier)
}