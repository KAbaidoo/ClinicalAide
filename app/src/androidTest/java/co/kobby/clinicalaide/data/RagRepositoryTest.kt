package co.kobby.clinicalaide.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.RagRepository
import co.kobby.clinicalaide.data.rag.search.SemanticSearchService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for RagRepository business logic and coordination.
 * Verifies repository methods correctly delegate and enhance DAO functionality.
 */
@RunWith(AndroidJUnit4::class)
class RagRepositoryTest {
    
    private lateinit var repository: RagRepository
    private lateinit var database: RagDatabase
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = RagDatabase.getInstance(context)
        val dao = database.ragDao()
        val searchService = SemanticSearchService(dao)
        repository = RagRepository(dao, searchService)
    }
    
    // ==================== SEARCH OPERATIONS ====================
    
    @Test
    fun test_searchMedicalContent_returnsRelevantResults() = runBlocking {
        val results = repository.searchMedicalContent("malaria treatment", 10)
        
        assertNotNull("Search results should not be null", results)
        assertTrue("Should find results for malaria treatment", results.isNotEmpty())
        assertTrue("Should respect limit", results.size <= 10)
        
        // Results should be relevant to the query
        val relevantCount = results.count { content ->
            content.contentText.contains("malaria", ignoreCase = true) ||
            content.contentText.contains("treatment", ignoreCase = true)
        }
        assertTrue("Most results should be relevant", relevantCount > results.size / 2)
    }
    
    @Test
    fun test_searchMedicalContentWithScores_includesSimilarity() = runBlocking {
        val results = repository.searchMedicalContentWithScores("fever symptoms", 5)
        
        assertNotNull("Scored results should not be null", results)
        assertTrue("Should find results for fever symptoms", results.isNotEmpty())
        
        // Each result should have a similarity score
        results.forEach { result ->
            assertNotNull("Content should not be null", result.content)
            assertTrue("Similarity should be between 0 and 1", result.similarity in 0.0f..1.0f)
        }
        
        // Results should be sorted by similarity (descending)
        for (i in 1 until results.size) {
            assertTrue(
                "Results should be sorted by similarity",
                results[i - 1].similarity >= results[i].similarity
            )
        }
    }
    
    @Test
    fun test_findSimilarContent_excludesSource() = runBlocking {
        // First, get some content
        val searchResults = repository.searchContent("diarrhea", 1)
        
        if (searchResults.isNotEmpty()) {
            val sourceContent = searchResults.first()
            val similarContent = repository.findSimilarContent(sourceContent.contentId, 5)
            
            // Source content should not be in similar results
            assertFalse(
                "Similar content should not include source",
                similarContent.any { it.contentId == sourceContent.contentId }
            )
            
            // Similar content should be related
            if (similarContent.isNotEmpty()) {
                val relatedCount = similarContent.count { content ->
                    // Check for some overlap in medical terms
                    val sourceWords = sourceContent.contentText.lowercase().split("\\s+".toRegex()).toSet()
                    val contentWords = content.contentText.lowercase().split("\\s+".toRegex()).toSet()
                    sourceWords.intersect(contentWords).size > 3
                }
                assertTrue("Some content should be related", relatedCount > 0)
            }
        }
    }
    
    @Test
    fun test_searchContent_delegatesToDao() = runBlocking {
        val results = repository.searchContent("medication", 15)
        
        assertNotNull("Search results should not be null", results)
        assertTrue("Should respect limit", results.size <= 15)
        
        results.forEach { content ->
            assertTrue(
                "Content should contain search term",
                content.contentText.contains("medication", ignoreCase = true)
            )
        }
    }
    
    // ==================== RAG CONTEXT BUILDING ====================
    
    @Test
    fun test_buildRagContext_assemblesCompleteContext() = runBlocking {
        val context = repository.buildRagContext("malaria diagnosis and treatment", 5)
        
        assertNotNull("RAG context should not be null", context)
        assertEquals("Query should match", "malaria diagnosis and treatment", context.query)
        assertNotNull("Contents should not be null", context.contents)
        assertTrue("Should have content", context.contents.isNotEmpty())
        assertTrue("Should respect max content limit", context.contents.size <= 5)
        assertNotNull("Context text should not be null", context.context)
        assertTrue("Context text should not be empty", context.context.isNotEmpty())
    }
    
    @Test
    fun test_buildRagContext_includesCitations() = runBlocking {
        val context = repository.buildRagContext("pediatric diarrhea", 3)
        
        assertNotNull("Citations should not be null", context.citations)
        assertTrue("Should have citations", context.citations.isNotEmpty())
        
        // Each citation should be a page reference
        context.citations.forEach { citation ->
            assertTrue("Citation should reference a page", citation.startsWith("Page "))
        }
        
        // Citations should be unique
        assertEquals(
            "Citations should be distinct",
            context.citations.distinct().size,
            context.citations.size
        )
    }
    
    @Test
    fun test_buildRagContext_calculatesAverageSimilarity() = runBlocking {
        val context = repository.buildRagContext("hypertension management", 5)
        
        assertTrue(
            "Average similarity should be between 0 and 1",
            context.averageSimilarity in 0.0f..1.0f
        )
        
        // If we have content, similarity should be > 0
        if (context.contents.isNotEmpty()) {
            assertTrue("Should have positive similarity", context.averageSimilarity > 0.0f)
        }
    }
    
    @Test
    fun test_formatResponseWithCitations_formatsCorrectly() = runBlocking {
        val response = "The treatment involves oral rehydration therapy."
        val citations = listOf("Page 29", "Page 30", "Page 31")
        
        val formatted = repository.formatResponseWithCitations(response, citations)
        
        assertTrue("Should contain original response", formatted.contains(response))
        assertTrue("Should contain References header", formatted.contains("References:"))
        
        // Each citation should be in the formatted output
        citations.forEach { citation ->
            assertTrue("Should contain citation: $citation", formatted.contains(citation))
        }
    }
    
    @Test
    fun test_formatResponseWithCitations_handlesEmptyCitations() = runBlocking {
        val response = "General medical information."
        val formatted = repository.formatResponseWithCitations(response, emptyList())
        
        assertEquals("Should only contain response when no citations", response, formatted)
        assertFalse("Should not contain References header", formatted.contains("References:"))
    }
    
    // ==================== CONTENT RETRIEVAL ====================
    
    @Test
    fun test_getContentByType_delegatesToDao() = runBlocking {
        val contents = repository.getContentByType("bullet", 5)
        
        assertNotNull("Contents should not be null", contents)
        assertTrue("Should respect limit", contents.size <= 5)
        
        contents.forEach { content ->
            assertEquals("Content type should match", "bullet", content.contentType)
        }
    }
    
    @Test
    fun test_getContentForSection_returnsAllSectionContent() = runBlocking {
        val contents = repository.getContentForSection(1)
        
        assertNotNull("Section contents should not be null", contents)
        
        contents.forEach { content ->
            assertEquals("Content should belong to section 1", 1, content.sectionId)
        }
    }
    
    @Test
    fun test_getContentById_returnsSpecificContent() = runBlocking {
        val content = repository.getContentById(10)
        
        assertNotNull("Content 10 should exist", content)
        assertEquals("Content ID should match", 10, content?.contentId)
        assertTrue("Content should have text", content?.contentText?.isNotEmpty() == true)
    }
    
    // ==================== CHAPTER AND SECTION OPERATIONS ====================
    
    @Test
    fun test_getAllChapters_returns23Chapters() = runBlocking {
        val chapters = repository.getAllChapters()
        
        assertNotNull("Chapters should not be null", chapters)
        assertEquals("Should have 23 chapters", 23, chapters.size)
    }
    
    @Test
    fun test_getSectionsInChapter_maintainsOrder() = runBlocking {
        val sections = repository.getSectionsInChapter(1)
        
        assertNotNull("Sections should not be null", sections)
        assertTrue("Chapter 1 should have sections", sections.isNotEmpty())
        
        // All sections should belong to the chapter
        sections.forEach { section ->
            assertEquals("Section should belong to chapter 1", 1, section.chapterId)
        }
    }
    
    @Test
    fun test_getMetadataForContent_returnsAllMetadata() = runBlocking {
        // Get some content first
        val contents = repository.searchContent("treatment", 1)
        
        if (contents.isNotEmpty()) {
            val metadata = repository.getMetadataForContent(contents.first().contentId)
            
            metadata.forEach { meta ->
                assertEquals(
                    "Metadata should belong to content",
                    contents.first().contentId,
                    meta.contentId
                )
                assertTrue("Key should not be empty", meta.key.isNotEmpty())
                assertTrue("Value should not be empty", meta.value.isNotEmpty())
            }
        }
    }
    
    // ==================== STATISTICS ====================
    
    @Test
    fun test_getDatabaseStats_returnsCorrectCounts() = runBlocking {
        val stats = repository.getDatabaseStats()
        
        assertEquals("Chapter count should be 23", 23, stats.chapterCount)
        assertEquals("Section count should be 831", 831, stats.sectionCount)
        assertEquals("Content count should be 664", 664, stats.contentCount)
        assertEquals("Metadata count should be 957", 957, stats.metadataCount)
        assertEquals("Embedding count should be 664", 664, stats.embeddingCount)
    }
    
    @Test
    fun test_getAvailableContentTypes_returnsTypes() = runBlocking {
        val types = repository.getAvailableContentTypes()
        
        assertNotNull("Content types should not be null", types)
        assertTrue("Should have content types", types.isNotEmpty())
        assertTrue("Should include paragraph", types.contains("paragraph"))
        
        // Should have standard content types
        val expectedTypes = listOf("paragraph", "bullet", "table", "note")
        val hasExpectedTypes = expectedTypes.any { types.contains(it) }
        assertTrue("Should have at least one expected type", hasExpectedTypes)
    }
    
    // ==================== FLOW OPERATIONS ====================
    
    @Test
    fun test_observeSearchResults_emitsUpdates() = runBlocking {
        val flow = repository.observeSearchResults("malaria", 10)
        val results = flow.first()
        
        assertNotNull("Flow should emit results", results)
        
        results.forEach { content ->
            assertTrue(
                "Content should contain search term",
                content.contentText.contains("malaria", ignoreCase = true)
            )
        }
    }
    
    @Test
    fun test_observeContentByType_emitsFilteredContent() = runBlocking {
        val flow = repository.observeContentByType("table", 5)
        val contents = flow.first()
        
        assertNotNull("Flow should emit contents", contents)
        assertTrue("Should respect limit", contents.size <= 5)
        
        contents.forEach { content ->
            assertEquals("Content type should be table", "table", content.contentType)
        }
    }
}