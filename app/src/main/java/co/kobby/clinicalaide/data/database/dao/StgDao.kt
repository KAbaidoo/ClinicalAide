package co.kobby.clinicalaide.data.database.dao

import androidx.room.*
import co.kobby.clinicalaide.data.database.entities.*
import co.kobby.clinicalaide.data.database.models.ContentBlockWithMetadata

@Dao
interface StgDao {
    
    // Chapter operations
    @Query("SELECT * FROM stg_chapters ORDER BY chapterNumber")
    suspend fun getAllChapters(): List<StgChapter>
    
    @Query("SELECT * FROM stg_chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: Long): StgChapter?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: StgChapter): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<StgChapter>)
    
    @Delete
    suspend fun deleteChapter(chapter: StgChapter)
    
    // Condition operations
    @Query("SELECT * FROM stg_conditions WHERE chapterId = :chapterId ORDER BY conditionNumber")
    suspend fun getConditionsByChapter(chapterId: Long): List<StgCondition>
    
    @Query("SELECT * FROM stg_conditions WHERE conditionName LIKE '%' || :searchTerm || '%'")
    suspend fun searchConditions(searchTerm: String): List<StgCondition>
    
    @Query("SELECT * FROM stg_conditions WHERE id = :conditionId")
    suspend fun getConditionById(conditionId: Long): StgCondition?
    
    @Query("SELECT * FROM stg_conditions")
    suspend fun getAllConditions(): List<StgCondition>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: StgCondition): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConditions(conditions: List<StgCondition>)
    
    // Content block operations
    @Query("""
        SELECT * FROM stg_content_blocks 
        WHERE conditionId = :conditionId 
        ORDER BY orderInCondition
    """)
    suspend fun getContentBlocksByCondition(conditionId: Long): List<StgContentBlock>
    
    @Query("""
        SELECT * FROM stg_content_blocks 
        WHERE conditionId = :conditionId AND blockType = :blockType
        ORDER BY orderInCondition
    """)
    suspend fun getContentBlocksByType(conditionId: Long, blockType: String): List<StgContentBlock>
    
    @Query("""
        SELECT cb.* FROM stg_content_blocks cb
        WHERE cb.clinicalContext = :context OR cb.clinicalContext = 'general'
        ORDER BY cb.id
    """)
    suspend fun getContentBlocksByContext(context: String): List<StgContentBlock>
    
    @Query("SELECT * FROM stg_content_blocks WHERE id IN (:blockIds)")
    suspend fun getContentBlocksByIds(blockIds: List<Long>): List<StgContentBlock>
    
    @Query("SELECT * FROM stg_content_blocks")
    suspend fun getAllContentBlocks(): List<StgContentBlock>
    
    @Query("SELECT * FROM stg_content_blocks WHERE id = :id")
    suspend fun getContentBlockById(id: Long): StgContentBlock?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentBlock(contentBlock: StgContentBlock): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentBlocks(contentBlocks: List<StgContentBlock>): List<Long>
    
    @Update
    suspend fun updateContentBlock(contentBlock: StgContentBlock)
    
    // Embedding operations
    @Query("SELECT * FROM stg_embeddings WHERE contentBlockId = :contentBlockId")
    suspend fun getEmbeddingByContentBlock(contentBlockId: Long): StgEmbedding?
    
    @Query("SELECT * FROM stg_embeddings WHERE id = :id")
    suspend fun getEmbeddingById(id: Long): StgEmbedding?
    
    @Query("SELECT * FROM stg_embeddings")
    suspend fun getAllEmbeddings(): List<StgEmbedding>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: StgEmbedding): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<StgEmbedding>)
    
    // Complex queries for context building
    @Query("""
        SELECT cb.*, c.conditionName, ch.chapterTitle 
        FROM stg_content_blocks cb
        JOIN stg_conditions c ON cb.conditionId = c.id
        JOIN stg_chapters ch ON c.chapterId = ch.id
        WHERE cb.id IN (:blockIds)
        ORDER BY cb.conditionId, cb.orderInCondition
    """)
    suspend fun getContentBlocksWithMetadata(blockIds: List<Long>): List<ContentBlockWithMetadata>
    
    // Medication operations
    @Query("""
        SELECT * FROM stg_medications 
        WHERE conditionId = :conditionId AND ageGroup = :ageGroup
    """)
    suspend fun getMedicationsByConditionAndAge(conditionId: Long, ageGroup: String): List<StgMedication>
    
    @Query("SELECT * FROM stg_medications WHERE conditionId = :conditionId")
    suspend fun getMedicationsByCondition(conditionId: Long): List<StgMedication>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: StgMedication): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(medications: List<StgMedication>)
    
    @Update
    suspend fun updateMedication(medication: StgMedication)
    
    // Cross-reference operations
    @Query("SELECT * FROM stg_cross_references WHERE fromConditionId = :conditionId")
    suspend fun getCrossReferencesByCondition(conditionId: Long): List<StgCrossReference>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossReference(crossReference: StgCrossReference): Long
    
    // Search cache operations
    @Query("SELECT * FROM stg_search_cache WHERE queryHash = :hash")
    suspend fun getCachedSearch(hash: String): StgSearchCache?
    
    @Query("SELECT * FROM stg_search_cache")
    suspend fun getAllSearchCache(): List<StgSearchCache>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheSearch(cache: StgSearchCache)
    
    @Query("DELETE FROM stg_search_cache WHERE timestamp < :cutoffTime")
    suspend fun clearOldCache(cutoffTime: Long)
    
    // Full-text search as backup to semantic search
    @Query("""
        SELECT * FROM stg_content_blocks 
        WHERE content LIKE '%' || :searchTerm || '%' 
        OR keywords LIKE '%' || :searchTerm || '%'
        ORDER BY 
            CASE WHEN content LIKE :searchTerm || '%' THEN 1 ELSE 2 END,
            LENGTH(content)
        LIMIT :limit
    """)
    suspend fun searchContentByText(searchTerm: String, limit: Int = 10): List<StgContentBlock>
    
    // Utility operations for testing
    @Query("DELETE FROM stg_chapters")
    suspend fun deleteAllChapters()
    
    @Query("DELETE FROM stg_search_cache")
    suspend fun deleteAllSearchCache()
    
    // Count operations for statistics
    @Query("SELECT COUNT(*) FROM stg_chapters")
    suspend fun getChapterCount(): Int
    
    @Query("SELECT COUNT(*) FROM stg_conditions")
    suspend fun getConditionCount(): Int
    
    @Query("SELECT COUNT(*) FROM stg_medications")
    suspend fun getMedicationCount(): Int
    
    @Query("SELECT COUNT(*) FROM stg_content_blocks")
    suspend fun getContentBlockCount(): Int
}