package dev.kid.core.common.model

/** What the AI is currently doing — drives UI cognitive glow. */
enum class CognitionState {
    /** Idle, waiting for user input. */
    IDLE,

    /** Processing / reasoning / generating thought. */
    THINKING,

    /** Executing a tool action (file, notification, etc.). */
    ACTING,

    /** Waiting for external result (desktop response, tool output). */
    WAITING,

    /** Offloading to desktop brain. */
    OFFLOADING,

    /** System is thermally stressed. */
    STRESSED,

    /** Listening to voice input. */
    LISTENING,
}
