package dev.mias.core.inference.react

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ToolRegistry")
class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @BeforeEach
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun `registers and executes a tool`() = runTest {
        registry.register("datetime", "Get the current time") { "12:00" }

        assertThat(registry.isRegistered("datetime")).isTrue()
        assertThat(registry.availableTools()).contains("datetime")
        assertThat(registry.get("datetime")!!.execute(emptyMap())).isEqualTo("12:00")
    }

    @Test
    fun `re-registering a name replaces the handler`() = runTest {
        registry.register("tool") { "old" }
        registry.register("tool") { "new" }

        assertThat(registry.get("tool")!!.execute(emptyMap())).isEqualTo("new")
        assertThat(registry.availableTools()).hasSize(1)
    }

    @Test
    fun `resolve matches exact names first`() {
        registry.register("web_search") { "" }
        assertThat(registry.resolve("web_search")).isEqualTo("web_search")
    }

    @Test
    fun `resolve maps a weak model's phrase onto the embedded tool name`() {
        registry.register("datetime") { "" }
        // A small model often emits a sentence instead of the exact id.
        assertThat(registry.resolve("Respond with the current datetime")).isEqualTo("datetime")
        assertThat(registry.resolve("USE DATETIME NOW")).isEqualTo("datetime")
    }

    @Test
    fun `resolve returns null for unknown actions`() {
        registry.register("calculator") { "" }
        assertThat(registry.resolve("teleport")).isNull()
        assertThat(registry.resolve("")).isNull()
    }

    @Test
    fun `describeForPrompt includes descriptions when present`() {
        registry.register("calculator", "Evaluate math expressions") { "" }
        registry.register("datetime") { "" }

        val catalogue = registry.describeForPrompt()
        assertThat(catalogue).contains("- calculator: Evaluate math expressions")
        assertThat(catalogue).contains("- datetime")
        assertThat(catalogue).doesNotContain("- datetime:")
    }
}
