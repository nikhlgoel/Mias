package dev.mias.core.inference.engine

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.ModelRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AutoEmbeddingProvider")
class AutoEmbeddingProviderTest {

    private lateinit var engine: EmbeddingEngine
    private lateinit var modelManager: ModelManager
    private lateinit var provider: AutoEmbeddingProvider

    @BeforeEach
    fun setUp() {
        engine = mockk()
        modelManager = mockk()
        provider = AutoEmbeddingProvider(engine, modelManager)
    }

    @Test
    fun `caches short query embeddings — second call skips the engine`() = runTest {
        every { engine.isModelLoaded() } returns true
        coEvery { engine.getEmbedding("what is the latest news") } returns
            MiasResult.Success(floatArrayOf(1f, 2f, 3f))

        val first = provider.getEmbedding("what is the latest news")
        val second = provider.getEmbedding("what is the latest news")

        assertThat(first).isInstanceOf(MiasResult.Success::class.java)
        assertThat((second as MiasResult.Success).data.toList())
            .containsExactly(1f, 2f, 3f).inOrder()
        coVerify(exactly = 1) { engine.getEmbedding("what is the latest news") }
    }

    @Test
    fun `does not cache long texts like document chunks`() = runTest {
        val chunk = "x".repeat(800)
        every { engine.isModelLoaded() } returns true
        coEvery { engine.getEmbedding(chunk) } returns MiasResult.Success(floatArrayOf(1f))

        provider.getEmbedding(chunk)
        provider.getEmbedding(chunk)

        coVerify(exactly = 2) { engine.getEmbedding(chunk) }
    }

    @Test
    fun `errors are not cached — the next call retries the engine`() = runTest {
        every { engine.isModelLoaded() } returns true
        coEvery { engine.getEmbedding("q") } returnsMany listOf(
            MiasResult.Error("transient failure"),
            MiasResult.Success(floatArrayOf(9f)),
        )

        val first = provider.getEmbedding("q")
        val second = provider.getEmbedding("q")

        assertThat(first).isInstanceOf(MiasResult.Error::class.java)
        assertThat(second).isInstanceOf(MiasResult.Success::class.java)
        coVerify(exactly = 2) { engine.getEmbedding("q") }
    }

    @Test
    fun `reports a clear error when no embedding model is installed`() = runTest {
        every { engine.isModelLoaded() } returns false
        coEvery { modelManager.getModelForRole(ModelRole.EMBEDDING) } returns null

        val result = provider.getEmbedding("anything")

        assertThat(result).isInstanceOf(MiasResult.Error::class.java)
        assertThat((result as MiasResult.Error).message).contains("embedding model")
        coVerify(exactly = 0) { engine.getEmbedding(any()) }
    }
}
