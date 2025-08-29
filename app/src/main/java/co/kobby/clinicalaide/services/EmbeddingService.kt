package co.kobby.clinicalaide.services

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating text embeddings using TensorFlow Lite.
 * Uses a pre-trained sentence embedding model for semantic similarity.
 */
@Singleton
class EmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLoader: TFLiteModelLoader,
    private val textPreprocessor: TextPreprocessor
) {
    companion object {
        private const val TAG = "EmbeddingService"
        private const val MODEL_PATH = "models/use_lite.tflite"
        private const val EMBEDDING_DIMENSION = 384
        private const val USE_MOCK_MODEL = true // Set to false when TFLite model is available
    }
    
    private var interpreter: Interpreter? = null
    private val embeddingDimension = EMBEDDING_DIMENSION
    
    init {
        // Try to load TFLite model if available
        if (!USE_MOCK_MODEL) {
            tryLoadModel()
        }
    }
    
    /**
     * Try to load the TFLite model from assets.
     */
    private fun tryLoadModel() {
        try {
            interpreter = modelLoader.loadModelFromAssets(MODEL_PATH)
            Log.d(TAG, "TFLite model loaded successfully")
            Log.d(TAG, modelLoader.getModelDetails())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load TFLite model, falling back to mock embeddings", e)
            interpreter = null
        }
    }
    
    /**
     * Generate embedding for a text input.
     * Uses TFLite model if available, otherwise falls back to mock embeddings.
     */
    fun generateEmbedding(text: String): FloatArray {
        return if (USE_MOCK_MODEL || interpreter == null) {
            // Use mock embeddings for now
            generateMockEmbedding(text)
        } else {
            // Use real TFLite model
            generateTFLiteEmbedding(text)
        }
    }
    
    /**
     * Generate embedding using TensorFlow Lite model.
     */
    private fun generateTFLiteEmbedding(text: String): FloatArray {
        val interpreter = this.interpreter ?: return generateMockEmbedding(text)
        
        try {
            // Preprocess text
            val processedText = textPreprocessor.preprocessForUSE(text)
            
            // Prepare input buffer
            // Note: Actual implementation depends on model's input requirements
            // This is a generic implementation that may need adjustment
            val inputBuffer = ByteBuffer.allocateDirect(processedText.length * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            
            // Convert text to bytes (simplified - real model may need tokenization)
            processedText.toByteArray().forEach { byte ->
                inputBuffer.putFloat(byte.toFloat())
            }
            inputBuffer.rewind()
            
            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(EMBEDDING_DIMENSION * 4)
            outputBuffer.order(ByteOrder.nativeOrder())
            
            // Run inference
            interpreter.run(inputBuffer, outputBuffer)
            
            // Extract embeddings
            outputBuffer.rewind()
            val embedding = FloatArray(EMBEDDING_DIMENSION)
            for (i in embedding.indices) {
                embedding[i] = outputBuffer.getFloat()
            }
            
            // Normalize the embedding
            return textPreprocessor.normalizeVector(embedding)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating TFLite embedding, falling back to mock", e)
            return generateMockEmbedding(text)
        }
    }
    
    /**
     * Generate a mock embedding for testing.
     * Creates a deterministic embedding based on text features.
     */
    private fun generateMockEmbedding(text: String): FloatArray {
        val embedding = FloatArray(embeddingDimension)
        val words = text.lowercase().split("\\s+".toRegex())
        
        // Create deterministic features based on text
        for (i in embedding.indices) {
            var value = 0f
            
            // Use different text features for different dimensions
            when (i % 10) {
                0 -> value = text.length.toFloat() / 1000f
                1 -> value = words.size.toFloat() / 100f
                2 -> value = if (text.contains("malaria")) 0.8f else 0.1f
                3 -> value = if (text.contains("treatment")) 0.7f else 0.1f
                4 -> value = if (text.contains("pediatric") || text.contains("child")) 0.6f else 0.1f
                5 -> value = if (text.contains("dose") || text.contains("dosage")) 0.5f else 0.1f
                6 -> value = if (text.contains("severe") || text.contains("emergency")) 0.6f else 0.1f
                7 -> value = if (text.contains("antibiotic") || text.contains("antimalarial")) 0.5f else 0.1f
                8 -> value = if (text.contains("oral") || text.contains("iv") || text.contains("im")) 0.4f else 0.1f
                9 -> value = if (text.contains("refer") || text.contains("referral")) 0.5f else 0.1f
            }
            
            // Add some variation based on character codes
            if (i < text.length) {
                value += (text[i].code.toFloat() / 1000f) * 0.1f
            }
            
            // Normalize to [-1, 1] range
            embedding[i] = (value * 2f - 1f).coerceIn(-1f, 1f)
        }
        
        // Normalize the vector
        val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] = embedding[i] / norm
            }
        }
        
        return embedding
    }
    
    /**
     * Serialize embedding to ByteArray for storage.
     */
    fun serializeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
    
    /**
     * Deserialize embedding from ByteArray.
     */
    fun deserializeEmbedding(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val embedding = FloatArray(bytes.size / 4)
        for (i in embedding.indices) {
            embedding[i] = buffer.getFloat()
        }
        return embedding
    }
    
    /**
     * Get information about the current embedding method.
     */
    fun getEmbeddingInfo(): String {
        return if (USE_MOCK_MODEL || interpreter == null) {
            "Using mock embeddings (deterministic, ${EMBEDDING_DIMENSION} dimensions)"
        } else {
            "Using TFLite model (${EMBEDDING_DIMENSION} dimensions)\n${modelLoader.getModelDetails()}"
        }
    }
    
    /**
     * Clean up resources.
     */
    fun close() {
        modelLoader.close()
        interpreter = null
    }
    
    /**
     * Calculate similarity between two embeddings.
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator != 0f) dotProduct / denominator else 0f
    }
}