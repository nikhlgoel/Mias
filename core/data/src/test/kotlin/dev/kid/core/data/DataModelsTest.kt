package dev.kid.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Data models")
class DataModelsTest {

    @Test
    fun `Conversation data class equality`() {
        val a = Conversation("1", "Title", emptyList(), 100L, 200L)
        val b = Conversation("1", "Title", emptyList(), 100L, 200L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `Message data class equality`() {
        val a = Message("m1", "c1", Role.USER, "Hello", 100L)
        val b = Message("m1", "c1", Role.USER, "Hello", 100L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `Role enum values`() {
        assertThat(Role.values()).asList().containsExactly(Role.USER, Role.ASSISTANT)
    }

    @Test
    fun `Conversation with messages`() {
        val msgs = listOf(
            Message("m1", "c1", Role.USER, "Hi", 1L),
            Message("m2", "c1", Role.ASSISTANT, "Hello!", 2L),
        )
        val conv = Conversation("c1", "Chat", msgs, 1L, 2L)
        assertThat(conv.messages).hasSize(2)
        assertThat(conv.messages[0].role).isEqualTo(Role.USER)
        assertThat(conv.messages[1].role).isEqualTo(Role.ASSISTANT)
    }
}
