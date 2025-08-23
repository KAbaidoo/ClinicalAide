package co.kobby.clinicalaide.data.rag

import co.kobby.clinicalaide.data.rag.dao.RagDao
import co.kobby.clinicalaide.data.rag.entities.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagRepository @Inject constructor(
    private val ragDao: RagDao
) {
    
    // ==================== SEARCH OPERATIONS ====================
    
    /**
     * Primary search function for medical queries
     */
    suspend fun searchMedicalContent(query: String, limit: Int = 20): List<ContentChunk> {
        return ragDao.fullTextSearch(query, limit)
    }
    
    /**
     * Search for specific medical conditions
     */
    suspend fun searchConditions(query: String, limit: Int = 10): List<ConditionEnhanced> {
        return ragDao.searchConditions(query, limit)
    }
    
    /**
     * Search for medications
     */
    suspend fun searchMedications(query: String, limit: Int = 10): List<MedicationEnhanced> {
        return ragDao.searchMedications(query, limit)
    }
    
    // ==================== CONTENT RETRIEVAL ====================
    
    /**
     * Get content chunks by type (treatment, diagnosis, etc.)
     */
    suspend fun getContentByType(type: String, limit: Int = 10): List<ContentChunk> {
        return ragDao.getContentChunksByType(type, limit)
    }
    
    /**
     * Get content for a specific condition
     */
    suspend fun getContentForCondition(conditionName: String, limit: Int = 10): List<ContentChunk> {
        return ragDao.getContentChunksByCondition(conditionName, limit)
    }
    
    /**
     * Get a specific content chunk by ID
     */
    suspend fun getContentChunkById(id: Int): ContentChunk? {
        return ragDao.getContentChunkById(id)
    }
    
    // ==================== CHAPTER OPERATIONS ====================
    
    /**
     * Get all chapters
     */
    suspend fun getAllChapters(): List<Chapter> {
        return ragDao.getAllChapters()
    }
    
    /**
     * Get conditions in a chapter
     */
    suspend fun getConditionsInChapter(chapterNumber: Int): List<ConditionEnhanced> {
        return ragDao.getConditionsByChapter(chapterNumber)
    }
    
    /**
     * Get medications in a chapter
     */
    suspend fun getMedicationsInChapter(chapterNumber: Int): List<MedicationEnhanced> {
        return ragDao.getMedicationsByChapter(chapterNumber)
    }
    
    // ==================== STATISTICS ====================
    
    /**
     * Get database statistics
     */
    suspend fun getDatabaseStats(): RagDao.DatabaseStats {
        return ragDao.getDatabaseStats()
    }
    
    /**
     * Get available chunk types
     */
    suspend fun getAvailableChunkTypes(): List<String> {
        return ragDao.getChunkTypes()
    }
    
    // ==================== FLOW OPERATIONS FOR UI ====================
    
    /**
     * Observe search results
     */
    fun observeSearchResults(query: String, limit: Int = 50): Flow<List<ContentChunk>> {
        return ragDao.observeSearchResults(query, limit)
    }
    
    /**
     * Observe content by type
     */
    fun observeContentByType(type: String, limit: Int = 50): Flow<List<ContentChunk>> {
        return ragDao.observeContentChunksByType(type, limit)
    }
    
    // ==================== RAG-SPECIFIC OPERATIONS ====================
    
    /**
     * Build context for AI response generation
     * Returns relevant chunks with citations for a medical query
     */
    suspend fun buildRagContext(query: String, maxChunks: Int = 5): RagContext {
        val chunks = searchMedicalContent(query, maxChunks)
        val citations = chunks.map { it.referenceCitation }.distinct()
        val context = chunks.joinToString("\n\n") { chunk ->
            "${chunk.content}\n[${chunk.referenceCitation}]"
        }
        
        return RagContext(
            query = query,
            chunks = chunks,
            context = context,
            citations = citations
        )
    }
    
    data class RagContext(
        val query: String,
        val chunks: List<ContentChunk>,
        val context: String,
        val citations: List<String>
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