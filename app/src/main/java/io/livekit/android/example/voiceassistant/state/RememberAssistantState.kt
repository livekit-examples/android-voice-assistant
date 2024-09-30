package io.livekit.android.example.voiceassistant.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.livekit.android.room.participant.Participant
import io.livekit.android.util.flow
import kotlinx.coroutines.flow.collectLatest

const val VOICE_ASSISTANT_STATE_KEY = "voice_assistant.state"

/**
 * Keeps track of the assistant state for a participant.
 */
@Composable
fun rememberAssistantState(participant: Participant?): AssistantState {
    var state by remember(participant) {
        val initialState = AssistantState.fromAttribute(
            participant?.attributes?.get(VOICE_ASSISTANT_STATE_KEY)
        )
        mutableStateOf(initialState)
    }

    LaunchedEffect(key1 = participant) {
        if (participant == null) {
            return@LaunchedEffect
        }

        participant::attributes.flow.collectLatest { attributes ->
            state = AssistantState.fromAttribute(attributes[VOICE_ASSISTANT_STATE_KEY])
        }
    }

    return state
}

enum class AssistantState {
    LISTENING,
    THINKING,
    SPEAKING,
    UNKNOWN;

    companion object {
        fun fromAttribute(attribute: String?): AssistantState {
            return when (attribute) {
                "listening" -> LISTENING
                "thinking" -> THINKING
                "speaking" -> SPEAKING
                else -> UNKNOWN
            }
        }
    }
}