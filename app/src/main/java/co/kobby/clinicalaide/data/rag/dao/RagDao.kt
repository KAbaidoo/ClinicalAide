package co.kobby.clinicalaide.data.rag.dao

import androidx.room.Dao
import androidx.room.Query
import co.kobby.clinicalaide.data.rag.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    
    // ==================== CHAPTERS ====================
    
    @Query("SELECT * FROM chapters ORDER BY number")
    suspend fun getAllChapters(): List<Chapter>
    
    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Int): Chapter?
    
    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun getChapterCount(): Int
    
    // ==================== CONTENT CHUNKS ====================
    
    @Query("SELECT * FROM content_chunks WHERE id = :id")
    suspend fun getContentChunkById(id: Int): ContentChunk?
    
    @Query("SELECT * FROM content_chunks WHERE chunk_type = :type LIMIT :limit")
    suspend fun getContentChunksByType(type: String, limit: Int = 10): List<ContentChunk>
    
    @Query("""
        SELECT * FROM content_chunks 
        WHERE content LIKE '%' || :query || '%' 
        ORDER BY 
            CASE 
                WHEN content LIKE :query || '%' THEN 1 
                WHEN content LIKE '%' || :query THEN 3 
                ELSE 2 
            END 
        LIMIT :limit
    """)
    suspend fun searchContentChunks(query: String, limit: Int = 10): List<ContentChunk>
    
    @Query("""
        SELECT * FROM content_chunks 
        WHERE condition_name LIKE '%' || :conditionName || '%'
        ORDER BY page_number
        LIMIT :limit
    """)
    suspend fun getContentChunksByCondition(conditionName: String, limit: Int = 10): List<ContentChunk>
    
    @Query("SELECT COUNT(*) FROM content_chunks")
    suspend fun getContentChunkCount(): Int
    
    @Query("SELECT DISTINCT chunk_type FROM content_chunks")
    suspend fun getChunkTypes(): List<String>
    
    // ==================== CONDITIONS ====================
    
    @Query("SELECT * FROM conditions_enhanced WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchConditions(query: String, limit: Int = 10): List<ConditionEnhanced>
    
    @Query("SELECT * FROM conditions_enhanced WHERE chapter_number = :chapterNumber ORDER BY page_number")
    suspend fun getConditionsByChapter(chapterNumber: Int): List<ConditionEnhanced>
    
    @Query("SELECT COUNT(*) FROM conditions_enhanced")
    suspend fun getConditionCount(): Int
    
    // ==================== MEDICATIONS ====================
    
    @Query("SELECT * FROM medications_enhanced WHERE generic_name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchMedications(query: String, limit: Int = 10): List<MedicationEnhanced>
    
    @Query("SELECT * FROM medications_enhanced WHERE chapter_number = :chapterNumber ORDER BY page_number")
    suspend fun getMedicationsByChapter(chapterNumber: Int): List<MedicationEnhanced>
    
    @Query("SELECT COUNT(*) FROM medications_enhanced")
    suspend fun getMedicationCount(): Int
    
    // ==================== FULL-TEXT SEARCH ====================
    
    @Query("""
        SELECT * FROM content_chunks 
        WHERE content LIKE '%' || :query || '%' 
            OR condition_name LIKE '%' || :query || '%'
            OR metadata LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN content LIKE :query || '%' THEN 1 
                WHEN condition_name LIKE :query || '%' THEN 2
                WHEN content LIKE '%' || :query || '%' THEN 3
                ELSE 4
            END,
            page_number
        LIMIT :limit
    """)
    suspend fun fullTextSearch(query: String, limit: Int = 20): List<ContentChunk>
    
    // ==================== STATISTICS ====================
    
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM chapters) as chapterCount,
            (SELECT COUNT(*) FROM content_chunks) as chunkCount,
            (SELECT COUNT(*) FROM conditions_enhanced) as conditionCount,
            (SELECT COUNT(*) FROM medications_enhanced) as medicationCount
    """)
    suspend fun getDatabaseStats(): DatabaseStats
    
    data class DatabaseStats(
        val chapterCount: Int,
        val chunkCount: Int,
        val conditionCount: Int,
        val medicationCount: Int
    )
    
    // ==================== FLOW QUERIES FOR UI ====================
    
    @Query("SELECT * FROM content_chunks WHERE chunk_type = :type ORDER BY page_number LIMIT :limit")
    fun observeContentChunksByType(type: String, limit: Int = 50): Flow<List<ContentChunk>>
    
    @Query("""
        SELECT * FROM content_chunks 
        WHERE content LIKE '%' || :query || '%'
        ORDER BY page_number
        LIMIT :limit
    """)
    fun observeSearchResults(query: String, limit: Int = 50): Flow<List<ContentChunk>>
}