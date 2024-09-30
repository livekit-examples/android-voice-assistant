/**
 * Adapted from: https://github.com/dzolnai/ExoVisualizer
 *
 * MIT License
 *
 * Copyright (c) 2019 Dániel Zolnai
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.livekit.android.example.voiceassistant.ui.noise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

// Taken from: https://en.wikipedia.org/wiki/Preferred_number#Audio_frequencies
private val FREQUENCY_BAND_LIMITS = FloatArray(31) { 20000 / 31f * it }


//private val FREQUENCY_BAND_LIMITS = arrayOf(
//    20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630,
//    800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000,
//    12500, 16000, 20000
//)

private val BANDS = FREQUENCY_BAND_LIMITS.size
private val SIZE = FFTAudioAnalyzer.SAMPLE_SIZE / 2
private val maxConst = 1E14 // Reference max value for accum magnitude

@Composable
fun FFTBarVisualizer(
    fft: FloatArray,
    modifier: Modifier = Modifier,
    style: DrawStyle = Fill,
    brush: Brush = SolidColor(Color.Blue),
    radius: Dp = 2.dp,
    innerPadding: Dp = 1.dp,
    smoothingFactor: Int = 3,
) {

    val _padding = remember(innerPadding) { innerPadding.coerceAtLeast(0.dp) }
    val _radius = remember(radius) { radius.coerceAtLeast(0.dp) }
    val previousValues = remember { FloatArray(BANDS * smoothingFactor) }
    Box(modifier = modifier
        .fillMaxSize()
        .drawWithCache {
            onDrawWithContent {

                if (fft.isEmpty()) {
                    return@onDrawWithContent
                }

                var currentFftPosition = 0
                var currentFrequencyBandLimitIndex = 0

                // We average out the values over 3 occurences (plus the current one), so big jumps are smoothed out
                // Iterate over the entire FFT result array
                while (currentFftPosition < SIZE && currentFrequencyBandLimitIndex < FREQUENCY_BAND_LIMITS.size) {
                    var accum = 0f

                    // We divide the bands by frequency.
                    // Check until which index we need to stop for the current band
                    val nextLimitAtPosition =
                        floor(FREQUENCY_BAND_LIMITS[currentFrequencyBandLimitIndex] / 24000.toFloat() * SIZE).toInt()

                    synchronized(fft) {
                        // Here we iterate within this single band
                        for (j in 0 until (nextLimitAtPosition - currentFftPosition) step 2) {
                            // Convert real and imaginary part to get energy

                            val realSq = fft[currentFftPosition + j]
                                .toDouble()
                                .pow(2.0)
                            val imaginarySq = fft[currentFftPosition + j + 1]
                                .toDouble()
                                .pow(2.0)
                            val raw = sqrt(realSq + imaginarySq).toFloat()

                            accum += raw
//                            // Hamming window (by frequency band instead of frequency, otherwise it would prefer 10kHz, which is too high)
//                            // The window mutes down the very high and the very low frequencies, usually not hearable by the human ear
//                            val m = BANDS / 2
//                            val windowed = raw * (0.54f - 0.46f * cos(2 * Math.PI * currentFrequencyBandLimitIndex / (m + 1))).toFloat()
//                            accum += windowed
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
                            // Shift over previous values
                            previousValues[i * BANDS + currentFrequencyBandLimitIndex] =
                                previousValues[(i + 1) * BANDS + currentFrequencyBandLimitIndex]
                        } else {
                            // Copy current value into last band slot
                            previousValues[i * BANDS + currentFrequencyBandLimitIndex] = accum
                        }
                    }
                    smoothedAccum /= (smoothingFactor + 1) // +1 because it also includes the current value

                    val leftX = size.width * (currentFrequencyBandLimitIndex / BANDS.toFloat())
                    val rightX = leftX + size.width / BANDS.toFloat()

                    val barHeight =
                        (size.height * (smoothedAccum / maxConst.toDouble())
                            .coerceAtMost(1.0)
                            .toFloat())
                    val top = size.height - barHeight


                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(leftX, top),
                        size = Size(rightX - leftX, barHeight),
                        cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
                    )


                    currentFrequencyBandLimitIndex++
                }

            }
        }
    )
}