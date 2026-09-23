package com.example.service.call

import android.util.Log

object CallStateMachine {
    private const val TAG = "CallStateMachine"

    /**
     * Validates whether a transition from [from] to [to] is legally allowed.
     */
    fun canTransition(from: CallStatusEnum, to: CallStatusEnum): Boolean {
        if (from == to) return true // idempotent

        return when (from) {
            CallStatusEnum.IDLE -> {
                // From idle, you can only initiate an outgoing call or receive an incoming call
                to == CallStatusEnum.CALLING || to == CallStatusEnum.RINGING
            }
            CallStatusEnum.CALLING -> {
                // Outgoing call can proceed to CONNECTING, CONNECTED, DECLINED, MISSED, BUSY, FAILED, or ENDED (canceled)
                to in listOf(
                    CallStatusEnum.CONNECTING,
                    CallStatusEnum.CONNECTED,
                    CallStatusEnum.DECLINED,
                    CallStatusEnum.MISSED,
                    CallStatusEnum.BUSY,
                    CallStatusEnum.FAILED,
                    CallStatusEnum.ENDED
                )
            }
            CallStatusEnum.RINGING -> {
                // Incoming call can be answered (CONNECTING/CONNECTED), declined, missed (timed out), or canceled by caller (ENDED)
                to in listOf(
                    CallStatusEnum.CONNECTING,
                    CallStatusEnum.CONNECTED,
                    CallStatusEnum.DECLINED,
                    CallStatusEnum.MISSED,
                    CallStatusEnum.BUSY,
                    CallStatusEnum.FAILED,
                    CallStatusEnum.ENDED
                )
            }
            CallStatusEnum.CONNECTING -> {
                // Connecting can transition to CONNECTED, RECONNECTING, FAILED, or ENDED
                to in listOf(
                    CallStatusEnum.CONNECTED,
                    CallStatusEnum.RECONNECTING,
                    CallStatusEnum.FAILED,
                    CallStatusEnum.ENDED
                )
            }
            CallStatusEnum.CONNECTED -> {
                // Connected call can enter RECONNECTING, ENDED, or FAILED
                to in listOf(
                    CallStatusEnum.RECONNECTING,
                    CallStatusEnum.ENDED,
                    CallStatusEnum.FAILED
                )
            }
            CallStatusEnum.RECONNECTING -> {
                // Reconnecting can recover back to CONNECTED, or terminate as FAILED / ENDED
                to in listOf(
                    CallStatusEnum.CONNECTED,
                    CallStatusEnum.ENDED,
                    CallStatusEnum.FAILED
                )
            }
            // Terminal states CANNOT transition to any active state
            CallStatusEnum.ENDED,
            CallStatusEnum.DECLINED,
            CallStatusEnum.MISSED,
            CallStatusEnum.BUSY,
            CallStatusEnum.FAILED -> {
                to == CallStatusEnum.IDLE
            }
        }
    }

    /**
     * Attempts to transition to the [newStatus], logging an error if forbidden.
     */
    fun validateTransition(current: CallStatusEnum, next: CallStatusEnum): Boolean {
        val valid = canTransition(current, next)
        if (!valid) {
            Log.e(TAG, "Illegal state transition attempted: $current -> $next rejected!")
        }
        return valid
    }
}
