package co.kobby.clinicalaide.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.TestDataFactory
import co.kobby.clinicalaide.data.TestDatabaseHelper
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
 * Data integrity tests for the STG database.
 * Validates JSON handling, enum values, and special character support.
 */
@RunWith(AndroidJUnit4::class)
class StgDataIntegrityTest {
    
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
    
    // ==================== JSON FIELD TESTS ====================
    
    @Test
    fun test_store_and_retrieve_json_array_in_keywords() = runBlocking {
        // Arrange
        val chapter = TestDataFactory.createChapter()
        val chapterId = dao.insertChapter(chapter)
        
        val jsonKeywords = """["malaria", "fever", "parasitic", "Plasmodium falciparum"]"""
        val condition = TestDataFactory.createCondition(
            chapterId = chapterId,
            keywords = jsonKeywords
        )
        
        // Act
        val conditionId = dao.insertCondition(condition)
        val retrieved = dao.getConditionById(conditionId)
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.keywords, equalTo(jsonKeywords))
        assertThat(TestDatabaseHelper.isValidJson(retrieved?.keywords ?: ""), `is`(true))
    }
    
    @Test
    fun test_store_and_retrieve_json_related_block_ids() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 1)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val relatedIds = """[1, 2, 3, 10, 15]"""
        val contentBlock = TestDataFactory.createContentBlock(
            conditionId = ids.conditionIds[0],
            relatedBlockIds = relatedIds
        )
        
        // Act
        val blockId = dao.insertContentBlock(contentBlock)
        val retrieved = dao.getContentBlockById(blockId)
        
        // Assert
        assertThat(retrieved?.relatedBlockIds, equalTo(relatedIds))
        assertThat(TestDatabaseHelper.isValidJson(retrieved?.relatedBlockIds ?: ""), `is`(true))
    }
    
    @Test
    fun test_store_and_retrieve_large_embedding_vector() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 1)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Create a large embedding vector (768 dimensions)
        val embedding = TestDataFactory.createRandomEmbedding(ids.blockIds[0], 768)
        
        // Act
        val embeddingId = dao.insertEmbedding(embedding)
        val retrieved = dao.getEmbeddingById(embeddingId)
        
        // Assert
        assertThat(retrieved, notNullValue())
        assertThat(retrieved?.embedding?.startsWith("["), `is`(true))
        assertThat(retrieved?.embedding?.endsWith("]"), `is`(true))
        assertThat(retrieved?.embeddingDimensions, equalTo(768))
    }
    
    @Test
    fun test_empty_json_arrays() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val contentBlock = TestDataFactory.createContentBlock(
            conditionId = ids.conditionIds[0],
            keywords = "[]",
            relatedBlockIds = "[]"
        )
        
        // Act
        val blockId = dao.insertContentBlock(contentBlock)
        val retrieved = dao.getContentBlockById(blockId)
        
        // Assert
        assertThat(retrieved?.keywords, equalTo("[]"))
        assertThat(retrieved?.relatedBlockIds, equalTo("[]"))
    }
    
    // ==================== ENUM VALIDATION TESTS ====================
    
    @Test
    fun test_valid_block_types() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act & Assert - Test all valid block types
        TestDataFactory.ValidValues.blockTypes.forEach { blockType ->
            val block = TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                blockType = blockType,
                orderInCondition = TestDataFactory.ValidValues.blockTypes.indexOf(blockType) + 1
            )
            val blockId = dao.insertContentBlock(block)
            val retrieved = dao.getContentBlockById(blockId)
            
            assertThat("BlockType $blockType should be stored correctly", 
                retrieved?.blockType, equalTo(blockType))
        }
    }
    
    @Test
    fun test_valid_clinical_contexts() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act & Assert - Test all valid clinical contexts
        TestDataFactory.ValidValues.clinicalContexts.forEach { context ->
            val block = TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                clinicalContext = context,
                orderInCondition = TestDataFactory.ValidValues.clinicalContexts.indexOf(context) + 1
            )
            val blockId = dao.insertContentBlock(block)
            val retrieved = dao.getContentBlockById(blockId)
            
            assertThat("Clinical context $context should be stored correctly",
                retrieved?.clinicalContext, equalTo(context))
        }
    }
    
    @Test
    fun test_valid_severity_levels() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act & Assert - Test all valid severity levels (including null)
        val severityOptions = TestDataFactory.ValidValues.severityLevels + listOf(null)
        severityOptions.forEachIndexed { index, severity ->
            val block = TestDataFactory.createContentBlock(
                conditionId = ids.conditionIds[0],
                severityLevel = severity,
                orderInCondition = index + 1
            )
            val blockId = dao.insertContentBlock(block)
            val retrieved = dao.getContentBlockById(blockId)
            
            assertThat("Severity level $severity should be stored correctly",
                retrieved?.severityLevel, equalTo(severity))
        }
    }
    
    @Test
    fun test_valid_evidence_levels() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act & Assert - Test all valid evidence levels
        TestDataFactory.ValidValues.evidenceLevels.forEach { evidence ->
            val medication = TestDataFactory.createMedication(
                conditionId = ids.conditionIds[0],
                evidenceLevel = evidence
            )
            val medId = dao.insertMedication(medication)
            val meds = dao.getMedicationsByCondition(ids.conditionIds[0])
            val retrieved = meds.find { it.id == medId }
            
            assertThat("Evidence level $evidence should be stored correctly",
                retrieved?.evidenceLevel, equalTo(evidence))
        }
    }
    
    @Test
    fun test_valid_medication_routes() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(
            conditionCount = 1, 
            blocksPerCondition = 0,
            medicationsPerCondition = 0 // Don't auto-create medications
        )
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act & Assert - Test all valid routes
        TestDataFactory.ValidValues.routes.forEach { route ->
            val medication = TestDataFactory.createMedication(
                conditionId = ids.conditionIds[0],
                route = route,
                medicationName = "Test Med $route"
            )
            dao.insertMedication(medication)
        }
        
        val medications = dao.getMedicationsByCondition(ids.conditionIds[0])
        assertThat(medications, hasSize(TestDataFactory.ValidValues.routes.size))
        
        TestDataFactory.ValidValues.routes.forEach { route ->
            assertThat(medications.map { it.route }, hasItem(route))
        }
    }
    
    @Test
    fun test_valid_age_groups() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        // Act & Assert - Test all valid age groups
        TestDataFactory.ValidValues.ageGroups.forEach { ageGroup ->
            val medication = TestDataFactory.createMedication(
                conditionId = ids.conditionIds[0],
                ageGroup = ageGroup,
                medicationName = "Test Med $ageGroup"
            )
            dao.insertMedication(medication)
        }
        
        // Verify each age group can be queried
        TestDataFactory.ValidValues.ageGroups.forEach { ageGroup ->
            val meds = dao.getMedicationsByConditionAndAge(ids.conditionIds[0], ageGroup)
            assertThat("Should find medications for age group $ageGroup",
                meds, not(empty()))
        }
    }
    
    @Test
    fun test_valid_reference_types() = runBlocking {
        // Act & Assert - Test all valid reference types
        TestDataFactory.ValidValues.referenceTypes.forEach { refType ->
            val crossRef = TestDataFactory.createCrossReference(
                fromConditionId = 1,
                toConditionId = 2,
                referenceType = refType
            )
            val refId = dao.insertCrossReference(crossRef)
            assertThat("Reference type $refType should be accepted", refId, greaterThan(0L))
        }
        
        val refs = dao.getCrossReferencesByCondition(1)
        assertThat(refs, hasSize(TestDataFactory.ValidValues.referenceTypes.size))
    }
    
    // ==================== SPECIAL CHARACTER TESTS ====================
    
    @Test
    fun test_handle_quotes_in_text_fields() = runBlocking {
        // Arrange
        val chapter = TestDataFactory.createChapter(
            chapterTitle = TestDataFactory.SpecialCharacters.withQuotes
        )
        
        // Act
        val chapterId = dao.insertChapter(chapter)
        val retrieved = dao.getChapterById(chapterId)
        
        // Assert
        assertThat(retrieved?.chapterTitle, equalTo(TestDataFactory.SpecialCharacters.withQuotes))
    }
    
    @Test
    fun test_handle_newlines_and_tabs_in_content() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val contentWithNewlines = TestDataFactory.createContentBlock(
            conditionId = ids.conditionIds[0],
            content = TestDataFactory.SpecialCharacters.withNewlines,
            orderInCondition = 1
        )
        
        val contentWithTabs = TestDataFactory.createContentBlock(
            conditionId = ids.conditionIds[0],
            content = TestDataFactory.SpecialCharacters.withTabs,
            orderInCondition = 2
        )
        
        // Act
        val id1 = dao.insertContentBlock(contentWithNewlines)
        val id2 = dao.insertContentBlock(contentWithTabs)
        
        val retrieved1 = dao.getContentBlockById(id1)
        val retrieved2 = dao.getContentBlockById(id2)
        
        // Assert
        assertThat(retrieved1?.content, equalTo(TestDataFactory.SpecialCharacters.withNewlines))
        assertThat(retrieved2?.content, equalTo(TestDataFactory.SpecialCharacters.withTabs))
    }
    
    @Test
    fun test_handle_unicode_characters() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val condition = TestDataFactory.createCondition(
            chapterId = ids.chapterId,
            conditionName = TestDataFactory.SpecialCharacters.withUnicode
        )
        
        // Act
        val conditionId = dao.insertCondition(condition)
        val retrieved = dao.getConditionById(conditionId)
        
        // Assert
        assertThat(retrieved?.conditionName, equalTo(TestDataFactory.SpecialCharacters.withUnicode))
    }
    
    @Test
    fun test_prevent_sql_injection() = runBlocking {
        // Arrange - Try to inject SQL through a text field
        val chapter = TestDataFactory.createChapter(
            description = TestDataFactory.SpecialCharacters.sqlInjection
        )
        
        // Act
        val chapterId = dao.insertChapter(chapter)
        val retrieved = dao.getChapterById(chapterId)
        
        // Assert - The SQL injection attempt should be stored as plain text
        assertThat(retrieved?.description, equalTo(TestDataFactory.SpecialCharacters.sqlInjection))
        
        // Verify tables still exist (injection didn't work)
        val allChapters = dao.getAllChapters()
        assertThat(allChapters, not(empty()))
    }
    
    @Test
    fun test_handle_json_special_characters() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val contentBlock = TestDataFactory.createContentBlock(
            conditionId = ids.conditionIds[0],
            keywords = TestDataFactory.SpecialCharacters.jsonSpecial
        )
        
        // Act
        val blockId = dao.insertContentBlock(contentBlock)
        val retrieved = dao.getContentBlockById(blockId)
        
        // Assert
        assertThat(retrieved?.keywords, equalTo(TestDataFactory.SpecialCharacters.jsonSpecial))
    }
    
    // ==================== BOUNDARY VALUE TESTS ====================
    
    @Test
    fun test_very_long_text_field() = runBlocking {
        // Arrange - Create a very long text (10,000 characters)
        val longText = "A".repeat(10000)
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val contentBlock = TestDataFactory.createContentBlock(
            conditionId = ids.conditionIds[0],
            content = longText
        )
        
        // Act
        val blockId = dao.insertContentBlock(contentBlock)
        val retrieved = dao.getContentBlockById(blockId)
        
        // Assert
        assertThat(retrieved?.content?.length, equalTo(10000))
        assertThat(retrieved?.content, equalTo(longText))
    }
    
    @Test
    fun test_empty_string_vs_null() = runBlocking {
        // Arrange
        val chapter1 = TestDataFactory.createChapter(description = "")
        val chapter2 = TestDataFactory.createChapter(description = null, chapterNumber = 2)
        
        // Act
        val id1 = dao.insertChapter(chapter1)
        val id2 = dao.insertChapter(chapter2)
        
        val retrieved1 = dao.getChapterById(id1)
        val retrieved2 = dao.getChapterById(id2)
        
        // Assert - Empty string should be preserved, null should remain null
        assertThat(retrieved1?.description, equalTo(""))
        assertThat(retrieved2?.description, nullValue())
    }
    
    @Test
    fun test_boolean_field_storage() = runBlocking {
        // Arrange
        val hierarchy = TestDataFactory.createCompleteHierarchy(conditionCount = 1, blocksPerCondition = 0)
        val ids = TestDatabaseHelper.insertTestHierarchy(dao, hierarchy)
        
        val weightBasedMed = TestDataFactory.createMedication(
            conditionId = ids.conditionIds[0],
            medicationName = "Weight Based",
            weightBased = true
        )
        
        val fixedDoseMed = TestDataFactory.createMedication(
            conditionId = ids.conditionIds[0],
            medicationName = "Fixed Dose",
            weightBased = false
        )
        
        // Act
        dao.insertMedication(weightBasedMed)
        dao.insertMedication(fixedDoseMed)
        
        val medications = dao.getMedicationsByCondition(ids.conditionIds[0])
        
        // Assert
        val weightBased = medications.find { it.medicationName == "Weight Based" }
        val fixedDose = medications.find { it.medicationName == "Fixed Dose" }
        
        assertThat(weightBased?.weightBased, `is`(true))
        assertThat(fixedDose?.weightBased, `is`(false))
    }
}