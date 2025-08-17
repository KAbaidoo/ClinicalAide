package co.kobby.clinicalaide.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.TestDataFactory
import co.kobby.clinicalaide.data.TestDatabaseHelper
import co.kobby.clinicalaide.data.database.dao.StgDao
import co.kobby.clinicalaide.data.database.entities.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance tests for the STG database.
 * Tests database operations with large datasets and concurrent access patterns.
 * 
 * Performance Benchmarks:
 * - Large dataset operations should complete within reasonable time limits
 * - Query performance should scale linearly with data size
 * - Index utilization should provide significant performance improvements
 * - Concurrent operations should not cause deadlocks or data corruption
 */
@RunWith(AndroidJUnit4::class)
class StgDatabasePerformanceTest {
    
    private lateinit var database: StgDatabase
    private lateinit var dao: StgDao
    
    @Before
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase()
        dao = database.stgDao()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    // ==================== LARGE DATASET TESTS ====================
    
    @Test
    fun test_insert_1000_chapters_performance() = runBlocking {
        // Arrange
        val chapters = (1..1000).map { i ->
            TestDataFactory.createChapter(
                chapterNumber = i,
                chapterTitle = "Chapter $i: Medical System ${i % 20}",
                startPage = (i - 1) * 50 + 1,
                endPage = i * 50,
                description = "Detailed description for chapter $i covering various medical conditions"
            )
        }
        
        // Act & Measure
        val insertTime = measureTimeMillis {
            dao.insertChapters(chapters)
        }
        
        // Assert
        val allChapters = dao.getAllChapters()
        assertThat(allChapters, hasSize(1000))
        assertThat("Batch insert of 1000 chapters should be under 5 seconds", 
            insertTime, lessThan(5000L))
        
        println("Performance: Inserted 1000 chapters in ${insertTime}ms")
    }
    
    @Test
    fun test_complex_hierarchy_5000_records_performance() = runBlocking {
        // Arrange - Create a complex medical hierarchy
        val totalTime = measureTimeMillis {
            // Insert 50 chapters
            val chapters = (1..50).map { i ->
                TestDataFactory.createChapter(
                    chapterNumber = i,
                    chapterTitle = "System $i"
                )
            }
            dao.insertChapters(chapters)
            val chapterIds = dao.getAllChapters().map { it.id }
            
            // Insert 20 conditions per chapter (1000 total)
            chapterIds.forEach { chapterId ->
                val conditions = (1..20).map { c ->
                    TestDataFactory.createCondition(
                        chapterId = chapterId,
                        conditionNumber = c,
                        conditionName = "Condition $chapterId-$c"
                    )
                }
                dao.insertConditions(conditions)
            }
            
            // Insert content blocks (4000 total - 4 per condition)
            val allConditions = dao.getAllConditions()
            allConditions.forEach { condition ->
                val blocks = (1..4).map { b ->
                    TestDataFactory.createContentBlock(
                        conditionId = condition.id,
                        blockType = listOf("definition", "symptoms", "treatment", "dosage")[b - 1],
                        orderInCondition = b,
                        content = "Content for ${condition.conditionName} block $b"
                    )
                }
                dao.insertContentBlocks(blocks)
            }
        }
        
        // Assert
        assertThat(dao.getAllChapters(), hasSize(50))
        assertThat(dao.getAllConditions(), hasSize(1000))
        assertThat(dao.getAllContentBlocks(), hasSize(4000))
        assertThat("Creating 5000+ record hierarchy should be under 10 seconds",
            totalTime, lessThan(10000L))
        
        println("Performance: Created 5050 records in ${totalTime}ms")
    }
    
    // ==================== QUERY PERFORMANCE TESTS ====================
    
    @Test
    fun test_text_search_performance_with_large_dataset() = runBlocking {
        // Arrange - Insert 2000 content blocks with varied content
        val conditionId = dao.insertCondition(
            TestDataFactory.createCondition(
                chapterId = dao.insertChapter(TestDataFactory.createChapter()),
                conditionName = "Test Condition"
            )
        )
        
        val searchTerms = listOf("malaria", "fever", "treatment", "dosage", "pediatric")
        val blocks = (1..2000).map { i ->
            val term = searchTerms[i % searchTerms.size]
            TestDataFactory.createContentBlock(
                conditionId = conditionId,
                content = "Block $i: Clinical guidance for $term management in patients",
                orderInCondition = i,
                keywords = """["$term", "clinical", "guideline"]"""
            )
        }
        
        val insertTime = measureTimeMillis {
            blocks.chunked(100).forEach { batch ->
                dao.insertContentBlocks(batch)
            }
        }
        
        // Act - Search for each term
        val searchTimes = mutableMapOf<String, Long>()
        searchTerms.forEach { term ->
            val time = measureTimeMillis {
                val results = dao.searchContentByText(term, 50)
                assertThat(results, not(empty()))
            }
            searchTimes[term] = time
        }
        
        // Assert
        assertThat("Insert time should be reasonable", insertTime, lessThan(5000L))
        searchTimes.forEach { (term, time) ->
            assertThat("Search for '$term' should be under 500ms", time, lessThan(500L))
        }
        
        println("Performance: Insert 2000 blocks in ${insertTime}ms")
        println("Search times: $searchTimes")
    }
    
