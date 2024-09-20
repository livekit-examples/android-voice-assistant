@file:OptIn(Beta::class)

package io.livekit.android.example.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.state.transcriptions.rememberParticipantTranscriptions
import io.livekit.android.compose.state.transcriptions.rememberTranscriptions
import io.livekit.android.example.voiceassistant.audio.LocalAudioTrackFlow
import io.livekit.android.example.voiceassistant.state.AssistantState
import io.livekit.android.example.voiceassistant.state.rememberAssistantState
import io.livekit.android.example.voiceassistant.ui.FFTRemoteAudioTrackBarVisualizer
import io.livekit.android.example.voiceassistant.ui.UserTranscription
import io.livekit.android.example.voiceassistant.ui.theme.LiveKitVoiceAssistantExampleTheme
import io.livekit.android.room.track.Track
import io.livekit.android.util.LoggingLevel

// Replace these values with your url and generated token.
const val wsURL = "ws://192.168.11.2:7880"
const val token =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MjYyMjI2NjIsImlzcyI6IkFQSVRMV3JLOHRid3I0NyIsIm5iZiI6MTcyMzYzMDY2Miwic3ViIjoicGhvbmUiLCJ2aWRlbyI6eyJyb29tIjoibXlyb29tIiwicm9vbUpvaW4iOnRydWV9fQ.61oC0qB3cOxIv-MUp89e05Pelw-G_thqg5G7UMEmAXw"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.loggingLevel = LoggingLevel.DEBUG
        requireNeededPermissions {
            setContent {
                LiveKitVoiceAssistantExampleTheme {
                    VoiceAssistant(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun VoiceAssistant(modifier: Modifier = Modifier) {
    ConstraintLayout(modifier = modifier) {
        // Setup listening to the local microphone if needed.
        val localAudioFlow = remember { LocalAudioTrackFlow() }
        val overrides = remember {
            LiveKitOverrides(
                audioOptions = AudioOptions(
                    javaAudioDeviceModuleCustomizer = { builder ->
                        builder.setSamplesReadyCallback(localAudioFlow)
                    }
                )
            )
        }

        RoomScope(
            url = wsURL,
            token = token,
            audio = true,
            connect = true,
            liveKitOverrides = overrides
        ) { room ->
            val (audioVisualizer, chatLog) = createRefs()
            val trackRefs = rememberTracks(sources = listOf(Track.Source.MICROPHONE))
            val remoteTrackRef = trackRefs.firstOrNull { it.participant != room.localParticipant }

            val assistantState = rememberAssistantState(participant = remoteTrackRef?.participant)

            // Optionally do something with the assistant state.
            when (assistantState) {
                AssistantState.LISTENING -> {}
                AssistantState.THINKING -> {}
                AssistantState.SPEAKING -> {}
                else -> {}
            }

            // Amplitude visualization of the Assistant's voice track.
            FFTRemoteAudioTrackBarVisualizer(
                audioTrackRef = remoteTrackRef,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .constrainAs(audioVisualizer) {
                        height = Dimension.percent(0.1f)
                        width = Dimension.fillToConstraints

                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
            )

            // Get and display the transcriptions.
            val segments = rememberTranscriptions()
            val localSegments = rememberParticipantTranscriptions(room.localParticipant)
            val lazyListState = rememberLazyListState()

            LazyColumn(
                userScrollEnabled = true,
                state = lazyListState,
                modifier = Modifier
                    .constrainAs(chatLog) {
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        height = Dimension.percent(0.9f)
                        width = Dimension.fillToConstraints
                    }
            ) {
                items(
                    items = segments,
                    key = { segment -> segment.id },
                ) { segment ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        if (localSegments.contains(segment)) {
                            UserTranscription(segment = segment, modifier = Modifier.align(Alignment.CenterEnd))
                        } else {
                            Text(text = segment.text, modifier = Modifier.align(Alignment.CenterStart))
                        }
                    }
                }
            }

            // Scroll to bottom as new transcriptions come in.
            LaunchedEffect(segments) {
                lazyListState.scrollToItem((segments.size - 1).coerceAtLeast(0))
            }
        }
    }
}
