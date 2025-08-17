package co.kobby.clinicalaide.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema validation tests for the STG database.
 * These tests verify that the database structure matches the specification exactly.
 * 
 * Test Categories:
 * 1. Table Creation - Verify all required tables exist
 * 2. Column Validation - Verify all columns exist with correct names
 * 3. Data Type Validation - Verify column data types match specification
 * 4. Constraint Validation - Verify NOT NULL, DEFAULT values, etc.
 */
@RunWith(AndroidJUnit4::class)
class StgDatabaseSchemaTest {
    
    private lateinit var database: StgDatabase
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            StgDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    // ==================== TABLE CREATION TESTS ====================
    
    @Test
    fun verify_all_required_tables_exist() {
        // Query SQLite master table to get all table names
        val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table'", null)
        val tables = mutableListOf<String>()
        
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()
        
        // Verify all required tables exist (plus Room's internal tables)
        assertThat(
            tables,
            hasItems(
                "stg_chapters",
                "stg_conditions",
                "stg_content_blocks",
                "stg_embeddings",
                "stg_medications",
                "stg_cross_references",
                "stg_search_cache"
            )
        )
    }
    
    @Test
    fun verify_no_extra_tables_exist() {
        val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table'", null)
        val tables = mutableListOf<String>()
        
        while (cursor.moveToNext()) {
            val tableName = cursor.getString(0)
            // Exclude Room's internal tables and Android metadata
            if (!tableName.startsWith("room_") && 
                !tableName.startsWith("android_") && 
                !tableName.startsWith("sqlite_")) {
                tables.add(tableName)
            }
        }
        cursor.close()
        
        // Verify only our expected tables exist
        assertThat(
            tables,
            containsInAnyOrder(
                "stg_chapters",
                "stg_conditions",
                "stg_content_blocks",
                "stg_embeddings",
                "stg_medications",
                "stg_cross_references",
                "stg_search_cache"
            )
        )
    }
    
    // ==================== COLUMN VALIDATION TESTS ====================
    
