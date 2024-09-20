/**
 * Originally adapted from: https://github.com/dzolnai/ExoVisualizer
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

import android.media.AudioTrack
import com.paramsen.noise.Noise
import io.livekit.android.example.voiceassistant.audio.AudioFormat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.max


class FFTAudioProcessor {

    companion object {
        const val SAMPLE_SIZE = 1024
        private val EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // Extra size next in addition to the AudioTrack buffer size
        private const val BUFFER_EXTRA_SIZE = SAMPLE_SIZE * 8
    }


    var isActive = false
        private set

    private var noise: Noise? = null
    private lateinit var inputAudioFormat: AudioFormat

    private var audioTrackBufferSize = 0

    private var fftBuffer: ByteBuffer = EMPTY_BUFFER
    private lateinit var srcBuffer: ByteBuffer
    private var srcBufferPosition = 0
    private val tempByteArray = ByteArray(SAMPLE_SIZE * 2)
    private val src = FloatArray(SAMPLE_SIZE)
    private val dst = FloatArray(SAMPLE_SIZE + 2)
    private var count = 0

    private val mutableFftFlow = MutableSharedFlow<Pair<Int, FloatArray>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val fftFlow: Flow<Pair<Int, FloatArray>> = mutableFftFlow

    fun configure(inputAudioFormat: AudioFormat) {
        this.inputAudioFormat = inputAudioFormat
        isActive = true

        noise = Noise.real(SAMPLE_SIZE)

        audioTrackBufferSize = getDefaultBufferSizeInBytes(inputAudioFormat)

        srcBuffer = ByteBuffer.allocate(audioTrackBufferSize + BUFFER_EXTRA_SIZE)

    }

    fun queueInput(inputBuffer: ByteBuffer) {
        var position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val frameCount = (limit - position) / (2 * inputAudioFormat.numberOfChannels)
        val singleChannelOutputSize = frameCount * 2

        // Setup buffer
        if (fftBuffer.capacity() < singleChannelOutputSize) {
            fftBuffer =
                ByteBuffer.allocateDirect(singleChannelOutputSize).order(ByteOrder.nativeOrder())
        } else {
            fftBuffer.clear()
        }

        // Process inputBuffer
        while (position < limit) {
            var summedUp = 0
            for (channelIndex in 0 until inputAudioFormat.numberOfChannels) {
                val current = inputBuffer.getShort(position + 2 * channelIndex)
                summedUp += current
            }
            // For the FFT, we use an average of all the channels
            fftBuffer.putShort((summedUp / inputAudioFormat.numberOfChannels).toShort())
            position += inputAudioFormat.numberOfChannels * 2
        }

        inputBuffer.position(limit)

        processFFT(this.fftBuffer)

    }

    private fun processFFT(buffer: ByteBuffer) {
        if (noise == null) {
            return
        }
        srcBuffer.put(buffer.array())
        srcBufferPosition += buffer.array().size
        // Since this is PCM 16 bit, each sample will be 2 bytes.
        // So to get the sample size in the end, we need to take twice as many bytes off the buffer
        val bytesToProcess = SAMPLE_SIZE * 2
        var currentByte: Byte? = null
        while (srcBufferPosition > audioTrackBufferSize) {
            srcBuffer.position(0)
            srcBuffer.get(tempByteArray, 0, bytesToProcess)

            tempByteArray.forEachIndexed { index, byte ->
                if (currentByte == null) {
                    currentByte = byte
                } else {
                    src[index / 2] =
                        (currentByte!!.toFloat() * Byte.MAX_VALUE + byte) / (Byte.MAX_VALUE * Byte.MAX_VALUE)
                    dst[index / 2] = 0f
                    currentByte = null
                }

            }
            srcBuffer.position(bytesToProcess)
            srcBuffer.compact()
            srcBufferPosition -= bytesToProcess
            srcBuffer.position(srcBufferPosition)
            val dst = FloatArray(SAMPLE_SIZE + 2)
            val fft = noise?.fft(src, dst)!!

            if (count == Int.MAX_VALUE) {
                count = Int.MIN_VALUE
            }
            count++
            mutableFftFlow.tryEmit(count to fft)

        }
    }

}

private fun durationUsToFrames(sampleRate: Int, durationUs: Long): Long {
    return durationUs * sampleRate / TimeUnit.MICROSECONDS.convert(1, TimeUnit.SECONDS)
}

private fun getPcmFrameSize(channelCount: Int): Int {
    // assumes PCM_16BIT
    return channelCount * 2
}

private fun getAudioTrackChannelConfig(channelCount: Int): Int {
    return when (channelCount) {
        1 -> android.media.AudioFormat.CHANNEL_OUT_MONO
        2 -> android.media.AudioFormat.CHANNEL_OUT_STEREO
        // ignore other channel counts that aren't used in LiveKit
        else -> android.media.AudioFormat.CHANNEL_INVALID
    }
}

private fun getDefaultBufferSizeInBytes(audioFormat: AudioFormat): Int {
    val outputPcmFrameSize = getPcmFrameSize(audioFormat.numberOfChannels)
    val minBufferSize =
        AudioTrack.getMinBufferSize(
            audioFormat.sampleRate,
            getAudioTrackChannelConfig(audioFormat.numberOfChannels),
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )

    check(minBufferSize != AudioTrack.ERROR_BAD_VALUE)
    val multipliedBufferSize = minBufferSize * 4
    val minAppBufferSize =
        durationUsToFrames(audioFormat.sampleRate, 250000).toInt() * outputPcmFrameSize
    val maxAppBufferSize = max(
        minBufferSize.toLong(),
        durationUsToFrames(audioFormat.sampleRate, 750000) * outputPcmFrameSize
    ).toInt()
    val bufferSizeInFrames =
        multipliedBufferSize.coerceIn(minAppBufferSize, maxAppBufferSize) / outputPcmFrameSize
    return bufferSizeInFrames * outputPcmFrameSize
}