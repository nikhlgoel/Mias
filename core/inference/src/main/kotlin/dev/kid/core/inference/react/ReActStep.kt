package dev.kid.core.inference.react

import dev.kid.core.common.model.BrainState

/** One step of the ReAct (Reasoning + Acting) loop. */
sealed interface ReActStep {
    /** Internal reasoning — not shown to user directly. */
    data class Thought(val reasoning: String) : ReActStep

    /** Tool invocation decided by the model. */
    data class Action(
        val tool: String,
        val input: Map<String, String>,
    ) : ReActStep

    /** Result returned after executing an action. */
    data class Observation(val result: String) : ReActStep

    /** Final response delivered to the user. */
    data class FinalAnswer(val response: String) : ReActStep

    /** Brain was swapped mid-stream — UI may show subtle indicator. */
    data class ModelSwitch(
        val from: BrainState,
        val to: BrainState,
    ) : ReActStep

    /** Stream token chunk for progressive display. */
    data class TokenChunk(val text: String) : ReActStep
}

/** JSON schema to constrain model output into valid ReAct steps. */
object ReActSchema {
    val CONSTRAINED_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "thought": {"type": "string"},
            "action": {"type": "string", "enum": [
              "respond_user", "search_file", "check_battery",
              "send_notification", "query_hindsight", "offload_desktop",
              "set_reminder", "open_app", "execute_code", "git_operation"
            ]},
            "action_input": {"type": "object"},
            "is_final": {"type": "boolean"}
          },
          "required": ["thought", "action", "action_input", "is_final"]
        }
    """.trimIndent()
}