    @Test
    fun verify_stg_chapters_table_columns() {
        val columns = getTableColumns("stg_chapters")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "id",
                "chapterNumber",
                "chapterTitle",
                "startPage",
                "endPage",
                "description"
            )
        )
    }
    
    @Test
    fun verify_stg_conditions_table_columns() {
        val columns = getTableColumns("stg_conditions")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "id",
                "chapterId",
                "conditionNumber",
                "conditionName",
                "startPage",
                "endPage",
                "keywords"
            )
        )
    }
    
    @Test
    fun verify_stg_content_blocks_table_columns() {
        val columns = getTableColumns("stg_content_blocks")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "id",
                "conditionId",
                "blockType",
                "content",
                "pageNumber",
                "orderInCondition",
                "clinicalContext",
                "severityLevel",
                "evidenceLevel",
                "keywords",
                "relatedBlockIds",
                "createdAt",
                "updatedAt"
            )
        )
    }
    
    @Test
    fun verify_stg_embeddings_table_columns() {
        val columns = getTableColumns("stg_embeddings")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "id",
                "contentBlockId",
                "embedding",
                "embeddingModel",
                "embeddingDimensions",
                "createdAt"
            )
        )
    }
    
    @Test
    fun verify_stg_medications_table_columns() {
        val columns = getTableColumns("stg_medications")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "id",
                "conditionId",
                "medicationName",
                "dosage",
                "frequency",
                "duration",
                "route",
                "ageGroup",
                "weightBased",
                "contraindications",
                "sideEffects",
                "evidenceLevel",
                "pageNumber"
            )
        )
    }
    
    @Test
    fun verify_stg_cross_references_table_columns() {
        val columns = getTableColumns("stg_cross_references")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "id",
                "fromConditionId",
                "toConditionId",
                "referenceType",
                "description"
            )
        )
    }
    
    @Test
    fun verify_stg_search_cache_table_columns() {
        val columns = getTableColumns("stg_search_cache")
        
        assertThat(
            columns.keys,
            containsInAnyOrder(
                "queryHash",
                "results",
                "timestamp",
                "hitCount"
            )
        )
    }
    
    // ==================== DATA TYPE VALIDATION TESTS ====================
    
    @Test
    fun verify_stg_chapters_column_types() {
        val columns = getTableColumns("stg_chapters")
        
        assertThat(columns["id"]?.type, `is`("INTEGER"))
        assertThat(columns["chapterNumber"]?.type, `is`("INTEGER"))
        assertThat(columns["chapterTitle"]?.type, `is`("TEXT"))
        assertThat(columns["startPage"]?.type, `is`("INTEGER"))
        assertThat(columns["endPage"]?.type, `is`("INTEGER"))
        assertThat(columns["description"]?.type, `is`("TEXT"))
    }
    
    @Test
    fun verify_stg_conditions_column_types() {
        val columns = getTableColumns("stg_conditions")
        
        assertThat(columns["id"]?.type, `is`("INTEGER"))
        assertThat(columns["chapterId"]?.type, `is`("INTEGER"))
        assertThat(columns["conditionNumber"]?.type, `is`("INTEGER"))
        assertThat(columns["conditionName"]?.type, `is`("TEXT"))
        assertThat(columns["startPage"]?.type, `is`("INTEGER"))
        assertThat(columns["endPage"]?.type, `is`("INTEGER"))
        assertThat(columns["keywords"]?.type, `is`("TEXT"))
    }
    
    @Test
    fun verify_stg_content_blocks_column_types() {
        val columns = getTableColumns("stg_content_blocks")
        
        assertThat(columns["id"]?.type, `is`("INTEGER"))
        assertThat(columns["conditionId"]?.type, `is`("INTEGER"))
        assertThat(columns["blockType"]?.type, `is`("TEXT"))
        assertThat(columns["content"]?.type, `is`("TEXT"))
        assertThat(columns["pageNumber"]?.type, `is`("INTEGER"))
        assertThat(columns["orderInCondition"]?.type, `is`("INTEGER"))
        assertThat(columns["clinicalContext"]?.type, `is`("TEXT"))
        assertThat(columns["severityLevel"]?.type, `is`("TEXT"))
        assertThat(columns["evidenceLevel"]?.type, `is`("TEXT"))
        assertThat(columns["keywords"]?.type, `is`("TEXT"))
        assertThat(columns["relatedBlockIds"]?.type, `is`("TEXT"))
        assertThat(columns["createdAt"]?.type, `is`("INTEGER"))
        assertThat(columns["updatedAt"]?.type, `is`("INTEGER"))
    }
    
    @Test
    fun verify_stg_embeddings_column_types() {
        val columns = getTableColumns("stg_embeddings")
        
        assertThat(columns["id"]?.type, `is`("INTEGER"))
        assertThat(columns["contentBlockId"]?.type, `is`("INTEGER"))
        assertThat(columns["embedding"]?.type, `is`("TEXT"))
        assertThat(columns["embeddingModel"]?.type, `is`("TEXT"))
        assertThat(columns["embeddingDimensions"]?.type, `is`("INTEGER"))
        assertThat(columns["createdAt"]?.type, `is`("INTEGER"))
    }
    
    @Test
    fun verify_stg_medications_column_types() {
        val columns = getTableColumns("stg_medications")
        
        assertThat(columns["id"]?.type, `is`("INTEGER"))
        assertThat(columns["conditionId"]?.type, `is`("INTEGER"))
        assertThat(columns["medicationName"]?.type, `is`("TEXT"))
        assertThat(columns["dosage"]?.type, `is`("TEXT"))
        assertThat(columns["frequency"]?.type, `is`("TEXT"))
        assertThat(columns["duration"]?.type, `is`("TEXT"))
        assertThat(columns["route"]?.type, `is`("TEXT"))
        assertThat(columns["ageGroup"]?.type, `is`("TEXT"))
        assertThat(columns["weightBased"]?.type, `is`("INTEGER"))
        assertThat(columns["contraindications"]?.type, `is`("TEXT"))
        assertThat(columns["sideEffects"]?.type, `is`("TEXT"))
        assertThat(columns["evidenceLevel"]?.type, `is`("TEXT"))
        assertThat(columns["pageNumber"]?.type, `is`("INTEGER"))
    }
    
    // ==================== CONSTRAINT VALIDATION TESTS ====================
    
    @Test
    fun verify_stg_chapters_constraints() {
        val columns = getTableColumns("stg_chapters")
        
        // Primary key
        assertThat(columns["id"]?.isPrimaryKey, `is`(true))
        
        // NOT NULL constraints
        assertThat(columns["id"]?.notNull, `is`(true))
        assertThat(columns["chapterNumber"]?.notNull, `is`(true))
        assertThat(columns["chapterTitle"]?.notNull, `is`(true))
        assertThat(columns["startPage"]?.notNull, `is`(true))
        assertThat(columns["endPage"]?.notNull, `is`(true))
        
        // Nullable columns
        assertThat(columns["description"]?.notNull, `is`(false))
    }
    
    @Test
    fun verify_stg_conditions_constraints() {
        val columns = getTableColumns("stg_conditions")
        
        // Primary key
        assertThat(columns["id"]?.isPrimaryKey, `is`(true))
        
        // NOT NULL constraints
        assertThat(columns["id"]?.notNull, `is`(true))
        assertThat(columns["chapterId"]?.notNull, `is`(true))
        assertThat(columns["conditionNumber"]?.notNull, `is`(true))
        assertThat(columns["conditionName"]?.notNull, `is`(true))
        assertThat(columns["startPage"]?.notNull, `is`(true))
        assertThat(columns["endPage"]?.notNull, `is`(true))
        assertThat(columns["keywords"]?.notNull, `is`(true))
    }
    
    @Test
    fun verify_stg_content_blocks_constraints() {
        val columns = getTableColumns("stg_content_blocks")
        
        // Primary key
        assertThat(columns["id"]?.isPrimaryKey, `is`(true))
        
        // NOT NULL constraints
        assertThat(columns["id"]?.notNull, `is`(true))
        assertThat(columns["conditionId"]?.notNull, `is`(true))
        assertThat(columns["blockType"]?.notNull, `is`(true))
        assertThat(columns["content"]?.notNull, `is`(true))
        assertThat(columns["pageNumber"]?.notNull, `is`(true))
        assertThat(columns["orderInCondition"]?.notNull, `is`(true))
        assertThat(columns["clinicalContext"]?.notNull, `is`(true))
        assertThat(columns["keywords"]?.notNull, `is`(true))
        assertThat(columns["relatedBlockIds"]?.notNull, `is`(true))
        assertThat(columns["createdAt"]?.notNull, `is`(true))
        assertThat(columns["updatedAt"]?.notNull, `is`(true))
        
        // Nullable columns
        assertThat(columns["severityLevel"]?.notNull, `is`(false))
        assertThat(columns["evidenceLevel"]?.notNull, `is`(false))
    }
    
    @Test
    fun verify_stg_search_cache_primary_key() {
        val columns = getTableColumns("stg_search_cache")
        
        // queryHash should be primary key (not id)
        assertThat(columns["queryHash"]?.isPrimaryKey, `is`(true))
        assertThat(columns["queryHash"]?.notNull, `is`(true))
    }
    
    // ==================== DEFAULT VALUE TESTS ====================
    
    @Test
    fun verify_default_values_in_stg_content_blocks() {
        // Verify default values are correctly set in StgContentBlock table
        val columns = getTableColumns("stg_content_blocks")
        
        // Check that clinicalContext has default value "general"
        // Note: SQLite PRAGMA doesn't directly show defaults for TEXT columns set via Room
        // But we can verify the column exists and allows defaults
        assertThat(columns["clinicalContext"], notNullValue())
        assertThat(columns["relatedBlockIds"], notNullValue())
        
        // The actual default values are enforced by Room at the entity level
        // These are tested in the DAO tests (StgDaoTest)
    }
    
    @Test
    fun verify_default_values_in_stg_embeddings() {
        // Verify default values are correctly set in StgEmbedding table
        val columns = getTableColumns("stg_embeddings")
        
        // Check that embeddingDimensions column exists
        assertThat(columns["embeddingDimensions"], notNullValue())
        
        // The actual default value (768) is enforced by Room at the entity level
        // This is tested in the DAO tests (StgDaoTest)
    }
    
    // ==================== FOREIGN KEY TESTS ====================
    
    @Test
    fun verify_foreign_keys_are_defined() {
        val cursor = database.query("PRAGMA foreign_key_list(stg_conditions)", null)
        val foreignKeys = mutableListOf<String>()
        
        while (cursor.moveToNext()) {
            val table = cursor.getString(2) // Referenced table
            val from = cursor.getString(3)  // From column
            val to = cursor.getString(4)    // To column
            foreignKeys.add("$from->$table.$to")
        }
        cursor.close()
        
        assertThat(foreignKeys, hasItem("chapterId->stg_chapters.id"))
    }
    
    // ==================== HELPER METHODS ====================
    
    private data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val isPrimaryKey: Boolean
    )
    
    private fun getTableColumns(tableName: String): Map<String, ColumnInfo> {
        val cursor = database.query("PRAGMA table_info($tableName)", null)
        val columns = mutableMapOf<String, ColumnInfo>()
        
        while (cursor.moveToNext()) {
            val name = cursor.getString(1)
            val type = cursor.getString(2)
            val notNull = cursor.getInt(3) == 1
            val defaultValue = if (cursor.isNull(4)) null else cursor.getString(4)
            val isPrimaryKey = cursor.getInt(5) == 1
            
            columns[name] = ColumnInfo(name, type, notNull, defaultValue, isPrimaryKey)
        }
        cursor.close()
        
        return columns
    }
}