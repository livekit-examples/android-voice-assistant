package io.livekit.android.example.voiceassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.example.voiceassistant.audio.AudioFormat
import io.livekit.android.example.voiceassistant.ui.noise.FFTAudioProcessor
import io.livekit.android.example.voiceassistant.ui.noise.FFTBarVisualizer
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import livekit.org.webrtc.AudioTrackSink
import java.nio.ByteBuffer

@Composable
fun FFTRemoteAudioTrackBarVisualizer(
    audioTrackRef: TrackReference?,
    modifier: Modifier = Modifier
) {
    val audioSink = remember { AudioTrackSinkFlow() }
    val audioProcessor = remember { FFTAudioProcessor() }
    val fft by audioProcessor.fftFlow.collectAsState(initial = 1 to FloatArray(0))
    DisposableEffect(key1 = audioTrackRef) {
        val track = audioTrackRef?.publication?.track as? RemoteAudioTrack
        track?.addSink(audioSink)

        onDispose {
            track?.removeSink(audioSink)
        }
    }

    LaunchedEffect(key1 = audioTrackRef) {
        audioSink.audioFormat.collect {
            audioProcessor.configure(it)
        }
    }

    LaunchedEffect(key1 = audioTrackRef) {
        launch(Dispatchers.IO) {
            audioSink.audioFlow.collect { (buffer, _) ->
                audioProcessor.queueInput(buffer)
            }
        }
    }

    FFTBarVisualizer(fft = fft.second, modifier = modifier)
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
