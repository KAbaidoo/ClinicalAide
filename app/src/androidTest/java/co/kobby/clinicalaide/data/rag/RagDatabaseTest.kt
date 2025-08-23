package co.kobby.clinicalaide.data.rag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.dao.RagDao
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test to verify the pre-populated RAG database loads correctly
 */
@RunWith(AndroidJUnit4::class)
class RagDatabaseTest {
    
    private lateinit var database: RagDatabase
    private lateinit var dao: RagDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = RagDatabase.getInstance(context)
        dao = database.ragDao()
    }
    
    @Test
    fun test_prePopulated_database_loads_successfully() = runBlocking {
        // Test that we can access the pre-populated data
        val stats = dao.getDatabaseStats()
        
        // Verify expected data from the stg_rag.db
        assertThat("Should have 31 chapters", stats.chapterCount, `is`(31))
        assertThat("Should have 969 content chunks", stats.chunkCount, `is`(969))
        assertThat("Should have 304 conditions", stats.conditionCount, `is`(304))
        assertThat("Should have 555 medications", stats.medicationCount, `is`(555))
    }
    
    @Test
    fun test_can_query_chapters() = runBlocking {
        val chapters = dao.getAllChapters()
        
        assertThat(chapters, notNullValue())
        assertThat(chapters.size, `is`(31))
        
        // Check first chapter
        val firstChapter = chapters.first()
        assertThat(firstChapter.number, `is`(1))
        assertThat(firstChapter.title, notNullValue())
    }
    
    @Test
    fun test_can_search_content() = runBlocking {
        // Search for a common medical term
        val results = dao.searchContentChunks("malaria", 10)
        
        assertThat(results, notNullValue())
        assertThat("Should find content about malaria", results, not(empty()))
        
        // Verify the results have required fields
        if (results.isNotEmpty()) {
            val firstResult = results.first()
            assertThat(firstResult.content, notNullValue())
            assertThat(firstResult.referenceCitation, notNullValue())
            assertThat(firstResult.pageNumber, greaterThan(0))
        }
    }
    
    @Test
    fun test_chunk_types_available() = runBlocking {
        val chunkTypes = dao.getChunkTypes()
        
        assertThat(chunkTypes, notNullValue())
        assertThat(chunkTypes, not(empty()))
        
        // Expected chunk types based on the database
        val expectedTypes = listOf("treatment", "medication", "clinical_features", "investigations")
        assertThat(chunkTypes, hasItems(*expectedTypes.toTypedArray()))
    }
    
    @Test
    fun test_full_text_search_works() = runBlocking {
        // Try a more complex search
        val results = dao.fullTextSearch("hypertension", 5)
        
        assertThat(results, notNullValue())
        // May or may not find results depending on content, so just check it doesn't crash
    }
}