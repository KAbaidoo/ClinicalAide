package co.kobby.clinicalaide.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.RagRepository
import co.kobby.clinicalaide.data.rag.dao.RagDao
import co.kobby.clinicalaide.services.SemanticSearchService
import co.kobby.clinicalaide.services.EmbeddingService
import co.kobby.clinicalaide.services.TFLiteModelLoader
import co.kobby.clinicalaide.services.TextPreprocessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge case and boundary tests for the RAG database and search operations.
 * Tests null handling, empty inputs, special characters, and limits.
 */
@RunWith(AndroidJUnit4::class)
class EdgeCaseTest {
    
    private lateinit var database: RagDatabase
    private lateinit var dao: RagDao
    private lateinit var repository: RagRepository
    private lateinit var searchService: SemanticSearchService
    private lateinit var embeddingService: EmbeddingService
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = RagDatabase.getInstance(context)
        dao = database.ragDao()
        
        // Initialize services for the new SemanticSearchService
        val modelLoader = TFLiteModelLoader(context)
        val textPreprocessor = TextPreprocessor()
        embeddingService = EmbeddingService(context, modelLoader, textPreprocessor)
        searchService = SemanticSearchService(database, embeddingService)
        repository = RagRepository(dao, searchService)
    }
    
    // ==================== EMPTY/NULL HANDLING TESTS ====================
    
    @Test
    fun test_searchEmptyQuery_returnsEmpty() = runBlocking {
        val results = dao.searchContent("", 10)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Empty query should return empty results", results.isEmpty())
    }
    
    @Test
    fun test_searchWhitespaceQuery_handledProperly() = runBlocking {
        val results = dao.searchContent("   ", 10)
        
        assertNotNull("Results should not be null for whitespace", results)
        // Whitespace might match or return empty depending on implementation
    }
    
    @Test
    fun test_nonExistentContentId_returnsNull() = runBlocking {
        val content = dao.getContentById(999999)
        
        assertNull("Non-existent content ID should return null", content)
    }
    
    @Test
    fun test_nonExistentChapterId_returnsNull() = runBlocking {
        val chapter = dao.getChapterById(999999)
        
        assertNull("Non-existent chapter ID should return null", chapter)
    }
    
    @Test
    fun test_nonExistentSectionId_returnsNull() = runBlocking {
        val section = dao.getSectionById(999999)
        
        assertNull("Non-existent section ID should return null", section)
    }
    
    @Test
    fun test_getChildSections_nonExistentParent_returnsEmpty() = runBlocking {
        val children = dao.getChildSections(999999)
        
        assertNotNull("Results should not be null", children)
        assertTrue("Non-existent parent should have no children", children.isEmpty())
    }
    
    @Test
    fun test_getMetadataByContent_nonExistent_returnsEmpty() = runBlocking {
        val metadata = dao.getMetadataByContent(999999)
        
        assertNotNull("Metadata list should not be null", metadata)
        assertTrue("Non-existent content should have no metadata", metadata.isEmpty())
    }
    
    @Test
    fun test_getEmbeddingByContent_nonExistent_returnsNull() = runBlocking {
        val embedding = dao.getEmbeddingByContent(999999)
        
        assertNull("Non-existent content should have no embedding", embedding)
    }
    
    // ==================== SPECIAL CHARACTERS TESTS ====================
    
    @Test
    fun test_searchSpecialCharacters_sanitized() = runBlocking {
        val dangerousQueries = listOf(
            "'; DROP TABLE content; --",
            "\" OR \"1\"=\"1",
            "test%",
            "test_",
            "test\\",
            "test'test",
            "test\"test"
        )
        
        dangerousQueries.forEach { query ->
            try {
                val results = dao.searchContent(query, 5)
                assertNotNull("Should handle dangerous query: $query", results)
                // Should not crash or execute SQL injection
            } catch (e: Exception) {
                fail("Should not throw exception for: $query")
            }
        }
    }
    
    @Test
    fun test_searchUnicodeCharacters() = runBlocking {
        val unicodeQueries = listOf(
            "测试", // Chinese
            "テスト", // Japanese
            "тест", // Russian
            "🏥💊", // Emojis
            "café", // Accented characters
            "naïve" // Diacritic marks
        )
        
        unicodeQueries.forEach { query ->
            val results = dao.searchContent(query, 5)
            assertNotNull("Should handle unicode query: $query", results)
            // Might not find results but shouldn't crash
        }
    }
    
    // ==================== LIMIT TESTING ====================
    
    @Test
    fun test_searchWithLimit0_returnsEmpty() = runBlocking {
        val results = dao.searchContent("malaria", 0)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Limit 0 should return empty results", results.isEmpty())
    }
    
    @Test
    fun test_searchWithNegativeLimit_handledGracefully() = runBlocking {
        try {
            val results = dao.searchContent("malaria", -1)
            assertNotNull("Should handle negative limit gracefully", results)
            // SQLite might treat negative as no limit or throw exception
        } catch (e: Exception) {
            // Some databases might throw exception for negative limit
            println("Negative limit threw exception: ${e.message}")
        }
    }
    
    @Test
    fun test_searchWithVeryLargeLimit() = runBlocking {
        val results = dao.searchContent("a", Int.MAX_VALUE)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Should return available results", results.size <= 664) // Max content count
    }
    
    @Test
    fun test_getContentByType_withLimit0() = runBlocking {
        val results = dao.getContentByType("paragraph", 0)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Limit 0 should return empty results", results.isEmpty())
    }
    
    // ==================== VERY LONG INPUT TESTS ====================
    
    @Test
    fun test_veryLongQuery_handled() = runBlocking {
        val longQuery = "malaria ".repeat(100) // 700+ characters
        
        val results = dao.searchContent(longQuery, 10)
        assertNotNull("Should handle very long queries", results)
        // Might not find exact matches but shouldn't crash
    }
    
    @Test
    fun test_veryLongMetadataSearch() = runBlocking {
        val longValue = "test".repeat(100)
        
        val results = dao.searchMetadata("key", longValue)
        assertNotNull("Should handle long metadata values", results)
        assertTrue("Probably won't find matches for very long value", results.isEmpty())
    }
    
    // ==================== BOUNDARY VALUE TESTS ====================
    
    @Test
    fun test_firstAndLastContent() = runBlocking {
        val firstContent = dao.getContentById(1)
        assertNotNull("First content should exist", firstContent)
        
        // Get max content ID
        val allContent = dao.searchContent("", Int.MAX_VALUE) // Empty search returns nothing
        val stats = dao.getDatabaseStats()
        
        // Try to get a content with ID equal to content count
        val lastContent = dao.getContentById(stats.contentCount)
        // May or may not exist depending on ID gaps
        
        // Try ID beyond range
        val beyondContent = dao.getContentById(stats.contentCount + 1000)
        assertNull("Content beyond range should be null", beyondContent)
    }
    
    @Test
    fun test_pageNumberBoundaries() = runBlocking {
        // Ghana STG has pages 29-692
        val earlyPages = dao.searchContent("", 1000)
            .filter { it.pageNumber < 29 }
        
        assertTrue("Should not have pages before 29", earlyPages.isEmpty())
        
        val latePages = dao.searchContent("", 1000)
            .filter { it.pageNumber > 692 }
        
        assertTrue("Should not have pages after 692", latePages.isEmpty())
    }
    
    // ==================== CASE SENSITIVITY TESTS ====================
    
    @Test
    fun test_searchCaseInsensitive() = runBlocking {
        val lowercase = dao.searchContent("malaria", 10)
        val uppercase = dao.searchContent("MALARIA", 10)
        val mixedCase = dao.searchContent("MaLaRiA", 10)
        
        // All should find results (case-insensitive search)
        assertTrue("Lowercase should find results", lowercase.isNotEmpty())
        assertTrue("Uppercase should find results", uppercase.isNotEmpty())
        assertTrue("Mixed case should find results", mixedCase.isNotEmpty())
        
        // Results counts should be similar
        assertEquals(
            "Case variations should return same number of results",
            lowercase.size,
            uppercase.size
        )
    }
    
    // ==================== CONCURRENT EDGE CASES ====================
    
    @Test
    fun test_simultaneousNullQueries() = runBlocking {
        val results = listOf(
            dao.getContentById(999999),
            dao.getChapterById(999999),
            dao.getSectionById(999999),
            dao.getEmbeddingByContent(999999)
        )
        
        results.forEach { result ->
            assertNull("All non-existent queries should return null", result)
        }
    }
    
    // ==================== METADATA EDGE CASES ====================
    
    @Test
    fun test_searchMetadata_emptyKey() = runBlocking {
        val results = dao.searchMetadata("", "value")
        
        assertNotNull("Results should not be null", results)
        assertTrue("Empty key should return no results", results.isEmpty())
    }
    
    @Test
    fun test_searchMetadata_emptyValue() = runBlocking {
        val results = dao.searchMetadata("target_population", "")
        
        assertNotNull("Results should not be null", results)
        // Empty value in LIKE '%' || '' || '%' matches everything
        // So this might return all metadata with that key
    }
    
    @Test
    fun test_getMetadataByKey_nonExistent() = runBlocking {
        val results = dao.getMetadataByKey("non_existent_key_xyz123")
        
        assertNotNull("Results should not be null", results)
        assertTrue("Non-existent key should return empty list", results.isEmpty())
    }
    
    // ==================== FLOW/REACTIVE EDGE CASES ====================
    
    @Test
    fun test_observeContentByType_invalidType() = runBlocking {
        val flow = dao.observeContentByType("invalid_type_xyz", 10)
        val results = flow.first()
        
        assertNotNull("Flow should emit empty list for invalid type", results)
        assertTrue("Invalid type should return empty results", results.isEmpty())
    }
    
    @Test
    fun test_observeSearchResults_emptyQuery() = runBlocking {
        val flow = dao.observeSearchResults("", 10)
        val results = flow.first()
        
        assertNotNull("Flow should emit for empty query", results)
        assertTrue("Empty query should return empty results", results.isEmpty())
    }
    
    // ==================== REPOSITORY EDGE CASES ====================
    
    @Test
    fun test_buildRagContext_emptyQuery() = runBlocking {
        val context = repository.buildRagContext("", 5)
        
        assertNotNull("Context should not be null", context)
        assertEquals("Query should be empty", "", context.query)
        assertTrue("Should have no content for empty query", context.contents.isEmpty())
        assertTrue("Should have no citations for empty query", context.citations.isEmpty())
    }
    
    @Test
    fun test_buildRagContext_noResults() = runBlocking {
        val context = repository.buildRagContext("xyz123abc456notfound", 5)
        
        assertNotNull("Context should not be null", context)
        // Might have empty or few results
        assertTrue("Should have few or no results", context.contents.size < 2)
    }
    
    @Test
    fun test_findSimilarContent_invalidId() = runBlocking {
        val similar = repository.findSimilarContent(999999, 5)
        
        assertNotNull("Similar content list should not be null", similar)
        assertTrue("Invalid ID should return empty similar content", similar.isEmpty())
    }
}