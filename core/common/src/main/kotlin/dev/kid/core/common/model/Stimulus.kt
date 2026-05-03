package dev.kid.core.common.model

/** What triggered the AI to think. */
enum class StimulusType {
    USER_MESSAGE,
    NOTIFICATION,
    CALENDAR_EVENT,
    TIMER_FIRED,
    GEOFENCE_TRIGGER,
    SYSTEM_EVENT,
    SCHEDULED_REFLECT,
    HANDOFF_RECEIVED,
}

data class Stimulus(
    val type: StimulusType,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)
