# Ghana STG Database Schema

## Core Database Structure

This document outlines the complete database schema for the Ghana STG Clinical Chatbot application. The schema is designed to support hierarchical medical content, semantic search, and offline functionality.

## Entity Relationship Overview

```
StgChapter (1) ──────── (Many) StgCondition
                                    │
                                    │ (1)
                                    │
                                    │ (Many)
                              StgContentBlock ──── (1) StgEmbedding
                                    │
                                    │ (1)
                                    │
                                    │ (Many)
                              StgMedication
```

## Table Definitions

### Core Entities

#### StgChapter
Represents major medical system categories from the Ghana STG document.

```kotlin
@Entity(tableName = "stg_chapters")
data class StgChapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterNumber: Int,
    val chapterTitle: String,
    val startPage: Int,
    val endPage: Int,
    val description: String? = null
)
```

**Sample Data:**
```
id=1, chapterNumber=1, chapterTitle="Disorders of the Gastrointestinal Tract", startPage=29, endPage=57
id=2, chapterNumber=2, chapterTitle="Disorders of the Liver", startPage=58, endPage=85
```

#### StgCondition
Individual medical conditions within each chapter.

```kotlin
@Entity(
    tableName = "stg_conditions",
    foreignKeys = [
        ForeignKey(
            entity = StgChapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StgCondition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterId: Long,
    val conditionNumber: Int, // e.g., 1 for "Diarrhoea"
    val conditionName: String, // e.g., "Diarrhoea"
    val startPage: Int,
    val endPage: Int,
    val keywords: String // JSON array of searchable terms
)
```

**Sample Data:**
```
id=1, chapterId=1, conditionNumber=1, conditionName="Diarrhoea", startPage=29, endPage=32
id=2, chapterId=1, conditionNumber=2, conditionName="Rotavirus Disease", startPage=33, endPage=35
```

#### StgContentBlock
Structured content sections for each medical condition.

