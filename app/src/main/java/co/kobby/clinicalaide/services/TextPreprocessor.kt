package co.kobby.clinicalaide.services

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preprocesses text for embedding model input.
 * Handles tokenization and normalization for TensorFlow Lite models.
 */
@Singleton
class TextPreprocessor @Inject constructor() {
    
    companion object {
        const val MAX_SEQUENCE_LENGTH = 256  // Maximum tokens for most models
        const val PAD_TOKEN = "[PAD]"
        const val UNK_TOKEN = "[UNK]"
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"
        const val PAD_ID = 0
        const val UNK_ID = 100
        const val CLS_ID = 101
        const val SEP_ID = 102
    }
    
    /**
     * Simple tokenization for text.
     * This is a basic implementation - for production, use proper tokenizer.
     */
    fun tokenize(text: String): List<String> {
        // Clean and normalize text
        val cleanedText = text.trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")  // Remove special characters
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
        
        // Split into tokens
        val tokens = mutableListOf<String>()
        tokens.add(CLS_TOKEN)  // Add CLS token at start
        
        // Simple word-based tokenization
        val words = cleanedText.split(" ").filter { it.isNotEmpty() }
        tokens.addAll(words.take(MAX_SEQUENCE_LENGTH - 2))  // Leave room for CLS and SEP
        
        tokens.add(SEP_TOKEN)  // Add SEP token at end
        
        return tokens
    }
    
    /**
     * Convert tokens to IDs for model input.
     * This is a simplified version - real implementation would use vocabulary file.
     */
    fun tokensToIds(tokens: List<String>): IntArray {
        val ids = IntArray(MAX_SEQUENCE_LENGTH) { PAD_ID }
        
        tokens.take(MAX_SEQUENCE_LENGTH).forEachIndexed { index, token ->
            ids[index] = when (token) {
                PAD_TOKEN -> PAD_ID
                CLS_TOKEN -> CLS_ID
                SEP_TOKEN -> SEP_ID
                UNK_TOKEN -> UNK_ID
                else -> token.hashCode() % 30000  // Simple hash-based ID (mock)
            }
        }
        
        return ids
    }
    
    /**
     * Create attention mask for the input.
     */
    fun createAttentionMask(tokens: List<String>): IntArray {
        val mask = IntArray(MAX_SEQUENCE_LENGTH) { 0 }
        val actualLength = minOf(tokens.size, MAX_SEQUENCE_LENGTH)
        
        for (i in 0 until actualLength) {
            mask[i] = 1
        }
        
        return mask
    }
    
    /**
     * Preprocess text for Universal Sentence Encoder style models.
     * Returns normalized text suitable for embedding.
     */
    fun preprocessForUSE(text: String): String {
        // USE models typically work with raw text
        // Just clean and normalize
        return text.trim()
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .take(512)  // Limit length for performance
    }
    
    /**
     * Preprocess text for BERT-style models.
     * Returns token IDs and attention mask.
     */
    fun preprocessForBERT(text: String): Pair<IntArray, IntArray> {
        val tokens = tokenize(text)
        val tokenIds = tokensToIds(tokens)
        val attentionMask = createAttentionMask(tokens)
        
        return Pair(tokenIds, attentionMask)
    }
    
    /**
     * Simple text cleaning for any model.
     */
    fun cleanText(text: String): String {
        return text.trim()
            .replace("\n", " ")
            .replace("\t", " ")
            .replace(Regex("\\s+"), " ")
            .take(1000)  // Reasonable length limit
    }
    
    /**
     * Normalize embedding vector (L2 normalization).
     */
    fun normalizeVector(vector: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }.toFloat())
        return if (norm > 0) {
            FloatArray(vector.size) { i -> vector[i] / norm }
        } else {
            vector
        }
    }
}