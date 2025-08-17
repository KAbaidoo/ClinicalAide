package co.kobby.clinicalaide.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.TestDataFactory
import co.kobby.clinicalaide.data.TestDatabaseHelper
import co.kobby.clinicalaide.data.database.dao.StgDao
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * Integration tests for end-to-end workflows in the STG database.
 * Tests complete scenarios that span multiple entities and operations.
 */
@RunWith(AndroidJUnit4::class)
class StgDatabaseIntegrationTest {
    
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
    
    // ==================== COMPLETE HIERARCHY TESTS ====================
    
    @Test
    fun test_create_and_navigate_complete_hierarchy() = runBlocking {
        // Arrange - Create a complete medical data hierarchy
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            chapterNumber = 1,
            conditionCount = 3,
            blocksPerCondition = 5,
            medicationsPerCondition = 3,
            includeEmbeddings = true
        )
        
        // Act - Insert the entire hierarchy
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Assert - Verify the hierarchy is correctly stored and can be navigated
        
        // 1. Chapter level
        val chapters = dao.getAllChapters()
        assertThat(chapters, hasSize(1))
        assertThat(chapters[0].chapterNumber, equalTo(1))
        
        // 2. Condition level
        val conditions = dao.getConditionsByChapter(ids.chapterId)
        assertThat(conditions, hasSize(3))
        conditions.forEach { condition ->
            assertThat(condition.chapterId, equalTo(ids.chapterId))
        }
        
        // 3. Content block level
        conditions.forEach { condition ->
            val blocks = dao.getContentBlocksByCondition(condition.id)
            assertThat("Condition ${condition.id} should have 5 blocks", 
                blocks, hasSize(5))
            
            // Verify blocks are ordered correctly
            val orderNumbers = blocks.map { it.orderInCondition }
            assertThat(orderNumbers, equalTo(listOf(1, 2, 3, 4, 5)))
        }
        
        // 4. Embedding level
        val allEmbeddings = dao.getAllEmbeddings()
        assertThat(allEmbeddings, hasSize(15)) // 3 conditions × 5 blocks
        