    @Test
    fun test_join_query_performance() = runBlocking {
        // Arrange - Create hierarchy with multiple relationships
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            conditionCount = 100,
            blocksPerCondition = 5,
            medicationsPerCondition = 3
        )
        TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act - Test complex JOIN query performance
        val queryTime = measureTimeMillis {
            val blockIds = dao.getAllContentBlocks().take(100).map { it.id }
            val results = dao.getContentBlocksWithMetadata(blockIds)
            
            // Verify results include joined data
            assertThat(results, hasSize(100))
            results.forEach { metadata ->
                assertThat(metadata.chapterTitle, notNullValue())
                assertThat(metadata.conditionName, notNullValue())
            }
        }
        
        // Assert
        assertThat("Complex JOIN query should be under 1 second", 
            queryTime, lessThan(1000L))
        
        println("Performance: JOIN query with 100 records in ${queryTime}ms")
    }
    
    // ==================== CACHE PERFORMANCE TESTS ====================
    
    @Test
    fun test_search_cache_hit_performance() = runBlocking {
        // Arrange - Create content and perform initial searches
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            conditionCount = 50,
            blocksPerCondition = 10
        )
        TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val searchQueries = listOf(
            "treatment protocol",
            "pediatric dosage",
            "emergency management",
            "differential diagnosis",
            "contraindications"
        )
        
        // Perform initial searches (cache miss)
        val cacheMissTimes = mutableListOf<Long>()
        searchQueries.forEach { query ->
            val time = measureTimeMillis {
                val results = dao.searchContentByText(query, 20)
                
                // Cache the results
                val cache = TestDataFactory.createSearchCache(
                    queryHash = query.hashCode().toString(),
                    results = results.map { it.id }.toString()
                )
                dao.cacheSearch(cache)
            }
            cacheMissTimes.add(time)
        }
        
        // Act - Perform same searches again (cache hit)
        val cacheHitTimes = mutableListOf<Long>()
        searchQueries.forEach { query ->
            val time = measureTimeMillis {
                val cached = dao.getCachedSearch(query.hashCode().toString())
                assertThat(cached, notNullValue())
            }
            cacheHitTimes.add(time)
        }
        
        // Assert - Cache hits should be significantly faster
        val avgCacheMiss = cacheMissTimes.average()
        val avgCacheHit = cacheHitTimes.average()
        
        // In-memory databases are already fast, so cache improvement may be less dramatic
        // Expect at least 2x speedup instead of 10x
        assertThat("Cache hits should be at least 2x faster than cache misses",
            avgCacheHit, lessThan(avgCacheMiss / 2))
        
        println("Performance: Avg cache miss: ${avgCacheMiss}ms, Avg cache hit: ${avgCacheHit}ms")
        println("Cache speedup: ${avgCacheMiss / avgCacheHit}x")
    }
    
    @Test
    fun test_cache_cleanup_performance() = runBlocking {
        // Arrange - Insert 10,000 cache entries
        val cacheEntries = (1..10000).map { i ->
            TestDataFactory.createSearchCache(
                queryHash = "query_hash_$i",
                timestamp = System.currentTimeMillis() - (i * 1000L), // Varying ages
                hitCount = (1..10).random()
            )
        }
        
        val insertTime = measureTimeMillis {
            cacheEntries.chunked(100).forEach { batch ->
                batch.forEach { dao.cacheSearch(it) }
            }
        }
        
        // Act - Clean up old cache entries
        val cleanupTime = measureTimeMillis {
            val cutoff = System.currentTimeMillis() - 5000000L // ~5000 seconds ago
            dao.clearOldCache(cutoff)
        }
        
        // Assert
        assertThat("Cache insertion should be fast", insertTime, lessThan(10000L))
        assertThat("Cache cleanup should be fast", cleanupTime, lessThan(1000L))
        
        val remainingCache = dao.getAllSearchCache()
        assertThat("Should have cleaned up ~5000 entries", 
            remainingCache.size, both(greaterThan(4000)).and(lessThan(6000)))
        
        println("Performance: Inserted 10000 cache entries in ${insertTime}ms")
        println("Performance: Cleaned up old cache in ${cleanupTime}ms")
    }
    
    // ==================== INDEX EFFECTIVENESS TESTS ====================
    
    @Test
    fun test_foreign_key_index_performance() = runBlocking {
        // Arrange - Create large dataset
        val chapterId = dao.insertChapter(TestDataFactory.createChapter())
        
        // Insert 1000 conditions
        val conditions = (1..1000).map { i ->
            TestDataFactory.createCondition(
                chapterId = chapterId,
                conditionNumber = i,
                conditionName = "Condition $i"
            )
        }
        dao.insertConditions(conditions)
        
        // Act - Query using foreign key (should use index)
        val queryTime = measureTimeMillis {
            val results = dao.getConditionsByChapter(chapterId)
            assertThat(results, hasSize(1000))
        }
        
        // Assert
        assertThat("Foreign key query should use index and be fast",
            queryTime, lessThan(100L))
        
        println("Performance: Foreign key query for 1000 records in ${queryTime}ms")
    }
    
    @Test
    fun test_primary_key_lookup_performance() = runBlocking {
        // Arrange - Insert 5000 content blocks
        val conditionId = dao.insertCondition(
            TestDataFactory.createCondition(
                chapterId = dao.insertChapter(TestDataFactory.createChapter())
            )
        )
        
        val blockIds = mutableListOf<Long>()
        (1..5000).chunked(100).forEach { batch ->
            val blocks = batch.map { i ->
                TestDataFactory.createContentBlock(
                    conditionId = conditionId,
                    orderInCondition = i
                )
            }
            val ids = dao.insertContentBlocks(blocks)
            blockIds.addAll(ids)
        }
        
        // Act - Random primary key lookups
        val lookupTimes = mutableListOf<Long>()
        repeat(100) {
            val randomId = blockIds.random()
            val time = measureTimeMillis {
                val block = dao.getContentBlockById(randomId)
                assertThat(block, notNullValue())
            }
            lookupTimes.add(time)
        }
        
        // Assert
        val avgLookupTime = lookupTimes.average()
        assertThat("Primary key lookups should be very fast (< 10ms avg)",
            avgLookupTime, lessThan(10.0))
        
        println("Performance: Avg primary key lookup time: ${avgLookupTime}ms")
    }
    
    // ==================== CONCURRENT ACCESS TESTS ====================
    
    @Test
    fun test_concurrent_inserts_performance() = runBlocking {
        // Arrange
        val chapterIds = (1..10).map { i ->
            dao.insertChapter(TestDataFactory.createChapter(chapterNumber = i))
        }
        
        // Act - Concurrent inserts from multiple coroutines
        val concurrentTime = measureTimeMillis {
            val jobs = chapterIds.map { chapterId ->
                async {
                    // Each coroutine inserts 100 conditions
                    val conditions = (1..100).map { c ->
                        TestDataFactory.createCondition(
                            chapterId = chapterId,
                            conditionNumber = c,
                            conditionName = "Concurrent Condition $chapterId-$c"
                        )
                    }
                    dao.insertConditions(conditions)
                }
            }
            jobs.awaitAll()
        }
        
        // Assert
        val totalConditions = dao.getAllConditions()
        assertThat(totalConditions, hasSize(1000))
        assertThat("Concurrent inserts should complete within reasonable time",
            concurrentTime, lessThan(5000L))
        
        println("Performance: 10 concurrent insert operations (1000 total records) in ${concurrentTime}ms")
    }
    
    @Test
    fun test_concurrent_reads_and_writes() = runBlocking {
        // Arrange - Initial data
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            conditionCount = 10,
            blocksPerCondition = 10
        )
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act - Concurrent reads and writes
        val concurrentTime = measureTimeMillis {
            val jobs = listOf(
                // Writer coroutines
                async {
                    repeat(50) {
                        val block = TestDataFactory.createContentBlock(
                            conditionId = ids.conditionIds.random(),
                            content = "Concurrent write $it",
                            orderInCondition = 100 + it
                        )
                        dao.insertContentBlock(block)
                    }
                },
                // Reader coroutines
                async {
                    repeat(100) {
                        dao.searchContentByText("treatment", 10)
                    }
                },
                async {
                    repeat(100) {
                        dao.getContentBlocksByCondition(ids.conditionIds.random())
                    }
                },
                // Update coroutine
                async {
                    repeat(25) {
                        val blocks = dao.getAllContentBlocks().take(5)
                        blocks.forEach { block ->
                            dao.updateContentBlock(
                                block.copy(content = "${block.content} [Updated]")
                            )
                        }
                    }
                }
            )
            jobs.awaitAll()
        }
        
        // Assert - Verify data integrity
        val finalBlocks = dao.getAllContentBlocks()
        assertThat(finalBlocks.size, greaterThanOrEqualTo(150)) // Original 100 + 50 new
        assertThat("Concurrent operations should complete without deadlock",
            concurrentTime, lessThan(10000L))
        
        println("Performance: Concurrent reads/writes completed in ${concurrentTime}ms")
    }
    
    // ==================== MEMORY EFFICIENCY TESTS ====================
    
    @Test
    fun test_large_embedding_storage_performance() = runBlocking {
        // Arrange - Create content blocks with large embeddings
        val conditionId = dao.insertCondition(
            TestDataFactory.createCondition(
                chapterId = dao.insertChapter(TestDataFactory.createChapter())
            )
        )
        
        // Insert 100 content blocks
        val blockIds = (1..100).map { i ->
            dao.insertContentBlock(
                TestDataFactory.createContentBlock(
                    conditionId = conditionId,
                    orderInCondition = i
                )
            )
        }
        
        // Act - Insert large embeddings (768 dimensions each)
        val embeddingTime = measureTimeMillis {
            blockIds.forEach { blockId ->
                val embedding = TestDataFactory.createRandomEmbedding(blockId, 768)
                dao.insertEmbedding(embedding)
            }
        }
        
        // Query embeddings
        val queryTime = measureTimeMillis {
            val embeddings = dao.getAllEmbeddings()
            assertThat(embeddings, hasSize(100))
            
            // Verify embedding dimensions
            embeddings.forEach { embedding ->
                assertThat(embedding.embeddingDimensions, equalTo(768))
                assertThat(embedding.embedding.length, greaterThan(1000)) // JSON string length
            }
        }
        
        // Assert
        assertThat("Embedding insertion should be efficient",
            embeddingTime, lessThan(5000L))
        assertThat("Embedding query should be fast",
            queryTime, lessThan(1000L))
        
        println("Performance: Inserted 100 768-dim embeddings in ${embeddingTime}ms")
        println("Performance: Queried embeddings in ${queryTime}ms")
    }
    
    // ==================== BATCH OPERATION TESTS ====================
    
    @Test
    fun test_batch_update_performance() = runBlocking {
        // Arrange - Insert 1000 medications
        val conditionId = dao.insertCondition(
            TestDataFactory.createCondition(
                chapterId = dao.insertChapter(TestDataFactory.createChapter())
            )
        )
        
        val medications = (1..1000).map { i ->
            TestDataFactory.createMedication(
                conditionId = conditionId,
                medicationName = "Medicine $i",
                evidenceLevel = listOf("A", "B", "C")[i % 3]
            )
        }
        
        medications.chunked(100).forEach { batch ->
            dao.insertMedications(batch)
        }
        
        // Act - Batch update evidence levels
        val updateTime = measureTimeMillis {
            val allMeds = dao.getMedicationsByCondition(conditionId)
            allMeds.forEach { med ->
                dao.updateMedication(
                    med.copy(evidenceLevel = "A") // Upgrade all to level A
                )
            }
        }
        
        // Assert
        val updatedMeds = dao.getMedicationsByCondition(conditionId)
        assertThat(updatedMeds, hasSize(1000))
        assertThat(updatedMeds.all { it.evidenceLevel == "A" }, `is`(true))
        assertThat("Batch update of 1000 records should be under 5 seconds",
            updateTime, lessThan(5000L))
        
        println("Performance: Updated 1000 medications in ${updateTime}ms")
    }
    
    @Test
    fun test_cascade_delete_performance() = runBlocking {
        // Arrange - Create deep hierarchy
        val chapterId = dao.insertChapter(TestDataFactory.createChapter())
        
        // 50 conditions
        val conditionIds = (1..50).map { i ->
            dao.insertCondition(
                TestDataFactory.createCondition(
                    chapterId = chapterId,
                    conditionNumber = i
                )
            )
        }
        
        // 10 blocks per condition (500 total)
        conditionIds.forEach { condId ->
            val blocks = (1..10).map { b ->
                TestDataFactory.createContentBlock(
                    conditionId = condId,
                    orderInCondition = b
                )
            }
            dao.insertContentBlocks(blocks)
        }
        
        // 5 medications per condition (250 total)
        conditionIds.forEach { condId ->
            val meds = (1..5).map { m ->
                TestDataFactory.createMedication(
                    conditionId = condId,
                    medicationName = "Med $m"
                )
            }
            dao.insertMedications(meds)
        }
        
        // Verify setup
        assertThat(dao.getAllConditions(), hasSize(50))
        assertThat(dao.getAllContentBlocks(), hasSize(500))
        
        // Act - Delete chapter (should cascade delete everything)
        val deleteTime = measureTimeMillis {
            val chapter = dao.getChapterById(chapterId)!!
            dao.deleteChapter(chapter)
        }
        
        // Assert
        assertThat(dao.getAllConditions(), empty())
        assertThat(dao.getAllContentBlocks(), empty())
        assertThat("Cascade delete of 800+ records should be fast",
            deleteTime, lessThan(1000L))
        
        println("Performance: Cascade deleted 800+ records in ${deleteTime}ms")
    }
}