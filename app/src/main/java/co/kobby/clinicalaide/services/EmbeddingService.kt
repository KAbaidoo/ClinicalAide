package co.kobby.clinicalaide.services

import android.content.Context
import android.util.Log
import co.kobby.clinicalaide.BuildConfig
import co.kobby.clinicalaide.config.EmbeddingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

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
        private const val QUERY_EMBEDDINGS_PATH = "query_embeddings.json"
    }
    
    data class QueryEmbedding(
        val query: String,
        val category: String,
        val embedding: List<Float>
    )
    
    private var interpreter: Interpreter? = null
    private val embeddingDimension = EMBEDDING_DIMENSION
    private val queryEmbeddings: Map<String, FloatArray> by lazy { loadQueryEmbeddings() }
    private val productionService = ProductionEmbeddingService(context, textPreprocessor)
    
    init {
        // Load pre-computed query embeddings instead of TFLite model
        Log.d(TAG, "Loading pre-computed query embeddings")
    }
    
    /**
     * Load pre-computed query embeddings from JSON file.
     */
    private fun loadQueryEmbeddings(): Map<String, FloatArray> {
        val embeddings = mutableMapOf<String, FloatArray>()
        
        try {
            Log.d(TAG, "Attempting to load query embeddings from: $QUERY_EMBEDDINGS_PATH")
            context.assets.open(QUERY_EMBEDDINGS_PATH).use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val type = object : TypeToken<List<QueryEmbedding>>() {}.type
                val queryList: List<QueryEmbedding> = Gson().fromJson(reader, type)
                
                Log.d(TAG, "Parsed ${queryList.size} query embeddings from JSON")
                
                queryList.forEach { item ->
                    embeddings[item.query.lowercase()] = item.embedding.toFloatArray()
                }
                
                Log.d(TAG, "Successfully loaded ${embeddings.size} pre-computed query embeddings")
                
                // Log some sample queries for debugging
                val sampleQueries = embeddings.keys.take(5)
                Log.d(TAG, "Sample queries loaded: $sampleQueries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load query embeddings: ${e.message}", e)
            Log.e(TAG, "Falling back to mock embeddings")
        }
        
        return embeddings
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
     * First checks for pre-computed embeddings, then falls back to generating.
     */
    fun generateEmbedding(text: String): FloatArray {
        Log.d(TAG, "generateEmbedding called for: $text")
        Log.d(TAG, "Current embedding mode: ${EmbeddingConfig.currentMode}")
        
        return when (EmbeddingConfig.currentMode) {
            EmbeddingConfig.Mode.PRODUCTION -> {
                // Production mode: Real-time generation
                try {
                    if (productionService.isReady()) {
                        Log.d(TAG, "Using production embedding service")
                        productionService.generateEmbedding(text)
                    } else {
                        Log.e(TAG, "Production service not ready")
                        throw IllegalStateException(
                            "Production embedding service is not initialized. " +
                            "Cannot generate embeddings in production mode."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate production embedding", e)
                    throw e // Re-throw in production mode - no fallback allowed
                }
            }
            
            EmbeddingConfig.Mode.PRE_COMPUTED -> {
                // Pre-computed mode: Use cached embeddings
                Log.d(TAG, "Using pre-computed embeddings (${queryEmbeddings.size} entries)")
                generatePreComputedEmbedding(text)
            }
            
            EmbeddingConfig.Mode.MOCK -> {
                // Mock mode: Generate fake embeddings for testing
                Log.d(TAG, "Using mock embeddings (development mode)")
                generateMockEmbedding(text)
            }
        }
    }
    
    /**
     * Generate embedding using pre-computed embeddings.
     */
    private fun generatePreComputedEmbedding(text: String): FloatArray {
        // First, check if we have a pre-computed embedding for this exact query
        val normalizedText = text.trim().lowercase()
        queryEmbeddings[normalizedText]?.let { 
            Log.d(TAG, "Using pre-computed embedding for: $normalizedText")
            return it 
        }
        
        // Try to find a similar query
        val similarQuery = findSimilarQuery(normalizedText)
        if (similarQuery != null) {
            Log.d(TAG, "Using embedding from similar query: $similarQuery for: $normalizedText")
            return queryEmbeddings[similarQuery]!!
        }
        
        // If no pre-computed embedding, generate composite
        return generateCompositeEmbedding(text)
    }
    
    /**
     * Find a similar query from pre-computed embeddings.
     */
    private fun findSimilarQuery(text: String): String? {
        val words = text.split("\\s+".toRegex())
        
        // Look for queries that contain the main keywords
        return queryEmbeddings.keys.find { query ->
            words.any { word -> 
                word.length > 3 && query.contains(word)
            }
        }
    }
    
    /**
     * Generate a composite embedding by averaging embeddings of related terms.
     */
    private fun generateCompositeEmbedding(text: String): FloatArray {
        val words = text.lowercase().split("\\s+".toRegex())
        val relatedEmbeddings = mutableListOf<FloatArray>()
        
        // Find embeddings for individual words or related queries
        words.forEach { word ->
            if (word.length > 3) {
                queryEmbeddings.entries
                    .filter { it.key.contains(word) }
                    .take(3)
                    .forEach { relatedEmbeddings.add(it.value) }
            }
        }
        
        // If we found related embeddings, average them
        if (relatedEmbeddings.isNotEmpty()) {
            Log.d(TAG, "Generating composite embedding from ${relatedEmbeddings.size} related terms")
            val result = FloatArray(EMBEDDING_DIMENSION)
            
            for (i in 0 until EMBEDDING_DIMENSION) {
                result[i] = relatedEmbeddings.map { it[i] }.average().toFloat()
            }
            
            // Normalize the result
            return textPreprocessor.normalizeVector(result)
        }
        
        // No fallback to mock in non-mock mode
        if (EmbeddingConfig.currentMode == EmbeddingConfig.Mode.MOCK) {
            Log.w(TAG, "No related embeddings found, using mock for: $text")
            return generateMockEmbedding(text)
        } else {
            // In production or pre-computed mode, throw exception instead of using mock
            throw IllegalStateException(
                "Cannot generate embedding for text: '$text'. " +
                "No related embeddings found and mock fallback is not allowed in ${EmbeddingConfig.currentMode} mode."
            )
        }
    }
    
    /**
     * Generate embedding using TensorFlow Lite model.
     */
    private fun generateTFLiteEmbedding(text: String): FloatArray {
        val interpreter = this.interpreter ?: run {
            // Never fall back to mock in production
            if (EmbeddingConfig.currentMode == EmbeddingConfig.Mode.MOCK) {
                return generateMockEmbedding(text)
            } else {
                throw IllegalStateException(
                    "TFLite interpreter not initialized. Cannot generate embeddings without a model."
                )
            }
        }
        
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
            Log.e(TAG, "Error generating TFLite embedding", e)
            // Never fall back to mock in production
            if (EmbeddingConfig.currentMode == EmbeddingConfig.Mode.MOCK) {
                return generateMockEmbedding(text)
            } else {
                throw RuntimeException(
                    "Failed to generate TFLite embedding for text: '$text'",
                    e
                )
            }
        }
    }
    
    /**
     * Generate a mock embedding for testing.
     * Creates a deterministic embedding based on text features.
     * WARNING: This should NEVER be called in production mode.
     */
    private fun generateMockEmbedding(text: String): FloatArray {
        // Safety check: Ensure we're not in production
        if (BuildConfig.IS_PRODUCTION_BUILD && EmbeddingConfig.currentMode != EmbeddingConfig.Mode.MOCK) {
            throw IllegalStateException(
                "CRITICAL: Attempted to generate mock embedding in production build! " +
                "Current mode: ${EmbeddingConfig.currentMode}"
            )
        }
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
        return when (EmbeddingConfig.currentMode) {
            EmbeddingConfig.Mode.PRODUCTION -> {
                if (productionService.isReady()) {
                    "Using production embedding service (${EMBEDDING_DIMENSION} dimensions)"
                } else {
                    "Production service not initialized"
                }
            }
            EmbeddingConfig.Mode.PRE_COMPUTED -> {
                "Using pre-computed embeddings (${queryEmbeddings.size} queries, ${EMBEDDING_DIMENSION} dimensions)"
            }
            EmbeddingConfig.Mode.MOCK -> {
                "Using mock embeddings (deterministic, ${EMBEDDING_DIMENSION} dimensions)"
            }
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