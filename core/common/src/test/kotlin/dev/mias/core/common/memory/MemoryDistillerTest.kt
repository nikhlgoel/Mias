package dev.mias.core.common.memory

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MemoryDistiller")
class MemoryDistillerTest {

    @Nested
    @DisplayName("containsPersonalSignal")
    inner class Gate {

        @Test
        fun `fires on durable personal statements`() {
            assertThat(MemoryDistiller.containsPersonalSignal("My name is Nikhil")).isTrue()
            assertThat(MemoryDistiller.containsPersonalSignal("I'm building an Android app")).isTrue()
            assertThat(MemoryDistiller.containsPersonalSignal("I prefer short answers")).isTrue()
            assertThat(MemoryDistiller.containsPersonalSignal("remember that my dog is called Rex")).isTrue()
            assertThat(MemoryDistiller.containsPersonalSignal("I work as a nurse in Pune")).isTrue()
        }

        @Test
        fun `stays quiet on ordinary questions`() {
            assertThat(MemoryDistiller.containsPersonalSignal("What is the capital of France?")).isFalse()
            assertThat(MemoryDistiller.containsPersonalSignal("Explain quantum entanglement")).isFalse()
            assertThat(MemoryDistiller.containsPersonalSignal("write a python script to sort a list")).isFalse()
            assertThat(MemoryDistiller.containsPersonalSignal("")).isFalse()
        }

        @Test
        fun `matching is case and whitespace tolerant`() {
            assertThat(MemoryDistiller.containsPersonalSignal("MY   NAME is Sam")).isTrue()
        }
    }

    @Nested
    @DisplayName("parse")
    inner class Parse {

        @Test
        fun `accepts clean facts one per line`() {
            val out = MemoryDistiller.parse(
                "The user's name is Nikhil.\nThe user is building an Android app called Mias.",
            )
            assertThat(out).containsExactly(
                "The user's name is Nikhil.",
                "The user is building an Android app called Mias.",
            ).inOrder()
        }

        @Test
        fun `strips bullets, numbering and a Facts echo`() {
            val out = MemoryDistiller.parse(
                "Facts:\n- The user lives in Pune.\n2) The user prefers concise replies.\n* The user has a dog named Rex.",
            )
            assertThat(out).containsExactly(
                "The user lives in Pune.",
                "The user prefers concise replies.",
                "The user has a dog named Rex.",
            ).inOrder()
        }

        @Test
        fun `NONE and refusal phrasings yield nothing`() {
            assertThat(MemoryDistiller.parse("NONE")).isEmpty()
            assertThat(MemoryDistiller.parse("none.")).isEmpty()
            assertThat(MemoryDistiller.parse("Nothing worth remembering")).isEmpty()
            assertThat(MemoryDistiller.parse("")).isEmpty()
            assertThat(MemoryDistiller.parse("   \n  \n")).isEmpty()
        }

        @Test
        fun `drops fragments, json leaks and overlong ramble`() {
            val ramble = "x".repeat(300)
            val out = MemoryDistiller.parse(
                "ok\n{\"thought\":\"json leak\"}\n$ramble\nThe user likes hiking.",
            )
            assertThat(out).containsExactly("The user likes hiking.")
        }

        @Test
        fun `dedupes case-insensitively and caps at the maximum`() {
            val out = MemoryDistiller.parse(
                "The user likes tea.\nTHE USER LIKES TEA.\nThe user is 30.\n" +
                    "The user codes in Kotlin.\nThe user plays chess.",
            )
            assertThat(out).hasSize(MemoryDistiller.MAX_MEMORIES)
            assertThat(out).containsExactly(
                "The user likes tea.",
                "The user is 30.",
                "The user codes in Kotlin.",
            ).inOrder()
        }
    }

    @Nested
    @DisplayName("extractExplicitMemory")
    inner class Explicit {

        @Test
        fun `pulls inline content from remember-that forms`() {
            assertThat(MemoryDistiller.extractExplicitMemory("Remember that my birthday is June 5"))
                .isEqualTo("my birthday is June 5")
            assertThat(MemoryDistiller.extractExplicitMemory("Can you remember that I'm allergic to nuts?"))
                .isEqualTo("I'm allergic to nuts?")
            assertThat(MemoryDistiller.extractExplicitMemory("note that the wifi password is hunter2"))
                .isEqualTo("the wifi password is hunter2")
            assertThat(MemoryDistiller.extractExplicitMemory("Remember: I use Arch Linux"))
                .isEqualTo("I use Arch Linux")
            assertThat(MemoryDistiller.extractExplicitMemory("Don't forget that the meeting is on Monday"))
                .isEqualTo("the meeting is on Monday")
            assertThat(MemoryDistiller.extractExplicitMemory("keep in mind I prefer tabs over spaces"))
                .isEqualTo("I prefer tabs over spaces")
        }

        @Test
        fun `bare commands return blank to signal use-previous-content`() {
            assertThat(MemoryDistiller.extractExplicitMemory("save this to memory")).isEmpty()
            assertThat(MemoryDistiller.extractExplicitMemory("Add that to your memory")).isEmpty()
            assertThat(MemoryDistiller.extractExplicitMemory("remember this")).isEmpty()
        }

        @Test
        fun `ordinary messages return null`() {
            assertThat(MemoryDistiller.extractExplicitMemory("What do you remember about me?")).isNull()
            assertThat(MemoryDistiller.extractExplicitMemory("I remember when phones had keyboards")).isNull()
            assertThat(MemoryDistiller.extractExplicitMemory("What's the weather like?")).isNull()
            assertThat(MemoryDistiller.extractExplicitMemory("")).isNull()
        }

        @Test
        fun `inline content is capped`() {
            val long = "remember that " + "a".repeat(500)
            assertThat(MemoryDistiller.extractExplicitMemory(long)!!.length)
                .isAtMost(MemoryDistiller.EXPLICIT_MAX_CHARS)
        }
    }

    @Nested
    @DisplayName("buildPrompt")
    inner class Prompt {

        @Test
        fun `includes both turns and the contract`() {
            val p = MemoryDistiller.buildPrompt("I'm Nikhil and I love astronomy", "Nice to meet you!")
            assertThat(p).contains("I'm Nikhil and I love astronomy")
            assertThat(p).contains("Nice to meet you!")
            assertThat(p).contains("NONE")
            assertThat(p).endsWith("Facts:")
        }

        @Test
        fun `caps very long inputs`() {
            val long = "a".repeat(5_000)
            val p = MemoryDistiller.buildPrompt(long, long)
            assertThat(p.length).isLessThan(2_500)
        }
    }
}
