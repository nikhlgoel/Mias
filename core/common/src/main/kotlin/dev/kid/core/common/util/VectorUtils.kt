package dev.kid.core.common.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Converts a FloatArray representing a vector embedding into a ByteArray 
 * so it can be stored efficiently in SQLite/Room without JSON overhead.
 */
fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(this.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (f in this) {
        buffer.putFloat(f)
    }
    return buffer.array()
}

/**
 * Reconstructs a FloatArray from a ByteArray loaded from Room.
 */
fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val floatArray = FloatArray(this.size / 4)
    for (i in floatArray.indices) {
        floatArray[i] = buffer.float
    }
    return floatArray
}

/**
 * Calculates the Cosine Similarity between two vector embeddings.
 * Returns a value between -1.0 and 1.0. A value closer to 1.0 means highly similar.
 */
fun FloatArray.cosineSimilarity(other: FloatArray): Float {
    if (this.size != other.size) {
        throw IllegalArgumentException("Vector dimensions must match. (this: ${this.size}, other: ${other.size})")
    }
    var dotProduct = 0f
    var normA = 0f
    var normB = 0f
    for (i in this.indices) {
        dotProduct += this[i] * other[i]
        normA += this[i] * this[i]
        normB += other[i] * other[i]
    }
    if (normA == 0f || normB == 0f) return 0f
    return dotProduct / (sqrt(normA) * sqrt(normB))
}
