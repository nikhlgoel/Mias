package dev.mias.mobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Singleton

/**
 * Provides the shared Ktor [HttpClient] into the Hilt graph.
 *
 * Relocated to the RN app at the S7 cutover from the deleted `core/network`
 * module. Consumers (web search/fetch/answer in `core/agent`, HuggingFace
 * registry lookups in `core/model-hub`) inject this — model downloads use their
 * own qualified client, so this timeout can stay tight. The legacy Tailscale
 * `MeshClient` / Kotlin `McpClient` from the old module were dropped (the RN app
 * offloads via the TypeScript MCP client and the Bridge).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        // Narrow, safe retry: idempotent GETs only (an MCP/HF POST must never be
        // silently replayed), on 5xx/429 or fast network IO errors — never on a
        // whole-request timeout that already burned its budget.
        install(HttpRequestRetry) {
            maxRetries = MAX_RETRIES
            retryIf { request, response ->
                request.method == HttpMethod.Get &&
                    (response.status.value in 500..599 || response.status.value == 429)
            }
            retryOnExceptionIf { request, cause ->
                request.method == HttpMethod.Get &&
                    cause is IOException &&
                    cause !is HttpRequestTimeoutException
            }
            exponentialDelay(base = RETRY_EXP_BASE)
        }
        engine {
            requestTimeout = REQUEST_TIMEOUT_MS
        }
    }

    private const val REQUEST_TIMEOUT_MS = 30_000L
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val SOCKET_TIMEOUT_MS = 15_000L
    private const val MAX_RETRIES = 2
    private const val RETRY_EXP_BASE = 1.5
}
