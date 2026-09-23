package com.example.service.call

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standard states for real-time WebRTC calling.
 */
enum class CallSessionState {
    IDLE,
    CALLING,
    RINGING,
    CONNECTING,
    CONNECTED,
    ENDED
}

/**
 * Represents metadata for a state change event.
 */
data class CallStateTransitionEvent(
    val fromState: CallSessionState,
    val toState: CallSessionState,
    val callId: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * CallStateManager tracks and strictly enforces valid state transitions between
 * IDLE, CALLING, RINGING, CONNECTING, CONNECTED, and ENDED states.
 * Illegal transitions are prevented to avoid race conditions or invalid WebRTC states.
 */
object CallStateManager {

    private const val TAG = "CallStateManager"

    private val _currentState = MutableStateFlow(CallSessionState.IDLE)
    val currentState: StateFlow<CallSessionState> = _currentState.asStateFlow()

    private val _currentCallId = MutableStateFlow("")
    val currentCallId: StateFlow<String> = _currentCallId.asStateFlow()

    private val _transitionEvents = MutableSharedFlow<CallStateTransitionEvent>(replay = 1)
    val transitionEvents: SharedFlow<CallStateTransitionEvent> = _transitionEvents.asSharedFlow()

    /**
     * Map of allowed transitions:
     * - IDLE -> CALLING (outgoing), RINGING (incoming)
     * - CALLING -> RINGING (remote ringing signaled), CONNECTING (accepted, SDP exchange), ENDED (cancelled/timeout/busy)
     * - RINGING -> CONNECTING (accepted, SDP exchange), ENDED (rejected/timeout/cancelled)
     * - CONNECTING -> CONNECTED (WebRTC ICE connected), ENDED (handshake failed/timeout)
     * - CONNECTED -> ENDED (hangup/remote end/network failure)
     * - ENDED -> IDLE (cleanup & reset)
     */
    private val allowedTransitions: Map<CallSessionState, Set<CallSessionState>> = mapOf(
        CallSessionState.IDLE to setOf(
            CallSessionState.CALLING,
            CallSessionState.RINGING,
            CallSessionState.ENDED
        ),
        CallSessionState.CALLING to setOf(
            CallSessionState.RINGING,
            CallSessionState.CONNECTING,
            CallSessionState.ENDED
        ),
        CallSessionState.RINGING to setOf(
            CallSessionState.CONNECTING,
            CallSessionState.ENDED
        ),
        CallSessionState.CONNECTING to setOf(
            CallSessionState.CONNECTED,
            CallSessionState.ENDED
        ),
        CallSessionState.CONNECTED to setOf(
            CallSessionState.ENDED
        ),
        CallSessionState.ENDED to setOf(
            CallSessionState.IDLE
        )
    )

    /**
     * Attempts to transition to [nextState].
     * Returns true if transition was valid and applied; false if illegal.
     */
    @Synchronized
    fun transitionTo(nextState: CallSessionState, callId: String = _currentCallId.value, reason: String = ""): Boolean {
        val from = _currentState.value

        // Allow no-op transition to the same state
        if (from == nextState) {
            Log.d(TAG, "Already in state $nextState. Ignoring redundant transition.")
            return true
        }

        val permitted = allowedTransitions[from]?.contains(nextState) == true
        if (!permitted) {
            Log.e(
                TAG,
                "ILLEGAL STATE TRANSITION BLOCKED: Cannot transition from [$from] to [$nextState]. " +
                        "Allowed targets from [$from] are: ${allowedTransitions[from]}. CallId: $callId, Reason: $reason"
            )
            return false
        }

        _currentCallId.value = callId
        _currentState.value = nextState

        val event = CallStateTransitionEvent(
            fromState = from,
            toState = nextState,
            callId = callId,
            reason = reason
        )
        _transitionEvents.tryEmit(event)

        Log.i(TAG, "Call State Transition: [$from] -> [$nextState] (CallId: $callId, Reason: '$reason')")
        return true
    }

    /**
     * Resets the manager to IDLE state.
     */
    @Synchronized
    fun reset() {
        val from = _currentState.value
        if (from != CallSessionState.IDLE) {
            _currentState.value = CallSessionState.IDLE
            _currentCallId.value = ""
            _transitionEvents.tryEmit(
                CallStateTransitionEvent(
                    fromState = from,
                    toState = CallSessionState.IDLE,
                    callId = "",
                    reason = "RESET"
                )
            )
            Log.i(TAG, "CallStateManager reset to IDLE from [$from]")
        }
    }

    /**
     * Returns true if there is an ongoing or pending call in progress.
     */
    fun isCallActive(): Boolean {
        return when (_currentState.value) {
            CallSessionState.CALLING,
            CallSessionState.RINGING,
            CallSessionState.CONNECTING,
            CallSessionState.CONNECTED -> true
            CallSessionState.IDLE,
            CallSessionState.ENDED -> false
        }
    }

    /**
     * Checks if transitioning to [targetState] is currently valid.
     */
    fun canTransitionTo(targetState: CallSessionState): Boolean {
        return allowedTransitions[_currentState.value]?.contains(targetState) == true
    }
}
