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
 * Simple test to verify RAG database loads without schema errors
 */
@RunWith(AndroidJUnit4::class)
class RagDatabaseSimpleTest {
    
    @Test
    fun test_rag_database_loads_successfully() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        try {
            // This will use the singleton instance with pre-packaged database
            val database = RagDatabase.getInstance(context)
            val dao = database.ragDao()
            
            // If we get here without exception, schema validation passed
            println("✅ RAG Database loaded successfully - schema validation passed!")
            
            // Quick test to verify data exists
            val stats = dao.getDatabaseStats()
            println("Chapters: ${stats.chapterCount}")
            println("Content Chunks: ${stats.chunkCount}")
            println("Conditions: ${stats.conditionCount}")
            println("Medications: ${stats.medicationCount}")
            
            assertEquals("Should have 31 chapters", 31, stats.chapterCount)
            assertEquals("Should have 969 content chunks", 969, stats.chunkCount)
            
        } catch (e: Exception) {
            fail("Database failed to load: ${e.message}")
        }
    }
}