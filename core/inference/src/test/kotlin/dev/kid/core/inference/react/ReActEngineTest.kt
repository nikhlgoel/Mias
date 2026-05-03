package dev.kid.core.inference.react

import com.google.common.truth.Truth.assertThat
import dev.kid.core.common.KidResult
import dev.kid.core.inference.InferenceEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ReActEngine")
class ReActEngineTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var reActEngine: ReActEngine
    private lateinit var mockEngine: InferenceEngine

    @BeforeEach
    fun setUp() {
        toolRegistry = ToolRegistry()
        reActEngine = ReActEngine(toolRegistry)
        mockEngine = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("max-step guard")
    inner class MaxStepGuardTests {

        @Test
        fun `stops after MAX_ITERATIONS and emits fallback FinalAnswer`() = runTest {
            // Model always returns a non-final action so the loop would run forever
            // without the max-step guard
            val nonFinalJson = """
                {"thought": "still thinking", "action": "some_tool", "action_input": {}, "is_final": false}
            """.trimIndent()

            coEvery { mockEngine.generateStream(any(), any()) } returns flowOf(
                KidResult.Success(nonFinalJson),
            )

            // Register a dummy tool so executeAction doesn't return "not available"
            toolRegistry.register("some_tool") { mapOf<String, String>() -> "ok" }

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "do something",
                maxIterations = 3, // Use small number for test speed
            ).toList()

            // The last step should be the fallback FinalAnswer from max iterations
            val finalSteps = steps.filterIsInstance<ReActStep.FinalAnswer>()
            assertThat(finalSteps).isNotEmpty()
            assertThat(finalSteps.last().response).contains("thinking about this for a while")
        }

        @Test
        fun `MAX_ITERATIONS constant is 7`() {
            assertThat(ReActEngine.MAX_ITERATIONS).isEqualTo(7)
        }
    }

    @Nested
    @DisplayName("tool output truncation")
    inner class ToolOutputTruncationTests {

        @Test
        fun `MAX_TOOL_OUTPUT_LENGTH constant is 2000`() {
            assertThat(ReActEngine.MAX_TOOL_OUTPUT_LENGTH).isEqualTo(2000)
        }

        @Test
        fun `truncates tool output longer than MAX_TOOL_OUTPUT_LENGTH`() = runTest {
            val longOutput = "x".repeat(3000)
            toolRegistry.register("verbose_tool") { longOutput }

            val actionJson = """
                {"thought": "need data", "action": "verbose_tool", "action_input": {}, "is_final": false}
            """.trimIndent()
            val finalJson = """
                {"thought": "done", "action": "respond_user", "action_input": {"response": "here"}, "is_final": true}
            """.trimIndent()

            var callCount = 0
            coEvery { mockEngine.generateStream(any(), any()) } answers {
                callCount++
                if (callCount == 1) flowOf(KidResult.Success(actionJson))
                else flowOf(KidResult.Success(finalJson))
            }

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "get data",
            ).toList()

            val observations = steps.filterIsInstance<ReActStep.Observation>()
            assertThat(observations).isNotEmpty()
            val obs = observations.first().result
            assertThat(obs.length).isLessThan(longOutput.length)
            assertThat(obs).contains("[output truncated at ${ReActEngine.MAX_TOOL_OUTPUT_LENGTH} chars]")
        }

        @Test
        fun `does not truncate tool output within limit`() = runTest {
            val shortOutput = "short result"
            toolRegistry.register("short_tool") { shortOutput }

            val actionJson = """
                {"thought": "need data", "action": "short_tool", "action_input": {}, "is_final": false}
            """.trimIndent()
            val finalJson = """
                {"thought": "done", "action": "respond_user", "action_input": {"response": "here"}, "is_final": true}
            """.trimIndent()

            var callCount = 0
            coEvery { mockEngine.generateStream(any(), any()) } answers {
                callCount++
                if (callCount == 1) flowOf(KidResult.Success(actionJson))
                else flowOf(KidResult.Success(finalJson))
            }

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "get data",
            ).toList()

            val observations = steps.filterIsInstance<ReActStep.Observation>()
            assertThat(observations).isNotEmpty()
            assertThat(observations.first().result).isEqualTo(shortOutput)
        }
    }

    @Nested
    @DisplayName("tool validation")
    inner class ToolValidationTests {

        @Test
        fun `unknown tool name returns error with available tools list`() = runTest {
            toolRegistry.register("real_tool") { "result" }

            val actionJson = """
                {"thought": "use fake", "action": "hallucinated_tool", "action_input": {}, "is_final": false}
            """.trimIndent()
            val finalJson = """
                {"thought": "ok", "action": "respond_user", "action_input": {"response": "done"}, "is_final": true}
            """.trimIndent()

            var callCount = 0
            coEvery { mockEngine.generateStream(any(), any()) } answers {
                callCount++
                if (callCount == 1) flowOf(KidResult.Success(actionJson))
                else flowOf(KidResult.Success(finalJson))
            }

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "do it",
            ).toList()

            val observations = steps.filterIsInstance<ReActStep.Observation>()
            assertThat(observations).isNotEmpty()
            assertThat(observations.first().result).contains("not available")
            assertThat(observations.first().result).contains("real_tool")
        }
    }

    @Nested
    @DisplayName("final answer")
    inner class FinalAnswerTests {

        @Test
        fun `emits FinalAnswer when is_final is true`() = runTest {
            val finalJson = """
                {"thought": "I know the answer", "action": "respond_user", "action_input": {"response": "Hello!"}, "is_final": true}
            """.trimIndent()

            coEvery { mockEngine.generateStream(any(), any()) } returns flowOf(
                KidResult.Success(finalJson),
            )

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "say hello",
            ).toList()

            val finals = steps.filterIsInstance<ReActStep.FinalAnswer>()
            assertThat(finals).hasSize(1)
            assertThat(finals.first().response).isEqualTo("Hello!")
        }

        @Test
        fun `emits FinalAnswer with thought when no response key`() = runTest {
            val finalJson = """
                {"thought": "The answer is 42", "action": "respond_user", "action_input": {}, "is_final": true}
            """.trimIndent()

            coEvery { mockEngine.generateStream(any(), any()) } returns flowOf(
                KidResult.Success(finalJson),
            )

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "meaning of life",
            ).toList()

            val finals = steps.filterIsInstance<ReActStep.FinalAnswer>()
            assertThat(finals).hasSize(1)
            assertThat(finals.first().response).isEqualTo("The answer is 42")
        }

        @Test
        fun `emits FinalAnswer on engine error`() = runTest {
            coEvery { mockEngine.generateStream(any(), any()) } returns flowOf(
                KidResult.Error("OOM: out of memory"),
            )

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "anything",
            ).toList()

            val finals = steps.filterIsInstance<ReActStep.FinalAnswer>()
            assertThat(finals).hasSize(1)
            assertThat(finals.first().response).contains("OOM: out of memory")
        }
    }
}
