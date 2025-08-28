package co.kobby.clinicalaide

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct database test that skips Room validation
 */
@RunWith(AndroidJUnit4::class)
class DirectDatabaseTest {
    
    @Test
    fun test_database_connection_with_fallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        try {
            // Create database with schema validation disabled
            val database = Room.databaseBuilder(
                context.applicationContext,
                RagDatabase::class.java,
                "test_stg_rag.db"
            )
                .createFromAsset("databases/stg_rag.db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
            
            val dao = database.ragDao()
            
            println("✅ Database opened successfully!")
            
            // Test basic queries
            val stats = dao.getDatabaseStats()
            println("Database Statistics:")
            println("  Chapters: ${stats.chapterCount}")
            println("  Sections: ${stats.sectionCount}")
            println("  Content: ${stats.contentCount}")
            println("  Metadata: ${stats.metadataCount}")
            println("  Embeddings: ${stats.embeddingCount}")
            
            assertTrue("Should have chapters", stats.chapterCount > 0)
            assertTrue("Should have content", stats.contentCount > 0)
            
            // Test getting chapters
            val chapters = dao.getAllChapters()
            println("\nFound ${chapters.size} chapters")
            if (chapters.isNotEmpty()) {
                println("First chapter: ${chapters.first().chapterNumber} - ${chapters.first().chapterTitle}")
            }
            
            println("\n✅ All direct database tests passed!")
            
            database.close()
            
        } catch (e: Exception) {
            println("❌ Database test failed: ${e.message}")
            e.printStackTrace()
            fail("Database failed to load: ${e.message}")
        }
    }
}