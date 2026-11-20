package com.antigravity.dualcam.model

/**
 * Finite state machine for recording engine.
 */
enum class RecordingState {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    FINALIZING,
    ERROR
}
