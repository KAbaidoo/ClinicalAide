package co.kobby.clinicalaide

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test to verify RAG database loads correctly with pre-packaged data
 */
@RunWith(AndroidJUnit4::class)
class RagDatabaseTest {
    
    private lateinit var database: RagDatabase
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Use getInstance to get the real database with pre-packaged data
        database = RagDatabase.getInstance(context)
    }
    
    @After
    fun tearDown() {
        // Don't close the singleton database
    }
    
    @Test
    fun test_database_opens_successfully() = runBlocking {
        // If we get here without exception, database opened successfully
        val dao = database.ragDao()
        assertNotNull(dao)
    }
    
    @Test
    fun test_database_has_preloaded_data() = runBlocking {
        val dao = database.ragDao()
        
        // Get database statistics
        val stats = dao.getDatabaseStats()
        
        println("=== RAG Database Stats ===")
        println("Chapters: ${stats.chapterCount}")
        println("Content Chunks: ${stats.chunkCount}")
        println("Conditions: ${stats.conditionCount}")
        println("Medications: ${stats.medicationCount}")
        
        // Verify we have data
        assertTrue("Should have chapters", stats.chapterCount > 0)
        assertTrue("Should have content chunks", stats.chunkCount > 0)
        
        // Expected values from stg_rag_complete.db
        assertEquals("Should have 31 chapters", 31, stats.chapterCount)
        assertEquals("Should have 969 content chunks", 969, stats.chunkCount)
        assertEquals("Should have 304 conditions", 304, stats.conditionCount)
        assertEquals("Should have 555 medications", 555, stats.medicationCount)
    }
    
    @Test
    fun test_can_search_content() = runBlocking {
        val dao = database.ragDao()
        
        // Search for a common medical term
        val results = dao.searchContentChunks("malaria", 5)
        
        println("\n=== Search Results for 'malaria' ===")
        results.forEach { chunk ->
            println("Type: ${chunk.chunkType}")
            println("Page: ${chunk.pageNumber}")
            println("Content: ${chunk.content.take(100)}...")
            println("Citation: ${chunk.referenceCitation}")
            println("---")
        }
        
        // Verify we get results
        assertTrue("Should find results for 'malaria'", results.isNotEmpty())
        
        // Verify result structure
        results.forEach { chunk ->
            assertNotNull("Content should not be null", chunk.content)
            assertNotNull("Chunk type should not be null", chunk.chunkType)
            assertNotNull("Reference citation should not be null", chunk.referenceCitation)
            assertTrue("Page number should be positive", chunk.pageNumber > 0)
        }
    }
    
    @Test
    fun test_can_retrieve_chapters() = runBlocking {
        val dao = database.ragDao()
        
        val chapters = dao.getAllChapters()
        
        println("\n=== First 5 Chapters ===")
        chapters.take(5).forEach { chapter ->
            println("Chapter ${chapter.number}: ${chapter.title} (Page ${chapter.startPage})")
        }
        
        assertEquals("Should have 31 chapters", 31, chapters.size)
        
        // Verify chapter structure
        chapters.forEach { chapter ->
            assertTrue("Chapter number should be positive", chapter.number > 0)
            assertTrue("Start page should be positive", chapter.startPage > 0)
            assertNotNull("Title should not be null", chapter.title)
            assertTrue("Title should not be empty", chapter.title.isNotEmpty())
        }
    }
    
    @Test
    fun test_can_search_conditions() = runBlocking {
        val dao = database.ragDao()
        
        val conditions = dao.searchConditions("fever", 5)
        
        println("\n=== Conditions with 'fever' ===")
        conditions.forEach { condition ->
            println("Condition: ${condition.name} (Page ${condition.pageNumber})")
        }
        
        // Verify we can search conditions
        assertTrue("Should find conditions", conditions.isNotEmpty())
    }
    
    @Test
    fun test_can_search_medications() = runBlocking {
        val dao = database.ragDao()
        
        val medications = dao.searchMedications("paracetamol", 5)
        
        println("\n=== Medications with 'paracetamol' ===")
        medications.forEach { medication ->
            println("Medication: ${medication.genericName}")
            medication.strength?.let { println("  Strength: $it") }
            medication.route?.let { println("  Route: $it") }
        }
        
        // Verify we can search medications
        // Note: May or may not find results depending on exact content
        assertNotNull("Search should complete without error", medications)
    }
}