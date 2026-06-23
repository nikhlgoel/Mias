package dev.mias.core.inference.react

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChatTemplate")
class ChatTemplateTest {

    @Nested
    @DisplayName("forModel")
    inner class ForModel {

        @Test
        fun `maps model families to their template`() {
            assertThat(ChatTemplate.forModel("Qwen2.5 0.5B Instruct")).isEqualTo(ChatTemplateKind.CHATML)
            assertThat(ChatTemplate.forModel("qwen2.5-coder-3b")).isEqualTo(ChatTemplateKind.CHATML)
            assertThat(ChatTemplate.forModel("Phi-3.5 Mini Instruct")).isEqualTo(ChatTemplateKind.PHI)
            assertThat(ChatTemplate.forModel("Gemma 3n E2B")).isEqualTo(ChatTemplateKind.PLAIN)
            assertThat(ChatTemplate.forModel("")).isEqualTo(ChatTemplateKind.PLAIN)
        }
    }

    @Nested
    @DisplayName("build")
    inner class Build {

        private val system = "You are a test assistant."
        private val user = "What is two plus two?"

        @Test
        fun `chatml wraps turns in im_start tokens and opens the assistant turn`() {
            val prompt = ChatTemplate.build(ChatTemplateKind.CHATML, system, user)
            assertThat(prompt).contains("<|im_start|>system\n$system<|im_end|>")
            assertThat(prompt).contains("<|im_start|>user\n$user<|im_end|>")
            assertThat(prompt).endsWith("<|im_start|>assistant\n")
        }

        @Test
        fun `phi uses its control tokens and opens the assistant turn`() {
            val prompt = ChatTemplate.build(ChatTemplateKind.PHI, system, user)
            assertThat(prompt).contains("<|system|>\n$system<|end|>")
            assertThat(prompt).contains("<|user|>\n$user<|end|>")
            assertThat(prompt).endsWith("<|assistant|>\n")
        }

        @Test
        fun `plain is the simple labelled format`() {
            val prompt = ChatTemplate.build(ChatTemplateKind.PLAIN, system, user)
            assertThat(prompt).isEqualTo("$system\n\nUser: $user\n\nAssistant:")
        }

        @Test
        fun `system and user content are never mixed into each other's turn`() {
            for (kind in ChatTemplateKind.entries) {
                val prompt = ChatTemplate.build(kind, system, user)
                // Both blocks present exactly once.
                assertThat(prompt.indexOf(system)).isEqualTo(prompt.lastIndexOf(system))
                assertThat(prompt.indexOf(user)).isEqualTo(prompt.lastIndexOf(user))
                // System comes before user, which comes before the open turn end.
                assertThat(prompt.indexOf(system)).isLessThan(prompt.indexOf(user))
            }
        }
    }
}
