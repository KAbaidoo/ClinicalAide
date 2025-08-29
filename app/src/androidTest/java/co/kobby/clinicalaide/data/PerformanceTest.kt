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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance tests for the RAG database and search operations.
 * Benchmarks query speed, memory usage, and concurrent access patterns.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTest {
    
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
    
    // ==================== QUERY PERFORMANCE TESTS ====================
    
    @Test
    fun test_searchContent_under100ms() = runBlocking {
        // Warm up the database
        dao.searchContent("test", 1)
        
        // Measure search performance
        val searchTime = measureTimeMillis {
            val results = dao.searchContent("malaria", 20)
            assertNotNull("Results should not be null", results)
        }
        
        println("Search query took: ${searchTime}ms")
        assertTrue(
            "Search should complete under 100ms (actual: ${searchTime}ms)",
            searchTime < 100
        )
    }
    
    @Test
    fun test_getAllChapters_under50ms() = runBlocking {
        // Warm up
        dao.getAllChapters()
        
        // Measure performance
        val loadTime = measureTimeMillis {
            val chapters = dao.getAllChapters()
            assertEquals("Should load 23 chapters", 23, chapters.size)
        }
        
        println("Loading chapters took: ${loadTime}ms")
        assertTrue(
            "Chapter loading should complete under 50ms (actual: ${loadTime}ms)",
            loadTime < 50
        )
    }
    
    @Test
    fun test_buildRagContext_under500ms() = runBlocking {
        // Warm up
        repository.buildRagContext("test", 1)
        
        // Measure RAG context building
        val contextTime = measureTimeMillis {
            val context = repository.buildRagContext("pediatric malaria treatment", 5)
            assertNotNull("Context should not be null", context)
            assertTrue("Should have content", context.contents.isNotEmpty())
        }
        
        println("Building RAG context took: ${contextTime}ms")
        assertTrue(
            "RAG context should build under 500ms (actual: ${contextTime}ms)",
            contextTime < 500
        )
    }
    
    @Test
    fun test_fullTextSearch_under200ms() = runBlocking {
        // Warm up
        dao.fullTextSearch("test", 1)
        
        // Measure full text search
        val searchTime = measureTimeMillis {
            val results = dao.fullTextSearch("diarrhea treatment children", 30)
            assertNotNull("Results should not be null", results)
        }
        
        println("Full text search took: ${searchTime}ms")
        assertTrue(
            "Full text search should complete under 200ms (actual: ${searchTime}ms)",
            searchTime < 200
        )
    }
    
    @Test
    fun test_semanticSearch_under300ms() = runBlocking {
        // Warm up
        searchService.searchContent("test", 1)
        
        // Measure semantic search
        val searchTime = measureTimeMillis {
            val results = searchService.searchContent("fever and headache symptoms", 10)
            assertNotNull("Results should not be null", results)
            assertTrue("Should find results", results.isNotEmpty())
        }
        
        println("Semantic search took: ${searchTime}ms")
        assertTrue(
            "Semantic search should complete under 300ms (actual: ${searchTime}ms)",
            searchTime < 300
        )
    }
    
    // ==================== BATCH OPERATION TESTS ====================
    
    @Test
    fun test_batchContentRetrieval_efficient() = runBlocking {
        val sectionIds = (1..10).toList()
        
        val batchTime = measureTimeMillis {
            val contents = sectionIds.map { sectionId ->
                dao.getContentBySection(sectionId)
            }
            assertTrue("Should retrieve content", contents.isNotEmpty())
        }
        
        println("Batch content retrieval (10 sections) took: ${batchTime}ms")
        assertTrue(
            "Batch retrieval should be under 500ms (actual: ${batchTime}ms)",
            batchTime < 500
        )
    }
    
    @Test
    fun test_multipleSearches_performWell() = runBlocking {
        val queries = listOf(
            "malaria", "diarrhea", "fever", "hypertension", "diabetes",
            "pregnancy", "pediatric", "emergency", "infection", "treatment"
        )
        
        val totalTime = measureTimeMillis {
            queries.forEach { query ->
                val results = dao.searchContent(query, 5)
                assertNotNull("Results for '$query' should not be null", results)
            }
        }
        
        val averageTime = totalTime / queries.size
        println("Average search time: ${averageTime}ms (total: ${totalTime}ms for ${queries.size} queries)")
        assertTrue(
            "Average search time should be under 50ms (actual: ${averageTime}ms)",
            averageTime < 50
        )
    }
    
    // ==================== CONCURRENT ACCESS TESTS ====================
    
    @Test
    fun test_concurrentSearches_handleWell() = runBlocking {
        val queries = listOf(
            "malaria treatment",
            "pediatric dosage",
            "emergency care",
            "hypertension management",
            "diabetes medication"
        )
        
        val concurrentTime = measureTimeMillis {
            val results = queries.map { query ->
                async {
                    dao.searchContent(query, 10)
                }
            }.awaitAll()
            
            assertEquals("Should complete all queries", queries.size, results.size)
            results.forEach { result ->
                assertNotNull("Each result should not be null", result)
            }
        }
        
        println("Concurrent searches (${queries.size}) took: ${concurrentTime}ms")
        assertTrue(
            "Concurrent searches should complete under 300ms (actual: ${concurrentTime}ms)",
            concurrentTime < 300
        )
    }
    
    @Test
    fun test_concurrentDifferentOperations() = runBlocking {
        val mixedTime = measureTimeMillis {
            val results = listOf(
                async { dao.getAllChapters() },
                async { dao.searchContent("malaria", 10) },
                async { dao.getDatabaseStats() },
                async { dao.getContentTypes() },
                async { searchService.searchContent("fever", 5) }
            ).awaitAll()
            
            assertEquals("Should complete all operations", 5, results.size)
        }
        
        println("Mixed concurrent operations took: ${mixedTime}ms")
        assertTrue(
            "Mixed operations should complete under 400ms (actual: ${mixedTime}ms)",
            mixedTime < 400
        )
    }
    
    // ==================== LARGE RESULT SET TESTS ====================
    
    @Test
    fun test_largeSearchResults_memoryEfficient() = runBlocking {
        // Get initial memory
        val runtime = Runtime.getRuntime()
        System.gc()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Perform large search
        val searchTime = measureTimeMillis {
            val results = dao.searchContent("a", 100) // Common letter, many results
            assertNotNull("Results should not be null", results)
            assertTrue("Should respect limit", results.size <= 100)
        }
        
        // Check memory after
        System.gc()
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsed = (finalMemory - initialMemory) / (1024 * 1024) // Convert to MB
        
        println("Large search took: ${searchTime}ms, memory delta: ${memoryUsed}MB")
        assertTrue(
            "Large search should complete under 200ms (actual: ${searchTime}ms)",
            searchTime < 200
        )
        assertTrue(
            "Memory increase should be under 10MB (actual: ${memoryUsed}MB)",
            memoryUsed < 10
        )
    }
    
    @Test
    fun test_allContentTypes_loadEfficiently() = runBlocking {
        val types = listOf("paragraph", "bullet", "table", "note")
        
        val loadTime = measureTimeMillis {
            types.forEach { type ->
                val contents = dao.getContentByType(type, 50)
                assertNotNull("Contents for type '$type' should not be null", contents)
                assertTrue("Should respect limit", contents.size <= 50)
            }
        }
        
        println("Loading all content types took: ${loadTime}ms")
        assertTrue(
            "Loading all types should complete under 400ms (actual: ${loadTime}ms)",
            loadTime < 400
        )
    }
    
    // ==================== DATABASE STATISTICS PERFORMANCE ====================
    
    @Test
    fun test_databaseStats_quickRetrieval() = runBlocking {
        // Warm up
        dao.getDatabaseStats()
        
        val statsTime = measureTimeMillis {
            val stats = dao.getDatabaseStats()
            assertEquals("Should have correct counts", 23, stats.chapterCount)
            assertEquals("Should have correct counts", 831, stats.sectionCount)
            assertEquals("Should have correct counts", 664, stats.contentCount)
        }
        
        println("Getting database stats took: ${statsTime}ms")
        assertTrue(
            "Stats retrieval should be under 20ms (actual: ${statsTime}ms)",
            statsTime < 20
        )
    }
    
    // ==================== PAGINATION TESTS ====================
    
    @Test
    fun test_paginatedSearch_efficient() = runBlocking {
        val pageSize = 20
        val pages = 5
        
        val paginationTime = measureTimeMillis {
            for (page in 0 until pages) {
                val results = dao.searchContent("treatment", pageSize)
                assertNotNull("Page $page results should not be null", results)
                assertTrue("Should respect page size", results.size <= pageSize)
            }
        }
        
        val averagePageTime = paginationTime / pages
        println("Paginated search average: ${averagePageTime}ms per page")
        assertTrue(
            "Each page should load under 50ms (actual: ${averagePageTime}ms)",
            averagePageTime < 50
        )
    }
    
    // ==================== COLD START TESTS ====================
    
    @Test
    fun test_coldStart_firstQuery() = runBlocking {
        // Note: This test should be run first or after clearing app data for true cold start
        val coldStartTime = measureTimeMillis {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val freshDatabase = RagDatabase.getInstance(context)
            val freshDao = freshDatabase.ragDao()
            
            val results = freshDao.searchContent("malaria", 10)
            assertNotNull("Cold start results should not be null", results)
        }
        
        println("Cold start query took: ${coldStartTime}ms")
        assertTrue(
            "Cold start should complete under 500ms (actual: ${coldStartTime}ms)",
            coldStartTime < 500
        )
    }
    
    // ==================== STRESS TESTS ====================
    
    @Test
    fun test_repeatedQueries_noDegradation() = runBlocking {
        val iterations = 50
        val times = mutableListOf<Long>()
        
        repeat(iterations) {
            val time = measureTimeMillis {
                dao.searchContent("treatment", 5)
            }
            times.add(time)
        }
        
        val firstHalf = times.take(iterations / 2).average()
        val secondHalf = times.drop(iterations / 2).average()
        
        println("First half average: ${firstHalf}ms, Second half average: ${secondHalf}ms")
        
        // Performance shouldn't degrade significantly
        assertTrue(
            "Performance should not degrade (first: ${firstHalf}ms, second: ${secondHalf}ms)",
            secondHalf < firstHalf * 1.5 // Allow 50% degradation max
        )
    }
}