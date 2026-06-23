package dev.mias.core.data.hindsight

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.common.util.toByteArray
import dev.mias.core.data.db.dao.HindsightDao
import dev.mias.core.data.db.entity.MentalModelEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HindsightMemory.storeUserMemory")
class HindsightUserMemoryTest {

    private fun existingMemory(content: String, embedding: FloatArray?) = MentalModelEntity(
        id = "existing",
        content = content,
        observationIds = "",
        strength = 0.8f,
        createdAt = 0L,
        updatedAt = 0L,
        embedding = embedding?.toByteArray(),
    )

    private fun build(
        existing: List<MentalModelEntity> = emptyList(),
        embeddingResult: MiasResult<FloatArray> = MiasResult.Success(floatArrayOf(1f, 0f)),
    ): Triple<HindsightMemory, HindsightDao, EmbeddingProvider> {
        val dao = mockk<HindsightDao>(relaxed = true)
        val provider = mockk<EmbeddingProvider>()
        coEvery { dao.getAllMentalModels() } returns existing
        coEvery { provider.getEmbedding(any()) } returns embeddingResult
        val memory = HindsightMemory(dao, provider, UnconfinedTestDispatcher())
        return Triple(memory, dao, provider)
    }

    @Test
    fun `stores a novel memory`() = runTest {
        val (memory, dao, _) = build()

        val result = memory.storeUserMemory("The user's name is Nikhil.")

        assertThat((result as MiasResult.Success).data).isTrue()
        coVerify(exactly = 1) { dao.upsertMentalModel(any()) }
    }

    @Test
    fun `skips an exact text duplicate regardless of embeddings`() = runTest {
        val (memory, dao, _) = build(
            existing = listOf(existingMemory("the user's name is nikhil.", null)),
        )

        val result = memory.storeUserMemory("The user's name is Nikhil.")

        assertThat((result as MiasResult.Success).data).isFalse()
        coVerify(exactly = 0) { dao.upsertMentalModel(any()) }
    }

    @Test
    fun `skips a semantic near-duplicate`() = runTest {
        // Existing memory has an almost identical embedding direction.
        val (memory, dao, _) = build(
            existing = listOf(existingMemory("User is called Nikhil", floatArrayOf(0.99f, 0.05f))),
            embeddingResult = MiasResult.Success(floatArrayOf(1f, 0f)),
        )

        val result = memory.storeUserMemory("The user's name is Nikhil.")

        assertThat((result as MiasResult.Success).data).isFalse()
        coVerify(exactly = 0) { dao.upsertMentalModel(any()) }
    }

    @Test
    fun `stores a semantically different memory`() = runTest {
        // Orthogonal embedding → clearly a different fact.
        val (memory, dao, _) = build(
            existing = listOf(existingMemory("User is called Nikhil", floatArrayOf(0f, 1f))),
            embeddingResult = MiasResult.Success(floatArrayOf(1f, 0f)),
        )

        val result = memory.storeUserMemory("The user lives in Pune.")

        assertThat((result as MiasResult.Success).data).isTrue()
        coVerify(exactly = 1) { dao.upsertMentalModel(any()) }
    }

    @Test
    fun `still stores when no embedding model is available`() = runTest {
        val (memory, dao, _) = build(
            embeddingResult = MiasResult.Error("no embedding model"),
        )

        val result = memory.storeUserMemory("The user prefers concise replies.")

        assertThat((result as MiasResult.Success).data).isTrue()
        coVerify(exactly = 1) { dao.upsertMentalModel(any()) }
    }

    @Test
    fun `ignores blank input`() = runTest {
        val (memory, dao, _) = build()

        val result = memory.storeUserMemory("   ")

        assertThat((result as MiasResult.Success).data).isFalse()
        coVerify(exactly = 0) { dao.upsertMentalModel(any()) }
    }
}
