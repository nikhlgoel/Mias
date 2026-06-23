package dev.mias.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Integrity checks for the built-in personas: these feed straight into the
 * system prompt and the persona picker, so a duplicate id or a blank prompt
 * would corrupt persona selection or silently neuter the assistant.
 */
class PersonasTest {

    @Test
    fun `ids are unique`() {
        val ids = Personas.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `every persona is fully specified`() {
        Personas.ALL.forEach { persona ->
            assertThat(persona.id).isNotEmpty()
            assertThat(persona.name).isNotEmpty()
            assertThat(persona.tagline).isNotEmpty()
            assertThat(persona.systemPrompt).isNotEmpty()
            // Each prompt must establish the assistant identity.
            assertThat(persona.systemPrompt).contains("Mias")
        }
    }

    @Test
    fun `default persona is part of the list`() {
        assertThat(Personas.ALL).contains(Personas.DEFAULT)
    }

    @Test
    fun `byId resolves known ids and falls back to default`() {
        assertThat(Personas.byId("coder")).isEqualTo(Personas.CODER)
        assertThat(Personas.byId("does-not-exist")).isEqualTo(Personas.DEFAULT)
        assertThat(Personas.byId(null)).isEqualTo(Personas.DEFAULT)
        assertThat(Personas.byId("")).isEqualTo(Personas.DEFAULT)
    }
}
