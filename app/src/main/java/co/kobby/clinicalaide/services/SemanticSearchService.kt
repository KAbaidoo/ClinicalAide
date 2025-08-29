package co.kobby.clinicalaide.services

import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.entities.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Service for performing semantic search on the STG content using vector embeddings.
 */
@Singleton
class SemanticSearchService @Inject constructor(
    private val ragDatabase: RagDatabase,
    private val embeddingService: EmbeddingService
) {
    
    /**
     * Search for relevant content based on semantic similarity to the query.
     */
    suspend fun searchContent(
        query: String,
        limit: Int = 5,
        minSimilarity: Float = 0.3f
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Generate embedding for the query
        val queryEmbedding = embeddingService.generateEmbedding(query)
        
        // Get all embeddings from database
        val allEmbeddings = ragDatabase.ragDao().getAllEmbeddings()
        
        // Calculate similarities and rank results
        val results = allEmbeddings.mapNotNull { embedding ->
            val similarity = cosineSimilarity(
                queryEmbedding,
                embeddingService.deserializeEmbedding(embedding.embedding)
            )
            
            if (similarity >= minSimilarity) {
                // Get the associated content
                val content = ragDatabase.ragDao().getContentById(embedding.contentId)
                content?.let {
                    SearchResult(
                        content = it,
                        similarity = similarity,
                        embeddingId = embedding.embeddingId
                    )
                }
            } else null
        }
        
        // Sort by similarity and take top results
        results.sortedByDescending { it.similarity }.take(limit)
    }
    
    /**
     * Search with additional context from previous messages.
     */
    suspend fun searchWithContext(
        query: String,
        previousContext: String? = null,
        limit: Int = 5
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Combine query with context if available
        val enhancedQuery = if (!previousContext.isNullOrEmpty()) {
            "$previousContext\n\nCurrent query: $query"
        } else {
            query
        }
        
        searchContent(enhancedQuery, limit)
    }
    
    /**
     * Calculate cosine similarity between two vectors.
     */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator != 0f) dotProduct / denominator else 0f
    }
    
    /**
     * Data class for search results.
     */
    data class SearchResult(
        val content: Content,
        val similarity: Float,
        val embeddingId: Int
    )
    
    /**
     * Build context from search results for LLM.
     */
    suspend fun buildContext(
        searchResults: List<SearchResult>,
        includeMetadata: Boolean = true
    ): String = withContext(Dispatchers.Default) {
        val contextBuilder = StringBuilder()
        
        searchResults.forEachIndexed { index, result ->
            contextBuilder.append("=== Context ${index + 1} (Similarity: ${String.format(java.util.Locale.ROOT, "%.2f", result.similarity)}) ===\n")
            contextBuilder.append("Page: ${result.content.pageNumber}")
            contextBuilder.append("\n")
            
            // Add section information
            val section = ragDatabase.ragDao().getSectionById(result.content.sectionId)
            section?.let {
                contextBuilder.append("Section: ${it.sectionTitle}\n")
                
                // Add chapter information if available
                it.chapterId.let { chapterId ->
                    val chapter = ragDatabase.ragDao().getChapterById(chapterId)
                    chapter?.let { ch ->
                        contextBuilder.append("Chapter: ${ch.chapterTitle}\n")
                    }
                }
            }
            
            contextBuilder.append("\n${result.content.contentText}\n")
            
            // Add metadata if requested
            if (includeMetadata) {
                val metadata = ragDatabase.ragDao().getMetadataByContentId(result.content.contentId)
                if (metadata.isNotEmpty()) {
                    contextBuilder.append("\nMetadata:\n")
                    metadata.forEach { meta ->
                        contextBuilder.append("- ${meta.key}: ${meta.value}\n")
                    }
                }
            }
            
            contextBuilder.append("\n")
        }
        
        contextBuilder.toString()
    }
}