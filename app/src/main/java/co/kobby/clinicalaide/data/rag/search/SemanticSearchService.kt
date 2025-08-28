package co.kobby.clinicalaide.data.rag.search

import co.kobby.clinicalaide.data.rag.dao.RagDao
import co.kobby.clinicalaide.data.rag.entities.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Service for performing semantic search using embeddings.
 * 
 * This service handles:
 * 1. Converting text queries to embeddings (placeholder for now)
 * 2. Computing cosine similarity between query and content embeddings
 * 3. Ranking and returning relevant content
 */
@Singleton
class SemanticSearchService @Inject constructor(
    private val ragDao: RagDao
) {
    
    companion object {
        private const val DEFAULT_SIMILARITY_THRESHOLD = 0.3f
        private const val EMBEDDING_DIMENSION = 384 // all-MiniLM-L6-v2 model
    }
    
    /**
     * Perform semantic search for medical content.
     * 
     * @param query The search query text
     * @param limit Maximum number of results to return
     * @param similarityThreshold Minimum cosine similarity score (0.0 to 1.0)
     * @return List of content ranked by relevance
     */
    suspend fun searchSemantically(
        query: String,
        limit: Int = 20,
        similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD
    ): List<ContentWithSimilarity> = withContext(Dispatchers.IO) {
        
        try {
            // Try embedding-based search first
            return@withContext searchWithEmbeddings(query, limit, similarityThreshold)
        } catch (e: Exception) {
            // Fallback to text-based search if embedding search fails
            return@withContext searchWithTextSimilarity(query, limit, similarityThreshold)
        }
    }
    
    /**
     * Embedding-based semantic search (when TensorFlow Lite model is available).
     */
    private suspend fun searchWithEmbeddings(
        query: String,
        limit: Int,
        similarityThreshold: Float
    ): List<ContentWithSimilarity> {
        
        // TODO: Implement TensorFlow Lite model loading and query embedding generation
        // For now, throw exception to trigger fallback to text search
        throw NotImplementedError("Embedding-based search not yet implemented")
        
        // Future implementation would:
        // 1. Load TensorFlow Lite model (all-MiniLM-L6-v2)
        // 2. Generate embedding for query
        // 3. Get all content embeddings from database
        // 4. Calculate cosine similarities
        // 5. Return sorted results
    }
    
    /**
     * Text-based semantic search fallback.
     */
    private suspend fun searchWithTextSimilarity(
        query: String,
        limit: Int,
        similarityThreshold: Float
    ): List<ContentWithSimilarity> {
        
        // Use enhanced text search that considers medical terminology
        val results = mutableListOf<ContentWithSimilarity>()
        
        // 1. Direct text search
        val directMatches = ragDao.searchContent(query, limit)
        directMatches.forEach { content ->
            val similarity = calculateEnhancedTextSimilarity(query, content.contentText)
            if (similarity >= similarityThreshold) {
                results.add(ContentWithSimilarity(content, similarity * 1.2f)) // Boost direct matches
            }
        }
        
        // 2. Search for medical synonyms and related terms
        val medicalTerms = extractMedicalTerms(query)
        for (term in medicalTerms) {
            val termMatches = ragDao.searchContent(term, 10)
            termMatches.forEach { content ->
                // Avoid duplicates
                if (!results.any { it.content.contentId == content.contentId }) {
                    val similarity = calculateEnhancedTextSimilarity(term, content.contentText)
                    if (similarity >= similarityThreshold) {
                        results.add(ContentWithSimilarity(content, similarity * 0.9f)) // Slightly lower for synonyms
                    }
                }
            }
        }
        
        return results.sortedByDescending { it.similarity }.take(limit)
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors.
     * 
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity score (0.0 to 1.0)
     */
    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) {
            "Embedding vectors must have the same dimensions"
        }
        
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        val magnitude = sqrt(norm1) * sqrt(norm2)
        return if (magnitude != 0.0f) dotProduct / magnitude else 0.0f
    }
    
    /**
     * Convert binary embedding data to float array.
     * The embeddings are stored as binary data (BLOB) in SQLite.
     */
    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Invalid embedding data size" }
        
        val floatArray = FloatArray(bytes.size / 4)
        for (i in floatArray.indices) {
            val offset = i * 4
            floatArray[i] = java.nio.ByteBuffer.wrap(bytes, offset, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .float
        }
        return floatArray
    }
    
    /**
     * Enhanced text-based similarity calculation with medical terminology awareness.
     */
    private fun calculateEnhancedTextSimilarity(query: String, content: String): Float {
        val queryWords = query.lowercase().split("\\s+".toRegex()).toSet()
        val contentWords = content.lowercase().split("\\s+".toRegex()).toSet()
        
        // Basic Jaccard similarity
        val intersection = queryWords.intersect(contentWords).size
        val union = queryWords.union(contentWords).size
        val jaccardSimilarity = if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f
        
        // Boost for medical term matches
        var medicalBoost = 0.0f
        val medicalTermsInQuery = queryWords.filter { isMedicalTerm(it) }
        val medicalTermsInContent = contentWords.filter { isMedicalTerm(it) }
        val medicalIntersection = medicalTermsInQuery.intersect(medicalTermsInContent.toSet()).size
        
        if (medicalTermsInQuery.isNotEmpty()) {
            medicalBoost = (medicalIntersection.toFloat() / medicalTermsInQuery.size) * 0.3f
        }
        
        // Boost for exact phrase matches
        var phraseBoost = 0.0f
        if (query.length > 10 && content.lowercase().contains(query.lowercase())) {
            phraseBoost = 0.2f
        }
        
        return (jaccardSimilarity + medicalBoost + phraseBoost).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Extract medical terms from query for expanded search.
     */
    private fun extractMedicalTerms(query: String): List<String> {
        val words = query.lowercase().split("\\s+".toRegex())
        val medicalTerms = mutableListOf<String>()
        
        // Add synonyms for common medical terms
        words.forEach { word ->
            when (word) {
                "fever", "temperature" -> medicalTerms.addAll(listOf("pyrexia", "hyperthermia", "fever"))
                "pain" -> medicalTerms.addAll(listOf("ache", "discomfort", "pain"))
                "nausea" -> medicalTerms.addAll(listOf("vomiting", "emesis", "nausea"))
                "diarrhea", "diarrhoea" -> medicalTerms.addAll(listOf("diarrhea", "diarrhoea", "loose stool"))
                "cough" -> medicalTerms.addAll(listOf("cough", "tussis"))
                "headache" -> medicalTerms.addAll(listOf("headache", "cephalgia"))
                "malaria" -> medicalTerms.addAll(listOf("malaria", "plasmodium", "antimalarial"))
                else -> if (isMedicalTerm(word)) medicalTerms.add(word)
            }
        }
        
        return medicalTerms.distinct()
    }
    
    /**
     * Simple check if a word is likely a medical term.
     */
    private fun isMedicalTerm(word: String): Boolean {
        val medicalSuffixes = listOf("itis", "osis", "emia", "pathy", "ology", "gram", "scopy")
        val medicalPrefixes = listOf("anti", "hyper", "hypo", "pre", "post", "intra", "extra")
        val commonMedicalTerms = setOf(
            "treatment", "therapy", "medication", "drug", "dose", "mg", "ml", "oral", "iv", "im",
            "diagnosis", "symptoms", "signs", "patient", "clinical", "medical", "disease",
            "infection", "bacteria", "virus", "parasite", "fungal", "chronic", "acute"
        )
        
        return word in commonMedicalTerms ||
                medicalSuffixes.any { word.endsWith(it) } ||
                medicalPrefixes.any { word.startsWith(it) } ||
                word.length > 8 // Longer words more likely to be medical terms
    }
    
    /**
     * Get similar content based on an existing content item.
     * Useful for "more like this" functionality.
     */
    suspend fun findSimilarContent(
        contentId: Int,
        limit: Int = 10,
        similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD
    ): List<ContentWithSimilarity> = withContext(Dispatchers.IO) {
        
        // Get the source content
        val sourceContent = ragDao.getContentById(contentId)
            ?: return@withContext emptyList()
        
        // TODO: Implement embedding-based similarity search
        // For now, use text-based search with the content text
        searchSemantically(
            query = sourceContent.contentText.take(100), // Use first 100 chars as query
            limit = limit + 1, // +1 to account for the source content itself
            similarityThreshold = similarityThreshold
        ).filterNot { it.content.contentId == contentId } // Exclude source content
         .take(limit)
    }
    
    /**
     * Data class representing content with its similarity score
     */
    data class ContentWithSimilarity(
        val content: Content,
        val similarity: Float
    )
}