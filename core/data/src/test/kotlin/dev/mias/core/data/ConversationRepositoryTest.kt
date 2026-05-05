package dev.mias.core.data

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationRepository contract")
class ConversationRepositoryTest {

    private lateinit var repo: ConversationRepository

    private val sampleConversation = Conversation(
        id = "conv-1",
        title = "Test",
        messages = listOf(
            Message("msg-1", "conv-1", Role.USER, "Hello", 1000L),
            Message("msg-2", "conv-1", Role.ASSISTANT, "Hi there!", 1001L),
        ),
        createdAt = 1000L,
        updatedAt = 1001L,
    )

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("getConversations")
    inner class GetConversationsTests {

        @Test
        fun `emits empty list initially`() = runTest {
            every { repo.getConversations() } returns flowOf(emptyList())

            val result = repo.getConversations().first()

            assertThat(result).isEmpty()
        }

        @Test
        fun `emits list of conversations`() = runTest {
            every { repo.getConversations() } returns flowOf(listOf(sampleConversation))

            val result = repo.getConversations().first()

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo("conv-1")
        }
    }

    @Nested
    @DisplayName("getConversation")
    inner class GetConversationTests {

        @Test
        fun `returns conversation by id`() = runTest {
            coEvery { repo.getConversation("conv-1") } returns MiasResult.Success(sampleConversation)

            val result = repo.getConversation("conv-1")

            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
            assertThat((result as MiasResult.Success).data.title).isEqualTo("Test")
        }

        @Test
        fun `returns Error for unknown id`() = runTest {
            coEvery { repo.getConversation("unknown") } returns MiasResult.Error("Not found")

            val result = repo.getConversation("unknown")

            assertThat(result).isInstanceOf(MiasResult.Error::class.java)
        }
    }

    @Nested
    @DisplayName("saveConversation")
    inner class SaveTests {

        @Test
        fun `saves successfully`() = runTest {
            coEvery { repo.saveConversation(any()) } returns MiasResult.Success(Unit)

            val result = repo.saveConversation(sampleConversation)

            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        }
    }

    @Nested
    @DisplayName("deleteConversation")
    inner class DeleteTests {

        @Test
        fun `deletes successfully`() = runTest {
            coEvery { repo.deleteConversation("conv-1") } returns MiasResult.Success(Unit)

            val result = repo.deleteConversation("conv-1")

            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        }

        @Test
        fun `returns Error when not found`() = runTest {
            coEvery { repo.deleteConversation("nope") } returns MiasResult.Error("Not found")

            val result = repo.deleteConversation("nope")

            assertThat(result).isInstanceOf(MiasResult.Error::class.java)
        }
    }
}
