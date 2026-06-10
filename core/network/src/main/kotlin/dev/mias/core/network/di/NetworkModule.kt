package dev.mias.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mias.core.network.MeshClient
import dev.mias.core.network.mesh.TailscaleMeshClient
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Shared client for the lightweight callers: web search/fetch/answer, HF
     * registry lookups, MCP. Model downloads use their own qualified client
     * (`@ModelHubHttpClient`), so the per-request timeout here can stay tight
     * without breaking multi-GB transfers.
     */
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

        // A page that takes >30s isn't worth grounding on — on-device answers
        // must stay interactive. Connect/socket limits catch dead hosts fast
        // instead of letting one stuck source eat the whole request budget.
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }

        // Transient-failure retry, deliberately narrow: idempotent GETs only
        // (an MCP POST must never be silently replayed), only on 5xx/429 or
        // network-layer IOExceptions, with exponential backoff. Whole-request
        // timeouts are explicitly excluded — a source that already burned its
        // full time budget must NOT be retried (callers fall back to snippets);
        // only fast failures (connect refused/reset) are worth a second try.
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

    @Provides
    @Singleton
    fun provideMeshClient(impl: TailscaleMeshClient): MeshClient = impl

    private const val REQUEST_TIMEOUT_MS = 30_000L
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val SOCKET_TIMEOUT_MS = 15_000L
    private const val MAX_RETRIES = 2

    /** Exponent base for retry backoff (delay ≈ base^attempt seconds), not a duration. */
    private const val RETRY_EXP_BASE = 1.5
}
