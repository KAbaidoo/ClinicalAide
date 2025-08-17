package co.kobby.clinicalaide.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.kobby.clinicalaide.data.database.StgDatabase
import co.kobby.clinicalaide.data.database.dao.StgDao

/**
 * Helper class for database test setup and teardown.
 * Provides common utilities for database testing.
 */
object TestDatabaseHelper {
    
    /**
     * Creates an in-memory database for testing.
     * This database is destroyed when the process is killed.
     */
    fun createInMemoryDatabase(): StgDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(
            context,
            StgDatabase::class.java
        ).allowMainThreadQueries() // Allow main thread queries for testing
            .build()
    }
    
    /**
     * Creates a test database with a specific name.
     * Useful for testing migrations or persistent storage.
     */
    fun createNamedDatabase(name: String): StgDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.databaseBuilder(
            context,
            StgDatabase::class.java,
            name
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }
    
    /**
     * Closes and deletes a named database.
     */
    fun deleteDatabase(database: StgDatabase, name: String) {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
    }
    
    /**
     * Inserts a complete test hierarchy into the database.
     * Returns the IDs of inserted entities for verification.
     */
    suspend fun insertTestHierarchy(
        dao: StgDao,
        hierarchy: TestDataFactory.CompleteHierarchy
    ): TestHierarchyIds {
        // Insert chapter
        val chapterId = dao.insertChapter(hierarchy.chapter)
        
        // Insert conditions with updated chapterId
        val conditionIds = mutableListOf<Long>()
        hierarchy.conditions.forEach { condition ->
            val updatedCondition = condition.copy(chapterId = chapterId)
            val conditionId = dao.insertCondition(updatedCondition)
            conditionIds.add(conditionId)
        }
        
        // Insert content blocks
        val blockIds = mutableListOf<Long>()
        conditionIds.forEachIndexed { index, actualConditionId ->
            // Use index as the key to match with content blocks
            val tempId = index.toLong()
            hierarchy.contentBlocks[tempId]?.forEach { block ->
                val blockId = dao.insertContentBlock(block.copy(conditionId = actualConditionId))
                blockIds.add(blockId)
            }
        }
        
        // Insert embeddings for each block if they exist
        if (hierarchy.embeddings.isNotEmpty()) {
            blockIds.forEach { blockId ->
                // Create a simple embedding for each block
                dao.insertEmbedding(TestDataFactory.createEmbedding(contentBlockId = blockId))
            }
        }
        
        // Insert medications
        val medicationIds = mutableListOf<Long>()
        conditionIds.forEachIndexed { index, actualConditionId ->
            // Use index as the key to match with medications
            val tempId = index.toLong()
            hierarchy.medications[tempId]?.forEach { medication ->
                val medId = dao.insertMedication(medication.copy(conditionId = actualConditionId))
                medicationIds.add(medId)
            }
        }
        
        // Get actual embedding IDs
        val embeddingIds = dao.getAllEmbeddings().map { it.id }
        
        return TestHierarchyIds(
            chapterId = chapterId,
            conditionIds = conditionIds,
            blockIds = blockIds,
            embeddingIds = embeddingIds,
            medicationIds = medicationIds
        )
    }
    
    /**
     * Clears all data from the database.
     * Useful for resetting state between tests.
     */
    suspend fun clearDatabase(dao: StgDao) {
        // Delete all chapters (cascade will handle the rest)
        dao.deleteAllChapters()
        // Clear search cache separately (no foreign key)
        dao.deleteAllSearchCache()
    }
    
    /**
     * Verifies that the database is empty.
     */
    suspend fun isDatabaseEmpty(dao: StgDao): Boolean {
        return dao.getAllChapters().isEmpty() &&
                dao.getAllContentBlocks().isEmpty() &&
                dao.getAllEmbeddings().isEmpty()
    }
    
    /**
     * Creates test data with special characters for integrity testing.
     */
    fun createSpecialCharacterTestData(): List<String> {
        return listOf(
            TestDataFactory.SpecialCharacters.withQuotes,
            TestDataFactory.SpecialCharacters.withNewlines,
            TestDataFactory.SpecialCharacters.withTabs,
            TestDataFactory.SpecialCharacters.withUnicode,
            TestDataFactory.SpecialCharacters.sqlInjection,
            TestDataFactory.SpecialCharacters.jsonSpecial
        )
    }
    
    /**
     * Validates JSON string format.
     */
    fun isValidJson(json: String): Boolean {
        return try {
            // Simple validation - check if it starts with [ or { and ends with ] or }
            (json.startsWith("[") && json.endsWith("]")) ||
            (json.startsWith("{") && json.endsWith("}"))
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generates a unique hash for testing.
     */
    fun generateTestHash(prefix: String = "test"): String {
        return "${prefix}_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * Data class to hold IDs after inserting test hierarchy.
     */
    data class TestHierarchyIds(
        val chapterId: Long,
        val conditionIds: List<Long>,
        val blockIds: List<Long>,
        val embeddingIds: List<Long>,
        val medicationIds: List<Long>
    )
    
    /**
     * Waits for a condition to become true, useful for async operations.
     * Times out after the specified duration.
     */
    suspend fun waitFor(
        timeoutMillis: Long = 5000,
        intervalMillis: Long = 100,
        condition: suspend () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (condition()) {
                return true
            }
            kotlinx.coroutines.delay(intervalMillis)
        }
        return false
    }
    
    /**
     * Measures the execution time of a suspend function.
     */
    suspend fun <T> measureTime(block: suspend () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        return Pair(result, duration)
    }
}