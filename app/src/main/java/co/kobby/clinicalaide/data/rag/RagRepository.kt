package co.kobby.clinicalaide.data.rag

import co.kobby.clinicalaide.data.rag.dao.RagDao
import co.kobby.clinicalaide.data.rag.entities.*
import co.kobby.clinicalaide.data.rag.search.SemanticSearchService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagRepository @Inject constructor(
    private val ragDao: RagDao,
    private val semanticSearchService: SemanticSearchService
) {
    
    // ==================== SEARCH OPERATIONS ====================
    
    /**
     * Primary search function for medical queries using semantic search
     */
    suspend fun searchMedicalContent(query: String, limit: Int = 20): List<Content> {
        val results = semanticSearchService.searchSemantically(query, limit)
        return results.map { it.content }
    }
    
    /**
     * Enhanced search that returns similarity scores
     */
    suspend fun searchMedicalContentWithScores(query: String, limit: Int = 20): List<SemanticSearchService.ContentWithSimilarity> {
        return semanticSearchService.searchSemantically(query, limit)
    }
    
    /**
     * Find similar content based on an existing content item
     */
    suspend fun findSimilarContent(contentId: Int, limit: Int = 10): List<Content> {
        val results = semanticSearchService.findSimilarContent(contentId, limit)
        return results.map { it.content }
    }
    
    /**
     * Search content by text
     */
    suspend fun searchContent(query: String, limit: Int = 10): List<Content> {
        return ragDao.searchContent(query, limit)
    }
    
    // ==================== CONTENT RETRIEVAL ====================
    
    /**
     * Get content by type (treatment, diagnosis, etc.)
     */
    suspend fun getContentByType(type: String, limit: Int = 10): List<Content> {
        return ragDao.getContentByType(type, limit)
    }
    
    /**
     * Get content for a specific section
     */
    suspend fun getContentForSection(sectionId: Int): List<Content> {
        return ragDao.getContentBySection(sectionId)
    }
    
    /**
     * Get a specific content by ID
     */
    suspend fun getContentById(contentId: Int): Content? {
        return ragDao.getContentById(contentId)
    }
    
    // ==================== CHAPTER OPERATIONS ====================
    
    /**
     * Get all chapters
     */
    suspend fun getAllChapters(): List<Chapter> {
        return ragDao.getAllChapters()
    }
    
    /**
     * Get sections in a chapter
     */
    suspend fun getSectionsInChapter(chapterId: Int): List<Section> {
        return ragDao.getSectionsByChapter(chapterId)
    }
    
    /**
     * Get metadata for content
     */
    suspend fun getMetadataForContent(contentId: Int): List<Metadata> {
        return ragDao.getMetadataByContent(contentId)
    }
    
    // ==================== STATISTICS ====================
    
    /**
     * Get database statistics
     */
    suspend fun getDatabaseStats(): RagDao.DatabaseStats {
        return ragDao.getDatabaseStats()
    }
    
    /**
     * Get available content types
     */
    suspend fun getAvailableContentTypes(): List<String> {
        return ragDao.getContentTypes()
    }
    
    // ==================== FLOW OPERATIONS FOR UI ====================
    
    /**
     * Observe search results
     */
    fun observeSearchResults(query: String, limit: Int = 50): Flow<List<Content>> {
        return ragDao.observeSearchResults(query, limit)
    }
    
    /**
     * Observe content by type
     */
    fun observeContentByType(type: String, limit: Int = 50): Flow<List<Content>> {
        return ragDao.observeContentByType(type, limit)
    }
    
    // ==================== RAG-SPECIFIC OPERATIONS ====================
    
    /**
     * Build context for AI response generation using semantic search
     * Returns relevant content with citations and similarity scores for a medical query
     */
    suspend fun buildRagContext(query: String, maxContent: Int = 5): RagContext {
        val contentWithScores = searchMedicalContentWithScores(query, maxContent)
        val contents = contentWithScores.map { it.content }
        val citations = contents.map { "Page ${it.pageNumber}" }.distinct()
        val context = contentWithScores.joinToString("\n\n") { (content, similarity) ->
            "${content.contentText}\n[Page ${content.pageNumber}, Relevance: ${String.format("%.2f", similarity)}]"
        }
        
        return RagContext(
            query = query,
            contents = contents,
            context = context,
            citations = citations,
            averageSimilarity = contentWithScores.map { it.similarity }.average().toFloat()
        )
    }
    
    data class RagContext(
        val query: String,
        val contents: List<Content>,
        val context: String,
        val citations: List<String>,
        val averageSimilarity: Float = 0.0f
    )
    
    /**
     * Format medical response with citations
     */
    fun formatResponseWithCitations(
        response: String,
        citations: List<String>
    ): String {
        return buildString {
            append(response)
            if (citations.isNotEmpty()) {
                append("\n\n")
                append("References:\n")
                citations.forEach { citation ->
                    append("• $citation\n")
                }
            }
        }
    }
}