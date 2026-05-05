package dev.mias.core.agent.capabilities

import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.ToolParameter
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Date/Time agent — provides current date, time, and timezone info.
 */
@Singleton
class DateTimeCapability @Inject constructor() : AgentCapability {

    override val name = "datetime"

    override val description = "Get current date, time, and timezone information. " +
        "Use 'now' for full datetime, 'date' for just date, 'time' for just time."

    override val parameters = listOf(
        ToolParameter("query", "One of: now, date, time, timezone"),
    )

    override suspend fun execute(input: Map<String, String>): MiasResult<String> {
        val query = input["query"] ?: "now"

        return runCatchingMias {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(tz)

            when (query.lowercase()) {
                "now" -> "${now.date} ${now.time} ($tz)"
                "date" -> now.date.toString()
                "time" -> now.time.toString()
                "timezone" -> tz.toString()
                else -> "${now.date} ${now.time} ($tz)"
            }
        }
    }
}
