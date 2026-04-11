package dev.kid.core.agent.capabilities

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.agent.AgentCapability
import dev.kid.core.agent.ParameterType
import dev.kid.core.agent.ToolParameter
import dev.kid.core.common.KidResult
import dev.kid.core.common.runCatchingKid
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppLaunchCapability — launches installed apps and opens URLs.
 *
 * Can open URLs in browser, deep-link into apps, or launch
 * apps by their package name. Respects Android security boundaries.
 */
@Singleton
class AppLaunchCapability @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentCapability {

    override val name = "open_url"
    override val description = "Open a URL in the browser or deep-link into an app"
    override val parameters = listOf(
        ToolParameter("url", "The URL or deep-link URI to open", required = true, type = ParameterType.STRING),
    )

    override suspend fun execute(input: Map<String, String>): KidResult<String> =
        runCatchingKid {
            val url = input["url"]?.trim()
                ?: return@runCatchingKid KidResult.Failure(IllegalArgumentException("Missing: url"))

            // Validate scheme — only allow http, https, and common deep-link schemes
            val uri = Uri.parse(url)
            val allowedSchemes = setOf("http", "https", "mailto", "tel", "geo")
            if (uri.scheme !in allowedSchemes) {
                return@runCatchingKid KidResult.Failure(
                    SecurityException("Blocked scheme: ${uri.scheme}. Only $allowedSchemes allowed."),
                )
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            KidResult.Success("Opened: $url")
        }.fold({ it }, { KidResult.Failure(it) })
}
