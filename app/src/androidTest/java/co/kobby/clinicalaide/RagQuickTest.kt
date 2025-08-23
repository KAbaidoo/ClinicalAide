package co.kobby.clinicalaide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quick test to verify RAG database loads
 */
@RunWith(AndroidJUnit4::class)
class RagQuickTest {
    
    @Test
    fun test_rag_database_opens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        try {
            val database = RagDatabase.getInstance(context)
            val dao = database.ragDao()
            
            // Try to get stats
            val stats = dao.getDatabaseStats()
            
            println("=== RAG Database Stats ===")
            println("Chapters: ${stats.chapterCount}")
            println("Content Chunks: ${stats.chunkCount}")
            println("Conditions: ${stats.conditionCount}")
            println("Medications: ${stats.medicationCount}")
            
            // Try a search
            val results = dao.searchContentChunks("malaria", 3)
            println("\n=== Search Results for 'malaria' ===")
            results.forEach { chunk ->
                println("Type: ${chunk.chunkType}")
                println("Content: ${chunk.content.take(100)}...")
                println("Citation: ${chunk.referenceCitation}")
                println("---")
            }
            
            assert(stats.chapterCount > 0) { "Should have chapters" }
            assert(stats.chunkCount > 0) { "Should have content chunks" }
            
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}