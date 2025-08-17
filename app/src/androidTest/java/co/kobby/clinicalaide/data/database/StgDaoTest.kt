package co.kobby.clinicalaide.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.database.dao.StgDao
import co.kobby.clinicalaide.data.database.entities.*
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive DAO tests for the STG database.
 * These tests verify all CRUD operations, queries, and relationships.
 * 
 * Test Categories:
 * 1. Basic CRUD Operations - Insert, retrieve, update, delete
 * 2. Query Operations - Search, filter, order
 * 3. Foreign Key Constraints - CASCADE delete behavior
 * 4. Default Values - Verify default values are applied
 * 5. Complex Queries - Joins and aggregations
 */
@RunWith(AndroidJUnit4::class)
class StgDaoTest {
    
    private lateinit var database: StgDatabase
    private lateinit var dao: StgDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StgDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.stgDao()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    // ==================== CHAPTER OPERATIONS ====================
    
    @Test
    fun test_insert_and_retrieve_single_chapter() = runBlocking {
        // Arrange
        val chapter = StgChapter(
            chapterNumber = 1,
            chapterTitle = "Gastrointestinal Disorders",
            startPage = 29,
            endPage = 57,
            description = "Disorders of the GI tract"
        )
        
        // Act
        val id = dao.insertChapter(chapter)
        val retrieved = dao.getChapterById(id)
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.chapterTitle, equalTo(chapter.chapterTitle))
        assertThat(retrieved?.chapterNumber, equalTo(chapter.chapterNumber))
        assertThat(retrieved?.startPage, equalTo(chapter.startPage))
        assertThat(retrieved?.endPage, equalTo(chapter.endPage))
        assertThat(retrieved?.description, equalTo(chapter.description))
    }
    
    @Test
    fun test_insert_multiple_chapters_and_retrieve_ordered() = runBlocking {
        // Arrange
        val chapters = listOf(
            StgChapter(chapterNumber = 3, chapterTitle = "Chapter 3", startPage = 100, endPage = 150),
            StgChapter(chapterNumber = 1, chapterTitle = "Chapter 1", startPage = 1, endPage = 50),
            StgChapter(chapterNumber = 2, chapterTitle = "Chapter 2", startPage = 51, endPage = 99)
        )
        
        // Act
        dao.insertChapters(chapters)
        val retrieved = dao.getAllChapters()
        
        // Assert
        assertThat(retrieved, hasSize(3))
        assertThat(retrieved[0].chapterNumber, equalTo(1))
        assertThat(retrieved[1].chapterNumber, equalTo(2))
        assertThat(retrieved[2].chapterNumber, equalTo(3))
    }
    
    @Test
    fun test_chapter_with_null_description() = runBlocking {
        // Arrange
        val chapter = StgChapter(
            chapterNumber = 1,
            chapterTitle = "Test Chapter",
            startPage = 1,
            endPage = 10,
            description = null
        )
        
        // Act
        val id = dao.insertChapter(chapter)
        val retrieved = dao.getChapterById(id)
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.description, nullValue())
    }
    
    // ==================== CONDITION OPERATIONS ====================
    
    @Test
    fun test_insert_and_retrieve_conditions_by_chapter() = runBlocking {
        // Arrange
        val chapter = StgChapter(
            chapterNumber = 1,
            chapterTitle = "GI Disorders",
            startPage = 1,
            endPage = 100
        )
        val chapterId = dao.insertChapter(chapter)
        
        val conditions = listOf(
            StgCondition(
                chapterId = chapterId,
                conditionNumber = 1,
                conditionName = "Diarrhoea",
                startPage = 29,
                endPage = 32,
                keywords = "[\"diarrhoea\", \"gastroenteritis\"]"
            ),
            StgCondition(
                chapterId = chapterId,
                conditionNumber = 2,
                conditionName = "Constipation",
                startPage = 33,
                endPage = 35,
                keywords = "[\"constipation\", \"bowel\"]"
            )
        )
        
        // Act
        dao.insertConditions(conditions)
        val retrieved = dao.getConditionsByChapter(chapterId)
        
        // Assert
        assertThat(retrieved, hasSize(2))
        assertThat(retrieved[0].conditionName, equalTo("Diarrhoea"))
        assertThat(retrieved[1].conditionName, equalTo("Constipation"))
    }
    
    @Test
    fun test_search_conditions_by_name() = runBlocking {
        // Arrange
        val chapter = StgChapter(
            chapterNumber = 1,
            chapterTitle = "Test",
            startPage = 1,
            endPage = 100
        )
        val chapterId = dao.insertChapter(chapter)
        
        val conditions = listOf(
            StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Malaria", startPage = 1, endPage = 5, keywords = "[]"),
            StgCondition(chapterId = chapterId, conditionNumber = 2, conditionName = "Typhoid", startPage = 6, endPage = 10, keywords = "[]"),
            StgCondition(chapterId = chapterId, conditionNumber = 3, conditionName = "Cerebral Malaria", startPage = 11, endPage = 15, keywords = "[]")
        )
        dao.insertConditions(conditions)
        
        // Act
        val results = dao.searchConditions("Malaria")
        
        // Assert
        assertThat(results, hasSize(2))
        assertThat(results.map { it.conditionName }, hasItems("Malaria", "Cerebral Malaria"))
    }
    
    // ==================== CONTENT BLOCK OPERATIONS ====================
    
    @Test
    fun test_insert_content_block_with_default_values() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val contentBlock = StgContentBlock(
            conditionId = conditionId,
            blockType = "treatment",
            content = "Treatment details",
            pageNumber = 5,
            orderInCondition = 1,
            keywords = "[\"treatment\"]"
        )
        
        // Act
        val id = dao.insertContentBlock(contentBlock)
        val retrieved = dao.getContentBlockById(id)
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.clinicalContext, equalTo("general"))
        assertThat(retrieved?.relatedBlockIds, equalTo("[]"))
        assertThat(retrieved?.createdAt, greaterThan(0L))
        assertThat(retrieved?.updatedAt, greaterThan(0L))
    }
    
    @Test
    fun test_get_content_blocks_by_type() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val blocks = listOf(
            StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Treatment 1", pageNumber = 1, orderInCondition = 1, keywords = "[]"),
            StgContentBlock(conditionId = conditionId, blockType = "diagnosis", content = "Diagnosis", pageNumber = 2, orderInCondition = 2, keywords = "[]"),
            StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Treatment 2", pageNumber = 3, orderInCondition = 3, keywords = "[]")
        )
        dao.insertContentBlocks(blocks)
        
        // Act
        val treatments = dao.getContentBlocksByType(conditionId, "treatment")
        
        // Assert
        assertThat(treatments, hasSize(2))
        assertThat(treatments[0].content, equalTo("Treatment 1"))
        assertThat(treatments[1].content, equalTo("Treatment 2"))
    }
    
    @Test
    fun test_get_content_blocks_by_context() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val blocks = listOf(
            StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Adult treatment", pageNumber = 1, orderInCondition = 1, clinicalContext = "adult", keywords = "[]"),
            StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "General treatment", pageNumber = 2, orderInCondition = 2, clinicalContext = "general", keywords = "[]"),
            StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Pediatric treatment", pageNumber = 3, orderInCondition = 3, clinicalContext = "pediatric", keywords = "[]")
        )
        dao.insertContentBlocks(blocks)
        
        // Act
        val adultBlocks = dao.getContentBlocksByContext("adult")
        
        // Assert
        assertThat(adultBlocks, hasSize(2)) // Should include "adult" and "general"
        assertThat(adultBlocks.map { it.content }, hasItems("Adult treatment", "General treatment"))
    }
    
    // ==================== EMBEDDING OPERATIONS ====================
    
    @Test
    fun test_insert_embedding_with_default_dimensions() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        val contentBlock = StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Test", pageNumber = 1, orderInCondition = 1, keywords = "[]")
        val blockId = dao.insertContentBlock(contentBlock)
        
        val embedding = StgEmbedding(
            contentBlockId = blockId,
            embedding = "[0.1, 0.2, 0.3]",
            embeddingModel = "text-embedding-004"
        )
        
        // Act
        val id = dao.insertEmbedding(embedding)
        val retrieved = dao.getEmbeddingById(id)
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.embeddingDimensions, equalTo(768))
        assertThat(retrieved?.createdAt, greaterThan(0L))
    }
    
    // ==================== MEDICATION OPERATIONS ====================
    
    @Test
    fun test_get_medications_by_condition_and_age() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Malaria", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val medications = listOf(
            StgMedication(
                conditionId = conditionId,
                medicationName = "Paracetamol",
                dosage = "500mg",
                frequency = "QID",
                duration = "3 days",
                route = "oral",
                ageGroup = "adult",
                pageNumber = 5
            ),
            StgMedication(
                conditionId = conditionId,
                medicationName = "Paracetamol Syrup",
                dosage = "250mg",
                frequency = "QID",
                duration = "3 days",
                route = "oral",
                ageGroup = "pediatric",
                pageNumber = 6
            )
        )
        dao.insertMedications(medications)
        
        // Act
        val adultMeds = dao.getMedicationsByConditionAndAge(conditionId, "adult")
        
        // Assert
        assertThat(adultMeds, hasSize(1))
        assertThat(adultMeds[0].medicationName, equalTo("Paracetamol"))
        assertThat(adultMeds[0].dosage, equalTo("500mg"))
    }
    
    @Test
    fun test_medication_weight_based_default() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val medication = StgMedication(
            conditionId = conditionId,
            medicationName = "Test Drug",
            dosage = "10mg",
            frequency = "BD",
            duration = "5 days",
            route = "oral",
            ageGroup = "adult",
            pageNumber = 5
        )
        
        // Act
        val id = dao.insertMedication(medication)
        val medications = dao.getMedicationsByCondition(conditionId)
        
        // Assert
        assertThat(medications, hasSize(1))
        assertThat(medications[0].weightBased, equalTo(false))
    }
    
    // ==================== FOREIGN KEY CASCADE DELETE TESTS ====================
    
    @Test
    fun test_cascade_delete_chapter_removes_conditions() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        
        val condition = StgCondition(
            chapterId = chapterId,
            conditionNumber = 1,
            conditionName = "Test Condition",
            startPage = 1,
            endPage = 10,
            keywords = "[]"
        )
        dao.insertCondition(condition)
        
        // Act
        val chapterToDelete = dao.getChapterById(chapterId)!!
        dao.deleteChapter(chapterToDelete)
        val conditions = dao.getConditionsByChapter(chapterId)
        
        // Assert
        assertThat(conditions, empty())
    }
    
    @Test
    fun test_cascade_delete_propagates_through_hierarchy() = runBlocking {
        // Arrange - Create full hierarchy
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val contentBlock = StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Test", pageNumber = 1, orderInCondition = 1, keywords = "[]")
        val blockId = dao.insertContentBlock(contentBlock)
        
        val embedding = StgEmbedding(contentBlockId = blockId, embedding = "[]", embeddingModel = "test")
        dao.insertEmbedding(embedding)
        
        val medication = StgMedication(conditionId = conditionId, medicationName = "Test", dosage = "10mg", frequency = "BD", duration = "5 days", route = "oral", ageGroup = "adult", pageNumber = 5)
        dao.insertMedication(medication)
        
        // Act - Delete the chapter
        val chapterToDelete = dao.getChapterById(chapterId)!!
        dao.deleteChapter(chapterToDelete)
        
        // Assert - Everything should be deleted
        assertThat(dao.getConditionsByChapter(chapterId), empty())
        assertThat(dao.getContentBlocksByCondition(conditionId), empty())
        assertThat(dao.getEmbeddingByContentBlock(blockId), nullValue())
        assertThat(dao.getMedicationsByCondition(conditionId), empty())
    }
    
    // ==================== SEARCH CACHE OPERATIONS ====================
    
    @Test
    fun test_search_cache_with_primary_key() = runBlocking {
        // Arrange
        val cache = StgSearchCache(
            queryHash = "hash123",
            results = "[{\"id\": 1}]",
            timestamp = System.currentTimeMillis(),
            hitCount = 1
        )
        
        // Act
        dao.cacheSearch(cache)
        val retrieved = dao.getCachedSearch("hash123")
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.results, equalTo("[{\"id\": 1}]"))
        assertThat(retrieved?.hitCount, equalTo(1))
    }
    
    @Test
    fun test_clear_old_cache() = runBlocking {
        // Arrange
        val oldTimestamp = System.currentTimeMillis() - 1000000
        val newTimestamp = System.currentTimeMillis()
        
        dao.cacheSearch(StgSearchCache("old", "[]", oldTimestamp, 1))
        dao.cacheSearch(StgSearchCache("new", "[]", newTimestamp, 1))
        
        // Act
        dao.clearOldCache(System.currentTimeMillis() - 500000)
        
        // Assert
        assertThat(dao.getCachedSearch("old"), nullValue())
        assertThat(dao.getCachedSearch("new"), notNullValue())
    }
    
    // ==================== COMPLEX QUERY TESTS ====================
    
    @Test
    fun test_content_blocks_with_metadata_join() = runBlocking {
        // Arrange - Create hierarchy
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "GI Disorders", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Diarrhoea", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val block1 = StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Treatment 1", pageNumber = 1, orderInCondition = 1, keywords = "[]")
        val block2 = StgContentBlock(conditionId = conditionId, blockType = "diagnosis", content = "Diagnosis", pageNumber = 2, orderInCondition = 2, keywords = "[]")
        
        val id1 = dao.insertContentBlock(block1)
        val id2 = dao.insertContentBlock(block2)
        
        // Act
        val results = dao.getContentBlocksWithMetadata(listOf(id1, id2))
        
        // Assert
        assertThat(results, hasSize(2))
        assertThat(results[0].conditionName, equalTo("Diarrhoea"))
        assertThat(results[0].chapterTitle, equalTo("GI Disorders"))
        assertThat(results[0].contentBlock.content, equalTo("Treatment 1"))
    }
    
    @Test
    fun test_full_text_search_with_ranking() = runBlocking {
        // Arrange
        val chapter = StgChapter(chapterNumber = 1, chapterTitle = "Test", startPage = 1, endPage = 100)
        val chapterId = dao.insertChapter(chapter)
        val condition = StgCondition(chapterId = chapterId, conditionNumber = 1, conditionName = "Test", startPage = 1, endPage = 10, keywords = "[]")
        val conditionId = dao.insertCondition(condition)
        
        val blocks = listOf(
            StgContentBlock(conditionId = conditionId, blockType = "treatment", content = "Malaria treatment", pageNumber = 1, orderInCondition = 1, keywords = "[\"malaria\"]"),
            StgContentBlock(conditionId = conditionId, blockType = "diagnosis", content = "Diagnosis of severe malaria", pageNumber = 2, orderInCondition = 2, keywords = "[\"severe\"]"),
            StgContentBlock(conditionId = conditionId, blockType = "symptoms", content = "Symptoms include fever", pageNumber = 3, orderInCondition = 3, keywords = "[\"fever\"]")
        )
        dao.insertContentBlocks(blocks)
        
        // Act
        val results = dao.searchContentByText("malaria", 10)
        
        // Assert
        assertThat(results, hasSize(2))
        assertThat(results[0].content, startsWith("Malaria")) // Should rank first (starts with search term)
    }
    
    // ==================== CROSS-REFERENCE OPERATIONS ====================
    
    @Test
    fun test_cross_references() = runBlocking {
        // Arrange
        val crossRef = StgCrossReference(
            fromConditionId = 1,
            toConditionId = 2,
            referenceType = "see_also",
            description = "Related condition"
        )
        
        // Act
        val id = dao.insertCrossReference(crossRef)
        val refs = dao.getCrossReferencesByCondition(1)
        
        // Assert
        assertThat(refs, hasSize(1))
        assertThat(refs[0].toConditionId, equalTo(2L))
        assertThat(refs[0].referenceType, equalTo("see_also"))
    }
}