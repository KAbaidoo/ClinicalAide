package co.kobby.clinicalaide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic test to verify RAG database loads and has data
 */
@RunWith(AndroidJUnit4::class)
class BasicDatabaseTest {
    
    @Test
    fun test_database_loads_and_has_data() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        try {
            // Get the database instance
            val database = RagDatabase.getInstance(context)
            val dao = database.ragDao()
            
            println("✅ Database opened successfully!")
            
            // Test 1: Check database statistics
            val stats = dao.getDatabaseStats()
            println("Database Statistics:")
            println("  Chapters: ${stats.chapterCount}")
            println("  Sections: ${stats.sectionCount}")
            println("  Content: ${stats.contentCount}")
            println("  Metadata: ${stats.metadataCount}")
            println("  Embeddings: ${stats.embeddingCount}")
            
            assertTrue("Should have chapters", stats.chapterCount > 0)
            assertTrue("Should have content", stats.contentCount > 0)
            
            // Test 2: Get all chapters
            val chapters = dao.getAllChapters()
            println("\nFound ${chapters.size} chapters")
            if (chapters.isNotEmpty()) {
                println("First chapter: ${chapters.first().chapterNumber} - ${chapters.first().chapterTitle}")
            }
            
            // Test 3: Simple search test
            val searchResults = dao.searchContent("malaria", 5)
            println("\nSearch for 'malaria' returned ${searchResults.size} results")
            searchResults.forEach { content ->
                println("  - Type: ${content.contentType}, Page: ${content.pageNumber}")
            }
            
            // Test 4: Get content by type
            val contentTypes = dao.getContentTypes()
            println("\nContent types in database: $contentTypes")
            
            if (contentTypes.isNotEmpty()) {
                val sampleContent = dao.getContentByType(contentTypes.first(), 3)
                println("Sample content of type '${contentTypes.first()}': ${sampleContent.size} items")
            }
            
            println("\n✅ All basic database tests passed!")
            
        } catch (e: Exception) {
            println("❌ Database test failed: ${e.message}")
            e.printStackTrace()
            fail("Database failed to load: ${e.message}")
        }
    }
}