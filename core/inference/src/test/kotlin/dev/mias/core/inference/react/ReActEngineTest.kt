package dev.mias.core.inference.react

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import dev.mias.core.inference.InferenceEngine
import dev.mias.core.inference.orchestrator.AgentReliabilitySink
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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
        reActEngine = ReActEngine(toolRegistry, Dispatchers.Unconfined)
        mockEngine = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("max-step guard")
    inner class MaxStepGuardTests {

        @Test
        fun `stops after MAX_ITERATIONS and emits fallback FinalAnswer`() = runTest {
            // Model always returns a non-final action so the loop would run forever
            // without the max-step guard.
            val nonFinalJson = """
                {"thought": "still thinking", "action": "some_tool", "action_input": {}, "is_final": false, "should_say": ""}
            """.trimIndent()

            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Success(nonFinalJson),
            )

            // Register a dummy tool so executeAction doesn't return "not available".
            toolRegistry.register("some_tool") { "ok" }

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "do something",
                maxIterations = 3,
            ).toList()

            val finalSteps = steps.filterIsInstance<ReActStep.FinalAnswer>()
            assertThat(finalSteps).isNotEmpty()
            // The loop didn't converge → it recovers with a clean plain pass.
            // The recovered answer must be non-empty and free of raw JSON.
            val recovered = finalSteps.last().response
            assertThat(recovered).isNotEmpty()
            assertThat(recovered).doesNotContain("is_final")
            assertThat(recovered).doesNotContain("should_say")
        }

        @Test
        fun `MAX_ITERATIONS constant is 3`() {
            assertThat(ReActEngine.MAX_ITERATIONS).isEqualTo(3)
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
                {"thought": "need data", "action": "verbose_tool", "action_input": {}, "is_final": false, "should_say": ""}
            """.trimIndent()
            val finalJson = """
                {"thought": "done", "action": "respond_user", "action_input": {"response": "here"}, "is_final": true, "should_say": "here"}
            """.trimIndent()

            var callCount = 0
            every { mockEngine.generateStream(any(), any(), any()) } answers {
                callCount++
                if (callCount == 1) flowOf(MiasResult.Success(actionJson))
                else flowOf(MiasResult.Success(finalJson))
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
                {"thought": "need data", "action": "short_tool", "action_input": {}, "is_final": false, "should_say": ""}
            """.trimIndent()
            val finalJson = """
                {"thought": "done", "action": "respond_user", "action_input": {"response": "here"}, "is_final": true, "should_say": "here"}
            """.trimIndent()

            var callCount = 0
            every { mockEngine.generateStream(any(), any(), any()) } answers {
                callCount++
                if (callCount == 1) flowOf(MiasResult.Success(actionJson))
                else flowOf(MiasResult.Success(finalJson))
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
                {"thought": "use fake", "action": "hallucinated_tool", "action_input": {}, "is_final": false, "should_say": ""}
            """.trimIndent()
            val finalJson = """
                {"thought": "ok", "action": "respond_user", "action_input": {"response": "done"}, "is_final": true, "should_say": "done"}
            """.trimIndent()

            var callCount = 0
            every { mockEngine.generateStream(any(), any(), any()) } answers {
                callCount++
                if (callCount == 1) flowOf(MiasResult.Success(actionJson))
                else flowOf(MiasResult.Success(finalJson))
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
        fun `emits FinalAnswer from should_say when is_final is true`() = runTest {
            val finalJson = """
                {"thought": "I know the answer", "action": "respond_user", "action_input": {}, "is_final": true, "should_say": "Hello!"}
            """.trimIndent()

            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Success(finalJson),
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
        fun `emits FinalAnswer with thought when no should_say or response key`() = runTest {
            val finalJson = """
                {"thought": "The answer is 42", "action": "respond_user", "action_input": {}, "is_final": true, "should_say": ""}
            """.trimIndent()

            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Success(finalJson),
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
            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Error("OOM: out of memory"),
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

    @Nested
    @DisplayName("deterministic mode (allowToolCalls = false)")
    inner class DeterministicModeTests {

        @Test
        fun `streams a plain reply and never advertises tools`() = runTest {
            val prompts = mutableListOf<String>()
            every { mockEngine.generateStream(any(), any(), any()) } answers {
                prompts.add(firstArg())
                flowOf(MiasResult.Success("The capital of France is Paris."))
            }
            // A registered tool must NOT leak into the prompt in deterministic mode.
            toolRegistry.register("web_search", "Search the web") { "results" }

            val steps = reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "test",
                userPrompt = "What is the capital of France?",
                allowToolCalls = false,
            ).toList()

            val finals = steps.filterIsInstance<ReActStep.FinalAnswer>()
            assertThat(finals).hasSize(1)
            assertThat(finals.first().response).isEqualTo("The capital of France is Paris.")
            assertThat(prompts).hasSize(1)
            assertThat(prompts.single()).doesNotContain("Available tools")
            assertThat(prompts.single()).doesNotContain("web_search")
        }

        @Test
        fun `never executes a tool`() = runTest {
            var toolCalled = false
            toolRegistry.register("web_search") {
                toolCalled = true
                "results"
            }
            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Success("answer"),
            )

            reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "s",
                userPrompt = "u",
                allowToolCalls = false,
            ).toList()

            assertThat(toolCalled).isFalse()
        }
    }

    @Nested
    @DisplayName("reliability sink")
    inner class ReliabilitySinkTests {

        @Test
        fun `records success when the loop converges`() = runTest {
            val outcomes = mutableListOf<Boolean>()
            val sink = AgentReliabilitySink { outcomes.add(it) }
            val finalJson = """
                {"thought": "ok", "action": "respond_user", "action_input": {}, "is_final": true, "should_say": "hi"}
            """.trimIndent()
            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Success(finalJson),
            )

            reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "s",
                userPrompt = "u",
                reliabilitySink = sink,
            ).toList()

            assertThat(outcomes).containsExactly(true)
        }

        @Test
        fun `records failure when the loop never converges`() = runTest {
            val outcomes = mutableListOf<Boolean>()
            val sink = AgentReliabilitySink { outcomes.add(it) }
            val nonFinal = """
                {"thought": "...", "action": "some_tool", "action_input": {}, "is_final": false, "should_say": ""}
            """.trimIndent()
            toolRegistry.register("some_tool") { "ok" }
            every { mockEngine.generateStream(any(), any(), any()) } returns flowOf(
                MiasResult.Success(nonFinal),
            )

            reActEngine.execute(
                engine = mockEngine,
                systemPrompt = "s",
                userPrompt = "u",
                maxIterations = 2,
                reliabilitySink = sink,
            ).toList()

            assertThat(outcomes).containsExactly(false)
        }
    }
}
