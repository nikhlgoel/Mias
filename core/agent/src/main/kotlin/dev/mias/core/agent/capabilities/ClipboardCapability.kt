package dev.mias.core.agent.capabilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.ToolParameter
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clipboard agent — read from and write to the system clipboard.
 */
@Singleton
class ClipboardCapability @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentCapability {

    override val name = "clipboard"

    override val description = "Read or write the system clipboard. " +
        "Use 'read' to get current clipboard content, 'write' to set it."

    override val parameters = listOf(
        ToolParameter("operation", "One of: read, write"),
        ToolParameter("text", "Text to copy to clipboard (required for write)", required = false),
    )

    override suspend fun execute(input: Map<String, String>): MiasResult<String> {
        val operation = input["operation"]
            ?: return MiasResult.Error("Missing parameter: operation")

        return runCatchingMias {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            when (operation.lowercase()) {
                "read" -> {
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0).text?.toString() ?: "(empty clipboard)"
                    } else {
                        "(empty clipboard)"
                    }
                }

                "write" -> {
                    val text = input["text"]
                        ?: throw IllegalArgumentException("Text required for write")
                    val clip = ClipData.newPlainText("Mias AI", text)
                    clipboard.setPrimaryClip(clip)
                    "Copied ${text.length} chars to clipboard"
                }

                else -> throw IllegalArgumentException("Unknown operation: $operation")
            }
        }
    }
}
