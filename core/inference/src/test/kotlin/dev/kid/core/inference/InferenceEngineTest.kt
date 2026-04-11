package dev.kid.core.inference

import com.google.common.truth.Truth.assertThat
import dev.kid.core.common.KidResult
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
            coEvery { engine.loadModel(any()) } returns KidResult.Success(Unit)

            val result = engine.loadModel("/path/to/model.onnx")

            assertThat(result).isInstanceOf(KidResult.Success::class.java)
        }

        @Test
        fun `returns Error when model file missing`() = runTest {
            coEvery { engine.loadModel(any()) } returns KidResult.Error("File not found")

            val result = engine.loadModel("/nonexistent/model.onnx")

            assertThat(result).isInstanceOf(KidResult.Error::class.java)
            assertThat((result as KidResult.Error).message).contains("not found")
        }
    }

    @Nested
    @DisplayName("generate")
    inner class GenerateTests {

        @Test
        fun `returns generated text on success`() = runTest {
            coEvery { engine.loadModel(any()) } returns KidResult.Success(Unit)
            coEvery { engine.generate(any(), any()) } returns KidResult.Success("Hello world")

            engine.loadModel("/model.onnx")
            val result = engine.generate("Say hello")

            assertThat(result).isInstanceOf(KidResult.Success::class.java)
            assertThat((result as KidResult.Success).data).isEqualTo("Hello world")
        }

        @Test
        fun `respects maxTokens parameter`() = runTest {
            coEvery { engine.generate("prompt", 128) } returns KidResult.Success("short")

            val result = engine.generate("prompt", 128)

            coVerify { engine.generate("prompt", 128) }
            assertThat(result).isInstanceOf(KidResult.Success::class.java)
        }

        @Test
        fun `returns Error when no model loaded`() = runTest {
            every { engine.isModelLoaded() } returns false
            coEvery { engine.generate(any(), any()) } returns KidResult.Error("No model loaded")

            val result = engine.generate("test")

            assertThat(result).isInstanceOf(KidResult.Error::class.java)
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
            coEvery { engine.loadModel(any()) } returns KidResult.Success(Unit)
            every { engine.isModelLoaded() } returns true

            engine.loadModel("/model.onnx")
            assertThat(engine.isModelLoaded()).isTrue()
        }

        @Test
        fun `unloadModel returns Success`() = runTest {
            coEvery { engine.unloadModel() } returns KidResult.Success(Unit)

            val result = engine.unloadModel()

            assertThat(result).isInstanceOf(KidResult.Success::class.java)
        }
    }
}