```kotlin
@Entity(
    tableName = "stg_content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = StgCondition::class,
            parentColumns = ["id"],
            childColumns = ["conditionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StgContentBlock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conditionId: Long,
    val blockType: String, // "definition", "causes", "symptoms", "treatment", "dosage", "referral", "contraindications"
    val content: String,
    val pageNumber: Int,
    val orderInCondition: Int, // Sequence within condition
    val clinicalContext: String = "general", // "pediatric", "adult", "pregnancy", "elderly", "emergency"
    val severityLevel: String? = null, // "mild", "moderate", "severe"
    val evidenceLevel: String? = null, // "A", "B", "C"
    val keywords: String, // Extracted key medical terms for better matching
    val relatedBlockIds: String = "[]", // JSON array of related block IDs
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Block Types:**
- `definition`: Medical definition and description
- `causes`: Etiology and risk factors
- `symptoms`: Clinical presentation and signs
- `treatment`: Therapeutic interventions
- `dosage`: Specific medication dosing
- `referral`: When and where to refer patients
- `contraindications`: When not to use treatments
- `diagnosis`: Diagnostic criteria and methods

**Clinical Contexts:**
- `general`: Applies to all patient populations
- `pediatric`: Children and adolescents
- `adult`: Adult population
- `pregnancy`: Pregnant women
- `elderly`: Geriatric population
- `neonatal`: Newborns (0-28 days)
- `emergency`: Emergency/urgent care scenarios

#### StgEmbedding
Vector embeddings for semantic search functionality.

```kotlin
@Entity(
    tableName = "stg_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = StgContentBlock::class,
            parentColumns = ["id"],
            childColumns = ["contentBlockId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StgEmbedding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentBlockId: Long,
    val embedding: String, // JSON string of the vector embedding
    val embeddingModel: String, // "text-embedding-004", "universal-sentence-encoder", etc.
    val embeddingDimensions: Int = 768, // Track vector size
    val createdAt: Long = System.currentTimeMillis()
)
```

### Supporting Entities

#### StgMedication
Detailed medication information extracted from treatment protocols.

```kotlin
@Entity(
    tableName = "stg_medications",
    foreignKeys = [
        ForeignKey(
            entity = StgCondition::class,
            parentColumns = ["id"],
            childColumns = ["conditionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StgMedication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conditionId: Long,
    val medicationName: String,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val route: String, // "oral", "IV", "IM", "topical", etc.
    val ageGroup: String, // "adult", "pediatric", "neonatal", "elderly"
    val weightBased: Boolean = false, // true if dose is per kg
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val evidenceLevel: String? = null,
    val pageNumber: Int
)
```

**Sample Data:**
```
medicationName="Oral Rehydration Solution (ORS)"
dosage="75ml/kg"
frequency="over 4 hours"
duration="until rehydrated"
route="oral"
ageGroup="pediatric"
weightBased=true
```

#### StgCrossReference
Relationships between different medical conditions.

```kotlin
@Entity(tableName = "stg_cross_references")
data class StgCrossReference(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromConditionId: Long,
    val toConditionId: Long,
    val referenceType: String, // "see_also", "differential", "complication", "prerequisite"
    val description: String? = null
)
```

**Reference Types:**
- `see_also`: Related conditions for additional reference
- `differential`: Conditions to consider in differential diagnosis
- `complication`: Potential complications of the condition
- `prerequisite`: Conditions that must be ruled out first

#### StgSearchCache
Performance optimization for frequently searched queries.

```kotlin
@Entity(tableName = "stg_search_cache")
data class StgSearchCache(
    @PrimaryKey
    val queryHash: String,
    val results: String, // JSON string of search results
    val timestamp: Long = System.currentTimeMillis(),
    val hitCount: Int = 1
)
```

## Data Access Objects (DAOs)

### Primary DAO Interface

```kotlin
@Dao
interface StgDao {
    
    // Chapter operations
    @Query("SELECT * FROM stg_chapters ORDER BY chapterNumber")
    suspend fun getAllChapters(): List<StgChapter>
    
    @Query("SELECT * FROM stg_chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: Long): StgChapter?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: StgChapter): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<StgChapter>)
    
    // Condition operations
    @Query("SELECT * FROM stg_conditions WHERE chapterId = :chapterId ORDER BY conditionNumber")
    suspend fun getConditionsByChapter(chapterId: Long): List<StgCondition>
    
    @Query("SELECT * FROM stg_conditions WHERE conditionName LIKE '%' || :searchTerm || '%'")
    suspend fun searchConditions(searchTerm: String): List<StgCondition>
    
    @Query("SELECT * FROM stg_conditions WHERE id = :conditionId")
    suspend fun getConditionById(conditionId: Long): StgCondition?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: StgCondition): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConditions(conditions: List<StgCondition>)
    
    // Content block operations
    @Query("""
        SELECT * FROM stg_content_blocks 
        WHERE conditionId = :conditionId 
        ORDER BY orderInCondition
    """)
    suspend fun getContentBlocksByCondition(conditionId: Long): List<StgContentBlock>
    
    @Query("""
        SELECT * FROM stg_content_blocks 
        WHERE conditionId = :conditionId AND blockType = :blockType
        ORDER BY orderInCondition
    """)
    suspend fun getContentBlocksByType(conditionId: Long, blockType: String): List<StgContentBlock>
    
    @Query("""
        SELECT cb.* FROM stg_content_blocks cb
        WHERE cb.clinicalContext = :context OR cb.clinicalContext = 'general'
        ORDER BY cb.id
    """)
    suspend fun getContentBlocksByContext(context: String): List<StgContentBlock>
    
    @Query("SELECT * FROM stg_content_blocks WHERE id IN (:blockIds)")
    suspend fun getContentBlocksByIds(blockIds: List<Long>): List<StgContentBlock>
    
    @Query("SELECT * FROM stg_content_blocks")
    suspend fun getAllContentBlocks(): List<StgContentBlock>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentBlock(contentBlock: StgContentBlock): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentBlocks(contentBlocks: List<StgContentBlock>)
    
    // Embedding operations
    @Query("SELECT * FROM stg_embeddings WHERE contentBlockId = :contentBlockId")
    suspend fun getEmbeddingByContentBlock(contentBlockId: Long): StgEmbedding?
    
    @Query("SELECT * FROM stg_embeddings")
    suspend fun getAllEmbeddings(): List<StgEmbedding>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: StgEmbedding): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<StgEmbedding>)
    
    // Complex queries for context building
    @Query("""
        SELECT cb.*, c.conditionName, ch.chapterTitle 
        FROM stg_content_blocks cb
        JOIN stg_conditions c ON cb.conditionId = c.id
        JOIN stg_chapters ch ON c.chapterId = ch.id
        WHERE cb.id IN (:blockIds)
        ORDER BY cb.conditionId, cb.orderInCondition
    """)
    suspend fun getContentBlocksWithMetadata(blockIds: List<Long>): List<ContentBlockWithMetadata>
    
    // Medication operations
    @Query("""
        SELECT * FROM stg_medications 
        WHERE conditionId = :conditionId AND ageGroup = :ageGroup
    """)
    suspend fun getMedicationsByConditionAndAge(conditionId: Long, ageGroup: String): List<StgMedication>
    
    @Query("SELECT * FROM stg_medications WHERE conditionId = :conditionId")
    suspend fun getMedicationsByCondition(conditionId: Long): List<StgMedication>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: StgMedication): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(medications: List<StgMedication>)
    
    // Cross-reference operations
    @Query("SELECT * FROM stg_cross_references WHERE fromConditionId = :conditionId")
    suspend fun getCrossReferencesByCondition(conditionId: Long): List<StgCrossReference>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossReference(crossReference: StgCrossReference): Long
    
    // Search cache operations
    @Query("SELECT * FROM stg_search_cache WHERE queryHash = :hash")
    suspend fun getCachedSearch(hash: String): StgSearchCache?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheSearch(cache: StgSearchCache)
    
    @Query("DELETE FROM stg_search_cache WHERE timestamp < :cutoffTime")
    suspend fun clearOldCache(cutoffTime: Long)
    
    // Full-text search as backup to semantic search
    @Query("""
        SELECT * FROM stg_content_blocks 
        WHERE content LIKE '%' || :searchTerm || '%' 
        OR keywords LIKE '%' || :searchTerm || '%'
        ORDER BY 
            CASE WHEN content LIKE :searchTerm || '%' THEN 1 ELSE 2 END,
            LENGTH(content)
        LIMIT :limit
    """)
    suspend fun searchContentByText(searchTerm: String, limit: Int = 10): List<StgContentBlock>
}
```

## Database Configuration

### Room Database Class

```kotlin
@Database(
    entities = [
        StgChapter::class,
        StgCondition::class,
        StgContentBlock::class,
        StgEmbedding::class,
        StgMedication::class,
        StgCrossReference::class,
        StgSearchCache::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StgDatabase : RoomDatabase() {
    abstract fun stgDao(): StgDao
    
    companion object {
        @Volatile
        private var INSTANCE: StgDatabase? = null
        
        fun getDatabase(context: Context): StgDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StgDatabase::class.java,
                    "stg_database"
                )
                .fallbackToDestructiveMigration() // For development
                .enableMultiInstanceInvalidation()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

### Type Converters

```kotlin
class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return gson.fromJson(value, object : TypeToken<List<String>>() {}.type)
    }
    
    @TypeConverter
    fun fromLongList(value: List<Long>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toLongList(value: String): List<Long> {
        return gson.fromJson(value, object : TypeToken<List<Long>>() {}.type)
    }
    
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        return gson.fromJson(value, FloatArray::class.java)
    }
}
```

## Data Transfer Objects

### Complex Query Results

```kotlin
data class ContentBlockWithMetadata(
    @Embedded val contentBlock: StgContentBlock,
    val conditionName: String,
    val chapterTitle: String
)

data class ConditionWithChapter(
    @Embedded val condition: StgCondition,
    val chapterTitle: String
)

data class MedicationWithCondition(
    @Embedded val medication: StgMedication,
    val conditionName: String,
    val chapterTitle: String
)
```

### Context Building Objects

```kotlin
data class LLMContext(
    val primaryContent: List<ContentBlockWithMetadata>,
    val supportingContent: List<ContentBlockWithMetadata>,
    val medications: List<StgMedication>,
    val clinicalMetadata: ClinicalMetadata,
    val citations: List<Citation>
)

data class ClinicalMetadata(
    val conditions: List<String>,
    val chapters: List<String>,
    val patientContext: String,
    val evidenceLevels: List<String>,
    val severityLevels: List<String>
)

data class Citation(
    val pageNumber: Int,
    val chapterTitle: String,
    val conditionName: String,
    val contentType: String
)
```

## Database Indices

For optimal query performance, create the following indices:

```sql
-- Content search optimization
CREATE INDEX idx_content_blocks_condition_type ON stg_content_blocks(conditionId, blockType);
CREATE INDEX idx_content_blocks_context ON stg_content_blocks(clinicalContext);
CREATE INDEX idx_content_blocks_keywords ON stg_content_blocks(keywords);

-- Embedding search optimization
CREATE INDEX idx_embeddings_content_block ON stg_embeddings(contentBlockId);

-- Medication search optimization
CREATE INDEX idx_medications_condition_age ON stg_medications(conditionId, ageGroup);

-- Cross-reference optimization
CREATE INDEX idx_cross_references_from ON stg_cross_references(fromConditionId);

-- Search cache optimization
CREATE INDEX idx_search_cache_timestamp ON stg_search_cache(timestamp);
```

## Data Validation Rules

### Content Block Validation
- `blockType` must be one of the predefined types
- `clinicalContext` must be one of the predefined contexts
- `content` must not be empty
- `pageNumber` must be > 0
- `orderInCondition` must be > 0

### Medication Validation
- `dosage` must include units (mg, ml, etc.)
- `ageGroup` must be one of the predefined age groups
- `route` must be a valid administration route
- `medicationName` must not be empty

### Embedding Validation
- `embedding` must be valid JSON array
- `embeddingDimensions` must match actual vector size
- `embeddingModel` must be specified

This schema provides a robust foundation for storing, querying, and retrieving Ghana STG content while supporting advanced features like semantic search and clinical context awareness.
