package co.kobby.clinicalaide.data.rag.dao

import androidx.room.Dao
import androidx.room.Query
import co.kobby.clinicalaide.data.rag.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    
    // ==================== CHAPTERS ====================
    
    @Query("SELECT * FROM chapters ORDER BY chapter_number")
    suspend fun getAllChapters(): List<Chapter>
    
    @Query("SELECT * FROM chapters WHERE chapter_id = :chapterId")
    suspend fun getChapterById(chapterId: Int): Chapter?
    
    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun getChapterCount(): Int
    
    // ==================== SECTIONS ====================
    
    @Query("SELECT * FROM sections WHERE chapter_id = :chapterId ORDER BY section_id")
    suspend fun getSectionsByChapter(chapterId: Int): List<Section>
    
    @Query("SELECT * FROM sections WHERE section_id = :sectionId")
    suspend fun getSectionById(sectionId: Int): Section?
    
    @Query("SELECT * FROM sections WHERE parent_section_id = :parentSectionId ORDER BY section_id")
    suspend fun getChildSections(parentSectionId: Int): List<Section>
    
    @Query("SELECT COUNT(*) FROM sections")
    suspend fun getSectionCount(): Int
    
    // ==================== CONTENT ====================
    
    @Query("SELECT * FROM content WHERE content_id = :contentId")
    suspend fun getContentById(contentId: Int): Content?
    
    @Query("SELECT * FROM content WHERE section_id = :sectionId ORDER BY content_id")
    suspend fun getContentBySection(sectionId: Int): List<Content>
    
    @Query("SELECT * FROM content WHERE content_type = :type LIMIT :limit")
    suspend fun getContentByType(type: String, limit: Int = 10): List<Content>
    
    @Query("""
        SELECT * FROM content 
        WHERE TRIM(:query) != '' AND content_text LIKE '%' || :query || '%' 
        ORDER BY 
            CASE 
                WHEN content_text LIKE :query || '%' THEN 1 
                WHEN content_text LIKE '%' || :query THEN 3 
                ELSE 2 
            END 
        LIMIT :limit
    """)
    suspend fun searchContent(query: String, limit: Int = 10): List<Content>
    
    @Query("SELECT COUNT(*) FROM content")
    suspend fun getContentCount(): Int
    
    @Query("SELECT DISTINCT content_type FROM content")
    suspend fun getContentTypes(): List<String>
    
    // ==================== METADATA ====================
    
    @Query("SELECT * FROM metadata WHERE content_id = :contentId")
    suspend fun getMetadataByContent(contentId: Int): List<Metadata>
    
    @Query("SELECT * FROM metadata WHERE key = :key")
    suspend fun getMetadataByKey(key: String): List<Metadata>
    
    @Query("SELECT * FROM metadata WHERE key = :key AND value LIKE '%' || :value || '%'")
    suspend fun searchMetadata(key: String, value: String): List<Metadata>
    
    // ==================== EMBEDDINGS ====================
    
    @Query("SELECT * FROM embeddings WHERE content_id = :contentId")
    suspend fun getEmbeddingByContent(contentId: Int): Embedding?
    
    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun getEmbeddingCount(): Int
    
    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<Embedding>
    
    @Query("SELECT * FROM metadata WHERE content_id = :contentId")
    suspend fun getMetadataByContentId(contentId: Int): List<Metadata>
    
    // ==================== FULL-TEXT SEARCH ====================
    
    @Query("""
        SELECT c.* FROM content c
        LEFT JOIN sections s ON c.section_id = s.section_id
        WHERE c.content_text LIKE '%' || :query || '%' 
            OR s.section_title LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN c.content_text LIKE :query || '%' THEN 1 
                WHEN s.section_title LIKE :query || '%' THEN 2
                WHEN c.content_text LIKE '%' || :query || '%' THEN 3
                ELSE 4
            END,
            c.page_number
        LIMIT :limit
    """)
    suspend fun fullTextSearch(query: String, limit: Int = 20): List<Content>
    
    // ==================== STATISTICS ====================
    
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM chapters) as chapterCount,
            (SELECT COUNT(*) FROM sections) as sectionCount,
            (SELECT COUNT(*) FROM content) as contentCount,
            (SELECT COUNT(*) FROM metadata) as metadataCount,
            (SELECT COUNT(*) FROM embeddings) as embeddingCount
    """)
    suspend fun getDatabaseStats(): DatabaseStats
    
    data class DatabaseStats(
        val chapterCount: Int,
        val sectionCount: Int,
        val contentCount: Int,
        val metadataCount: Int,
        val embeddingCount: Int
    )
    
    // ==================== FLOW QUERIES FOR UI ====================
    
    @Query("SELECT * FROM content WHERE content_type = :type ORDER BY page_number LIMIT :limit")
    fun observeContentByType(type: String, limit: Int = 50): Flow<List<Content>>
    
    @Query("""
        SELECT * FROM content 
        WHERE TRIM(:query) != '' AND content_text LIKE '%' || :query || '%'
        ORDER BY page_number
        LIMIT :limit
    """)
    fun observeSearchResults(query: String, limit: Int = 50): Flow<List<Content>>
}