package co.kobby.clinicalaide.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.dao.RagDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive tests for RagDao database operations.
 * Tests all DAO methods with the pre-packaged Ghana STG database.
 */
@RunWith(AndroidJUnit4::class)
class RagDaoTest {
    
    private lateinit var database: RagDatabase
    private lateinit var dao: RagDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = RagDatabase.getInstance(context)
        dao = database.ragDao()
    }
    
    // ==================== CHAPTER TESTS ====================
    
    @Test
    fun test_getAllChapters_returnsAllChapters() = runBlocking {
        val chapters = dao.getAllChapters()
        
        assertNotNull("Chapters should not be null", chapters)
        assertEquals("Should have 23 chapters", 23, chapters.size)
        
        // Verify first chapter
        val firstChapter = chapters.first()
        assertTrue("First chapter should have valid title", firstChapter.chapterTitle.isNotEmpty())
        assertTrue("First chapter should have chapter number", firstChapter.chapterNumber.isNotEmpty())
    }
    
    @Test
    fun test_getChapterById_returnsCorrectChapter() = runBlocking {
        val chapter = dao.getChapterById(1)
        
        assertNotNull("Chapter 1 should exist", chapter)
        assertEquals("Should be Chapter 1", "Chapter 1", chapter?.chapterNumber)
        assertTrue("Should have a title", chapter?.chapterTitle?.isNotEmpty() == true)
    }
    
    @Test
    fun test_getChapterCount_returns23() = runBlocking {
        val count = dao.getChapterCount()
        assertEquals("Should have 23 chapters", 23, count)
    }
    
    // ==================== SECTION TESTS ====================
    
    @Test
    fun test_getSectionsByChapter_returnsCorrectSections() = runBlocking {
        val sections = dao.getSectionsByChapter(1)
        
        assertNotNull("Sections should not be null", sections)
        assertTrue("Chapter 1 should have sections", sections.isNotEmpty())
        
        // All sections should belong to chapter 1
        sections.forEach { section ->
            assertEquals("Section should belong to chapter 1", 1, section.chapterId)
        }
    }
    
    @Test
    fun test_getSectionById_returnsCorrectSection() = runBlocking {
        val section = dao.getSectionById(1)
        
        assertNotNull("Section 1 should exist", section)
        assertTrue("Section should have a title", section?.sectionTitle?.isNotEmpty() == true)
    }
    
    @Test
    fun test_getChildSections_returnsHierarchy() = runBlocking {
        // Find a section with children
        val allSections = dao.getSectionsByChapter(1)
        val parentSection = allSections.firstOrNull { it.parentSectionId == null }
        
        if (parentSection != null) {
            val childSections = dao.getChildSections(parentSection.sectionId)
            
            // All children should reference the parent
            childSections.forEach { child ->
                assertEquals("Child should reference parent", parentSection.sectionId, child.parentSectionId)
            }
        }
    }
    
    @Test
    fun test_getSectionCount_returns831() = runBlocking {
        val count = dao.getSectionCount()
        assertEquals("Should have 831 sections", 831, count)
    }
    
    // ==================== CONTENT TESTS ====================
    
    @Test
    fun test_getContentById_returnsCorrectContent() = runBlocking {
        val content = dao.getContentById(1)
        
        assertNotNull("Content 1 should exist", content)
        assertTrue("Content should have text", content?.contentText?.isNotEmpty() == true)
        assertTrue("Content should have valid page number", (content?.pageNumber ?: 0) > 0)
    }
    
    @Test
    fun test_getContentBySection_returnsPageOrderedContent() = runBlocking {
        val contents = dao.getContentBySection(1)
        
        assertNotNull("Contents should not be null", contents)
        if (contents.size > 1) {
            // Contents should be ordered by content_id (which follows page order)
            for (i in 1 until contents.size) {
                assertTrue(
                    "Contents should be ordered by ID",
                    contents[i].contentId >= contents[i - 1].contentId
                )
            }
        }
    }
    
    @Test
    fun test_getContentByType_filtersCorrectly() = runBlocking {
        val paragraphs = dao.getContentByType("paragraph", 5)
        val bullets = dao.getContentByType("bullet", 5)
        
        // Verify type filtering
        paragraphs.forEach { content ->
            assertEquals("Should be paragraph type", "paragraph", content.contentType)
        }
        
        bullets.forEach { content ->
            assertEquals("Should be bullet type", "bullet", content.contentType)
        }
    }
    
    @Test
    fun test_searchContent_ranksResults() = runBlocking {
        val results = dao.searchContent("malaria", 10)
        
        assertNotNull("Search results should not be null", results)
        assertTrue("Should find results for 'malaria'", results.isNotEmpty())
        
        // Results should contain the search term
        results.forEach { content ->
            assertTrue(
                "Content should contain 'malaria'",
                content.contentText.contains("malaria", ignoreCase = true)
            )
        }
    }
    
    @Test
    fun test_fullTextSearch_searchesSectionsAndContent() = runBlocking {
        val results = dao.fullTextSearch("treatment", 20)
        
        assertNotNull("Full text search should not be null", results)
        assertTrue("Should find results for 'treatment'", results.isNotEmpty())
        assertTrue("Should respect limit", results.size <= 20)
    }
    
    @Test
    fun test_getContentCount_returns664() = runBlocking {
        val count = dao.getContentCount()
        assertEquals("Should have 664 content entries", 664, count)
    }
    
    @Test
    fun test_getContentTypes_returnsDistinctTypes() = runBlocking {
        val types = dao.getContentTypes()
        
        assertNotNull("Content types should not be null", types)
        assertTrue("Should have content types", types.isNotEmpty())
        assertTrue("Should include 'paragraph'", types.contains("paragraph"))
        
        // Types should be distinct
        assertEquals("Types should be unique", types.distinct().size, types.size)
    }
    
    // ==================== METADATA TESTS ====================
    
    @Test
    fun test_getMetadataByContent_returnsAllMetadata() = runBlocking {
        // Find content with metadata
        val contentWithMetadata = dao.searchContent("treatment", 10)
        
        if (contentWithMetadata.isNotEmpty()) {
            val metadata = dao.getMetadataByContent(contentWithMetadata.first().contentId)
            
            // Each metadata should belong to the content
            metadata.forEach { meta ->
                assertEquals(
                    "Metadata should belong to content",
                    contentWithMetadata.first().contentId,
                    meta.contentId
                )
                assertTrue("Metadata key should not be empty", meta.key.isNotEmpty())
                assertTrue("Metadata value should not be empty", meta.value.isNotEmpty())
            }
        }
    }
    
    @Test
    fun test_searchMetadata_filtersByKeyValue() = runBlocking {
        val metadata = dao.searchMetadata("target_population", "children")
        
        // All results should match the key and contain the value
        metadata.forEach { meta ->
            assertEquals("Key should match", "target_population", meta.key)
            assertTrue(
                "Value should contain 'children'",
                meta.value.contains("children", ignoreCase = true)
            )
        }
    }
    
    // ==================== EMBEDDING TESTS ====================
    
    @Test
    fun test_getEmbeddingByContent_returnsEmbedding() = runBlocking {
        val embedding = dao.getEmbeddingByContent(1)
        
        assertNotNull("Content 1 should have embedding", embedding)
        assertEquals("Embedding should reference content 1", 1, embedding?.contentId)
        assertTrue("Embedding should have data", embedding?.embedding?.isNotEmpty() == true)
        
        // Verify embedding dimensions (384 dimensions * 4 bytes per float)
        assertEquals("Embedding should be 384 dimensions", 1536, embedding?.embedding?.size)
    }
    
    @Test
    fun test_getEmbeddingCount_returns664() = runBlocking {
        val count = dao.getEmbeddingCount()
        assertEquals("Should have 664 embeddings", 664, count)
    }
    
    // ==================== STATISTICS TESTS ====================
    
    @Test
    fun test_getDatabaseStats_returnsCorrectCounts() = runBlocking {
        val stats = dao.getDatabaseStats()
        
        assertEquals("Chapter count should be 23", 23, stats.chapterCount)
        assertEquals("Section count should be 831", 831, stats.sectionCount)
        assertEquals("Content count should be 664", 664, stats.contentCount)
        assertEquals("Metadata count should be 957", 957, stats.metadataCount)
        assertEquals("Embedding count should be 664", 664, stats.embeddingCount)
    }
    
    // ==================== FLOW/REACTIVE TESTS ====================
    
    @Test
    fun test_observeContentByType_emitsFilteredContent() = runBlocking {
        val flow = dao.observeContentByType("paragraph", 5)
        val contents = flow.first()
        
        assertNotNull("Flow should emit contents", contents)
        assertTrue("Should have paragraph contents", contents.isNotEmpty())
        assertTrue("Should respect limit", contents.size <= 5)
        
        contents.forEach { content ->
            assertEquals("Content should be paragraph type", "paragraph", content.contentType)
        }
    }
    
    @Test
    fun test_observeSearchResults_emitsFilteredContent() = runBlocking {
        val flow = dao.observeSearchResults("fever", 10)
        val contents = flow.first()
        
        assertNotNull("Flow should emit search results", contents)
        
        contents.forEach { content ->
            assertTrue(
                "Content should contain 'fever'",
                content.contentText.contains("fever", ignoreCase = true)
            )
        }
    }
}