        // 5. Medication level
        conditions.forEach { condition ->
            val medications = dao.getMedicationsByCondition(condition.id)
            assertThat("Condition ${condition.id} should have 3 medications",
                medications, hasSize(3))
        }
    }
    
    // ==================== SEARCH WORKFLOW TESTS ====================
    
    @Test
    fun test_search_workflow_with_caching() = runBlocking {
        // Arrange - Set up test data
        val hierarchy = TestDataFactory.createCompleteHierarchy()
        TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val searchTerm = "treatment"
        val queryHash = generateHash(searchTerm)
        
        // Act 1 - First search (cache miss)
        val searchResults = dao.searchContentByText(searchTerm, 10)
        
        // Cache the results
        val cache = TestDataFactory.createSearchCache(
            queryHash = queryHash,
            results = searchResults.map { it.id }.toString(),
            hitCount = 1
        )
        dao.cacheSearch(cache)
        
        // Act 2 - Second search (cache hit)
        val cachedResult = dao.getCachedSearch(queryHash)
        
        // Act 3 - Update cache hit count
        if (cachedResult != null) {
            dao.cacheSearch(cachedResult.copy(hitCount = cachedResult.hitCount + 1))
        }
        
        // Assert
        assertThat(searchResults, not(empty()))
        assertThat(cachedResult, notNullValue())
        assertThat(cachedResult?.queryHash, equalTo(queryHash))
        
        val updatedCache = dao.getCachedSearch(queryHash)
        assertThat(updatedCache?.hitCount, equalTo(2))
    }
    
    @Test
    fun test_cache_cleanup_workflow() = runBlocking {
        // Arrange - Create multiple cache entries with different timestamps
        val oldTimestamp = System.currentTimeMillis() - 86400000 // 24 hours ago
        val recentTimestamp = System.currentTimeMillis() - 3600000 // 1 hour ago
        val currentTimestamp = System.currentTimeMillis()
        
        dao.cacheSearch(TestDataFactory.createSearchCache(
            queryHash = "old_search",
            timestamp = oldTimestamp
        ))
        dao.cacheSearch(TestDataFactory.createSearchCache(
            queryHash = "recent_search",
            timestamp = recentTimestamp
        ))
        dao.cacheSearch(TestDataFactory.createSearchCache(
            queryHash = "current_search",
            timestamp = currentTimestamp
        ))
        
        // Act - Clean up old cache (older than 2 hours)
        val cutoffTime = System.currentTimeMillis() - 7200000 // 2 hours ago
        dao.clearOldCache(cutoffTime)
        
        // Assert
        assertThat(dao.getCachedSearch("old_search"), nullValue())
        assertThat(dao.getCachedSearch("recent_search"), notNullValue())
        assertThat(dao.getCachedSearch("current_search"), notNullValue())
    }
    
    // ==================== CLINICAL CONTEXT WORKFLOW ====================
    
    @Test
    fun test_clinical_context_filtering_workflow() = runBlocking {
        // Arrange - Create content blocks with different clinical contexts
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val contexts = listOf("general", "pediatric", "adult", "pregnancy", "elderly")
        contexts.forEachIndexed { index, context ->
            val block = TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                clinicalContext = context,
                content = "Treatment for $context patients",
                orderInCondition = index + 1
            )
            dao.insertContentBlock(block)
        }
        
        // Act - Query for pediatric context (should get pediatric + general)
        val pediatricBlocks = dao.getContentBlocksByContext("pediatric")
        
        // Assert
        assertThat(pediatricBlocks, hasSize(2))
        val contextList = pediatricBlocks.map { it.clinicalContext }
        assertThat(contextList, containsInAnyOrder("general", "pediatric"))
    }
    
    // ==================== MEDICATION LOOKUP WORKFLOW ====================
    
    @Test
    fun test_age_based_medication_lookup_workflow() = runBlocking {
        // Arrange - Create medications for different age groups
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 2, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Add medications for different age groups to first condition
        val ageGroups = listOf(
            Triple("Paracetamol", "500mg", "adult"),
            Triple("Paracetamol Syrup", "250mg/5ml", "pediatric"),
            Triple("Paracetamol Drops", "100mg/ml", "neonatal")
        )
        
        ageGroups.forEach { (name, dose, age) ->
            dao.insertMedication(TestDataFactory.createMedication(
                conditionId = ids.conditionIds[0],
                medicationName = name,
                dosage = dose,
                ageGroup = age
            ))
        }
        
        // Act - Look up medications for pediatric patients
        val pediatricMeds = dao.getMedicationsByConditionAndAge(ids.conditionIds[0], "pediatric")
        
        // Assert
        assertThat(pediatricMeds, hasSize(1))
        assertThat(pediatricMeds[0].medicationName, equalTo("Paracetamol Syrup"))
        assertThat(pediatricMeds[0].dosage, equalTo("250mg/5ml"))
    }
    
    // ==================== CROSS-REFERENCE WORKFLOW ====================
    
    @Test
    fun test_cross_reference_navigation_workflow() = runBlocking {
        // Arrange - Create conditions with cross-references
        val chapter = TestDataFactory.createChapter()
        val chapterId = dao.insertChapter(chapter)
        
        // Create related conditions
        val malaria = TestDataFactory.createCondition(
            chapterId = chapterId,
            conditionName = "Malaria",
            conditionNumber = 1
        )
        val typhoid = TestDataFactory.createCondition(
            chapterId = chapterId,
            conditionName = "Typhoid Fever",
            conditionNumber = 2
        )
        val dengue = TestDataFactory.createCondition(
            chapterId = chapterId,
            conditionName = "Dengue Fever",
            conditionNumber = 3
        )
        
        val malariaId = dao.insertCondition(malaria)
        val typhoidId = dao.insertCondition(typhoid)
        val dengueId = dao.insertCondition(dengue)
        
        // Create cross-references (differential diagnosis)
        dao.insertCrossReference(TestDataFactory.createCrossReference(
            fromConditionId = malariaId,
            toConditionId = typhoidId,
            referenceType = "differential",
            description = "Consider typhoid in prolonged fever"
        ))
        dao.insertCrossReference(TestDataFactory.createCrossReference(
            fromConditionId = malariaId,
            toConditionId = dengueId,
            referenceType = "differential",
            description = "Consider dengue in urban areas"
        ))
        
        // Act - Navigate cross-references
        val malariaRefs = dao.getCrossReferencesByCondition(malariaId)
        val relatedConditionIds = malariaRefs.map { it.toConditionId }
        
        // Get the actual related conditions
        val relatedConditions = relatedConditionIds.mapNotNull { id ->
            dao.getConditionById(id)
        }
        
        // Assert
        assertThat(malariaRefs, hasSize(2))
        assertThat(malariaRefs.map { it.referenceType }, everyItem(equalTo("differential")))
        assertThat(relatedConditions.map { it.conditionName }, 
            containsInAnyOrder("Typhoid Fever", "Dengue Fever"))
    }
    
    // ==================== CONTENT BLOCK METADATA WORKFLOW ====================
    
    @Test
    fun test_content_blocks_with_metadata_workflow() = runBlocking {
        // Arrange - Create hierarchy
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            conditionCount = 2,
            blocksPerCondition = 3
        )
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act - Get content blocks with metadata
        val selectedBlockIds = ids.blockIds.take(4)
        val blocksWithMetadata = dao.getContentBlocksWithMetadata(selectedBlockIds)
        
        // Assert
        assertThat(blocksWithMetadata, hasSize(4))
        blocksWithMetadata.forEach { blockMeta ->
            assertThat(blockMeta.chapterTitle, notNullValue())
            assertThat(blockMeta.conditionName, notNullValue())
            assertThat(blockMeta.contentBlock, notNullValue())
        }
        
        // The ordering should be by conditionId first, then orderInCondition
        // Since we selected the first 4 blocks from 2 conditions with 3 blocks each,
        // we should have blocks with orderInCondition: 1,2,3 from condition 1 and 1 from condition 2
        val orders = blocksWithMetadata.map { it.contentBlock.orderInCondition }
        assertThat(orders, equalTo(listOf(1, 2, 3, 1)))
    }
    
    // ==================== PERFORMANCE TEST ====================
    
    @Test
    fun test_batch_operations_performance() = runBlocking {
        // Arrange - Create large dataset
        val chapters = (1..10).map { i ->
            TestDataFactory.createChapter(chapterNumber = i, chapterTitle = "Chapter $i")
        }
        
        // Act - Measure batch insert time
        val (_, insertTime) = TestDatabaseHelper.measureTime {
            dao.insertChapters(chapters)
        }
        
        // Act - Measure query time
        val (queriedChapters, queryTime) = TestDatabaseHelper.measureTime {
            dao.getAllChapters()
        }
        
        // Assert
        assertThat(queriedChapters, hasSize(10))
        assertThat("Batch insert should be fast", insertTime, lessThan(1000L)) // Less than 1 second
        assertThat("Query should be fast", queryTime, lessThan(100L)) // Less than 100ms
    }
    
    // ==================== TRANSACTION WORKFLOW ====================
    
    @Test
    fun test_cascade_delete_transaction_workflow() = runBlocking {
        // Arrange - Create complete hierarchy
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            conditionCount = 2,
            blocksPerCondition = 3,
            medicationsPerCondition = 2,
            includeEmbeddings = true
        )
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Verify everything is inserted
        assertThat(dao.getAllChapters(), hasSize(1))
        assertThat(dao.getConditionsByChapter(ids.chapterId), hasSize(2))
        assertThat(dao.getAllContentBlocks(), hasSize(6))
        assertThat(dao.getAllEmbeddings(), hasSize(6))
        
        // Act - Delete the chapter (should cascade delete everything)
        val chapter = dao.getChapterById(ids.chapterId)!!
        dao.deleteChapter(chapter)
        
        // Assert - Everything should be deleted
        assertThat(dao.getAllChapters(), empty())
        assertThat(dao.getConditionsByChapter(ids.chapterId), empty())
        assertThat(dao.getAllContentBlocks(), empty())
        assertThat(dao.getAllEmbeddings(), empty())
        
        // Verify database is truly empty
        assertThat(TestDatabaseHelper.isDatabaseEmpty(dao), `is`(true))
    }
    
    // ==================== SEARCH RANKING WORKFLOW ====================
    
    @Test
    fun test_search_ranking_workflow() = runBlocking {
        // Arrange - Create content with different relevance
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val blocks = listOf(
            TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                content = "Malaria is a parasitic disease", // Starts with search term
                orderInCondition = 1
            ),
            TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                content = "Treatment for severe malaria includes artemisinin", // Contains search term
                orderInCondition = 2
            ),
            TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                content = "Fever and chills are common in malaria", // Ends with search term
                orderInCondition = 3
            )
        )
        
        blocks.forEach { dao.insertContentBlock(it) }
        
        // Act - Search for "malaria"
        val results = dao.searchContentByText("Malaria", 10)
        
        // Assert - Results should be ranked by relevance
        assertThat(results, hasSize(3))
        assertThat(results[0].content, startsWith("Malaria")) // Best match first
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun generateHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}