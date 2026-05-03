package dev.kid.core.common.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("VectorUtils")
class VectorUtilsTest {

    @Nested
    @DisplayName("ByteArray ↔ FloatArray round-trip")
    inner class SerializationTests {

        @Test
        fun `toByteArray and back produces identical FloatArray`() {
            val original = floatArrayOf(1.0f, -0.5f, 0.0f, 3.14159f, -2.718f)
            val bytes = original.toByteArray()
            val restored = bytes.toFloatArray()

            assertThat(restored.toList()).isEqualTo(original.toList())
        }

        @Test
        fun `empty array round-trip`() {
            val original = floatArrayOf()
            val bytes = original.toByteArray()
            val restored = bytes.toFloatArray()

            assertThat(restored).isEmpty()
        }

        @Test
        fun `single element round-trip`() {
            val original = floatArrayOf(42.0f)
            val bytes = original.toByteArray()
            val restored = bytes.toFloatArray()

            assertThat(restored.toList()).containsExactly(42.0f)
        }

        @Test
        fun `byte array size is 4x float array size`() {
            val floats = floatArrayOf(1f, 2f, 3f)
            val bytes = floats.toByteArray()

            assertThat(bytes.size).isEqualTo(floats.size * 4)
        }
    }

    @Nested
    @DisplayName("cosineSimilarity")
    inner class CosineSimilarityTests {

        @Test
        fun `identical vectors return 1,0`() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(1f, 2f, 3f)

            val sim = a.cosineSimilarity(b)

            assertThat(sim).isWithin(0.0001f).of(1.0f)
        }

        @Test
        fun `opposite vectors return -1,0`() {
            val a = floatArrayOf(1f, 0f, 0f)
            val b = floatArrayOf(-1f, 0f, 0f)

            val sim = a.cosineSimilarity(b)

            assertThat(sim).isWithin(0.0001f).of(-1.0f)
        }

        @Test
        fun `orthogonal vectors return 0,0`() {
            val a = floatArrayOf(1f, 0f, 0f)
            val b = floatArrayOf(0f, 1f, 0f)

            val sim = a.cosineSimilarity(b)

            assertThat(sim).isWithin(0.0001f).of(0.0f)
        }

        @Test
        fun `zero vector returns 0,0`() {
            val a = floatArrayOf(0f, 0f, 0f)
            val b = floatArrayOf(1f, 2f, 3f)

            val sim = a.cosineSimilarity(b)

            assertThat(sim).isEqualTo(0.0f)
        }

        @Test
        fun `both zero vectors return 0,0`() {
            val a = floatArrayOf(0f, 0f)
            val b = floatArrayOf(0f, 0f)

            val sim = a.cosineSimilarity(b)

            assertThat(sim).isEqualTo(0.0f)
        }

        @Test
        fun `mismatched dimensions throws IllegalArgumentException`() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(1f, 2f)

            assertThrows<IllegalArgumentException> {
                a.cosineSimilarity(b)
            }
        }

        @Test
        fun `similar but not identical vectors return high similarity`() {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(1.01f, 2.02f, 3.03f)

            val sim = a.cosineSimilarity(b)

            assertThat(sim).isGreaterThan(0.99f)
        }

        @Test
        fun `similarity is commutative`() {
            val a = floatArrayOf(1f, 3f, -5f)
            val b = floatArrayOf(4f, -2f, 1f)

            assertThat(a.cosineSimilarity(b)).isWithin(0.0001f).of(b.cosineSimilarity(a))
        }
    }
}
