package dev.mias.core.inference

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("InferenceEngine contract")
class InferenceEngineTest {

    private lateinit var engine: InferenceEngine

    @BeforeEach
    fun setUp() {
        engine = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("loadModel")
    inner class LoadModelTests {

        @Test
        fun `returns Success when model loads`() = runTest {
            coEvery { engine.loadModel(any()) } returns MiasResult.Success(Unit)

            val result = engine.loadModel("/path/to/model.onnx")

            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        }

        @Test
        fun `returns Error when model file missing`() = runTest {
            coEvery { engine.loadModel(any()) } returns MiasResult.Error("File not found")

            val result = engine.loadModel("/nonexistent/model.onnx")

            assertThat(result).isInstanceOf(MiasResult.Error::class.java)
            assertThat((result as MiasResult.Error).message).contains("not found")
        }
    }

    @Nested
    @DisplayName("generate")
    inner class GenerateTests {

        @Test
        fun `returns generated text on success`() = runTest {
            coEvery { engine.loadModel(any()) } returns MiasResult.Success(Unit)
            coEvery { engine.generate(any(), any()) } returns MiasResult.Success("Hello world")

            engine.loadModel("/model.onnx")
            val result = engine.generate("Say hello")

            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
            assertThat((result as MiasResult.Success).data).isEqualTo("Hello world")
        }

        @Test
        fun `respects maxTokens parameter`() = runTest {
            coEvery { engine.generate("prompt", 128) } returns MiasResult.Success("short")

            val result = engine.generate("prompt", 128)

            coVerify { engine.generate("prompt", 128) }
            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        }

        @Test
        fun `returns Error when no model loaded`() = runTest {
            every { engine.isModelLoaded() } returns false
            coEvery { engine.generate(any(), any()) } returns MiasResult.Error("No model loaded")

            val result = engine.generate("test")

            assertThat(result).isInstanceOf(MiasResult.Error::class.java)
        }
    }

    @Nested
    @DisplayName("lifecycle")
    inner class LifecycleTests {

        @Test
        fun `isModelLoaded returns false initially`() {
            every { engine.isModelLoaded() } returns false
            assertThat(engine.isModelLoaded()).isFalse()
        }

        @Test
        fun `isModelLoaded returns true after load`() = runTest {
            coEvery { engine.loadModel(any()) } returns MiasResult.Success(Unit)
            every { engine.isModelLoaded() } returns true

            engine.loadModel("/model.onnx")
            assertThat(engine.isModelLoaded()).isTrue()
        }

        @Test
        fun `unloadModel returns Success`() = runTest {
            coEvery { engine.unloadModel() } returns MiasResult.Success(Unit)

            val result = engine.unloadModel()

            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        }
    }
}
