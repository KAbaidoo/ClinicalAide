# Ghana STG Database Design for Android AI Chatbot

## Database Overview

The RAG-ready database (`stg_rag_complete.db`) contains 969 content chunks from the Ghana STG with complete citation support for medical queries.

### Database Statistics
- **31 chapters** with titles and page ranges
- **304 medical conditions** with references
- **555 medications** with dosages
- **969 content chunks** for RAG retrieval
- **Full citations** for every piece of content

---

## Current Database Schema (SQLite)

### 1. **chapters**
Document structure and navigation
```sql
CREATE TABLE chapters (
    id INTEGER PRIMARY KEY NOT NULL,
    number INTEGER NOT NULL,
    title TEXT NOT NULL,
    start_page INTEGER NOT NULL
);
```

### 2. **sections**
Section mappings within chapters
```sql
CREATE TABLE sections (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    chapter_number INTEGER NOT NULL,
    section_number TEXT NOT NULL,
    title TEXT NOT NULL,
    page_number INTEGER NOT NULL,
    FOREIGN KEY (chapter_number) REFERENCES chapters(number)
);
```

### 3. **conditions_enhanced**
Medical conditions with complete references
```sql
CREATE TABLE conditions_enhanced (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    chapter_number INTEGER,
    section_number TEXT,
    page_number INTEGER NOT NULL,
    clinical_features TEXT,
    investigations TEXT,
    treatment TEXT,
    reference_citation TEXT,
    ocr_source BOOLEAN DEFAULT 1
);
```

### 4. **medications_enhanced**
Medication information with references
```sql
CREATE TABLE medications_enhanced (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    generic_name TEXT NOT NULL,
    chapter_number INTEGER,
    section_number TEXT,
    page_number INTEGER NOT NULL,
    strength TEXT,
    route TEXT,
    dosage_info TEXT,
    reference_citation TEXT,
    ocr_source BOOLEAN DEFAULT 1
);
```

### 5. **content_chunks**
RAG-ready content chunks with citations
```sql
CREATE TABLE content_chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    content TEXT NOT NULL,
    chunk_type TEXT NOT NULL,
    source_id INTEGER,
    chapter_number INTEGER,
    chapter_title TEXT,
    section_number TEXT,
    page_number INTEGER NOT NULL,
    condition_name TEXT,
    reference_citation TEXT NOT NULL,
    metadata TEXT,
    embedding BLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6. **embeddings**
Vector embeddings for similarity search
```sql
CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    chunk_id INTEGER NOT NULL,
    embedding BLOB NOT NULL,
    model_name TEXT DEFAULT 'universal-sentence-encoder',
    dimension INTEGER DEFAULT 512,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chunk_id) REFERENCES content_chunks(id)
);
```

### Indexes
```sql
CREATE INDEX idx_chunks_type ON content_chunks(chunk_type);
CREATE INDEX idx_chunks_condition ON content_chunks(condition_name);
CREATE INDEX idx_chunks_page ON content_chunks(page_number);
```

---

## Android Room Implementation

### Room Entity Classes

#### 1. Chapter Entity
```kotlin
@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey
    val id: Int,
    
    @ColumnInfo(name = "number")
    val number: Int,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "start_page")
    val startPage: Int
)
```

#### 2. Section Entity
```kotlin
@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["number"],
            childColumns = ["chapter_number"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chapter_number"])]
)
data class Section(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int
)
```

#### 3. ConditionEnhanced Entity
```kotlin
@Entity(tableName = "conditions_enhanced")
data class ConditionEnhanced(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int?,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "clinical_features")
    val clinicalFeatures: String?,
    
    @ColumnInfo(name = "investigations")
    val investigations: String?,
    
    @ColumnInfo(name = "treatment")
    val treatment: String?,
    
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String?,
    
    @ColumnInfo(name = "ocr_source")
    val ocrSource: Boolean = true
)
```

#### 4. MedicationEnhanced Entity
```kotlin
@Entity(tableName = "medications_enhanced")
data class MedicationEnhanced(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "generic_name")
    val genericName: String,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int?,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "strength")
    val strength: String?,
    
    @ColumnInfo(name = "route")
    val route: String?,
    
    @ColumnInfo(name = "dosage_info")
    val dosageInfo: String?,
    
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String?,
    
    @ColumnInfo(name = "ocr_source")
    val ocrSource: Boolean = true
)
```

#### 5. ContentChunk Entity
```kotlin
@Entity(
    tableName = "content_chunks",
    indices = [
        Index(value = ["chunk_type"]),
        Index(value = ["condition_name"]),
        Index(value = ["page_number"])
    ]
)
data class ContentChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "chunk_type")
    val chunkType: String, // "treatment", "clinical_features", "medication", etc.
    
    @ColumnInfo(name = "source_id")
    val sourceId: Int?,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int?,
    
    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String?,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "condition_name")
    val conditionName: String?,
    
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String,
    
    @ColumnInfo(name = "metadata")
    val metadata: String?, // JSON string
    
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String?
) {
    // For comparing ByteArray properly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContentChunk
        return id == other.id
    }
    
    override fun hashCode(): Int = id
}
```

#### 6. Embedding Entity
```kotlin
@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ContentChunk::class,
            parentColumns = ["id"],
            childColumns = ["chunk_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chunk_id"])]
)
data class Embedding(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "chunk_id")
    val chunkId: Int,
    
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray,
    
    @ColumnInfo(name = "model_name")
    val modelName: String = "universal-sentence-encoder",
    
    @ColumnInfo(name = "dimension")
    val dimension: Int = 512,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String?
)
```

---

## Data Access Objects (DAOs)

### 1. ChapterDao
```kotlin
@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY number")
    suspend fun getAllChapters(): List<Chapter>
    
    @Query("SELECT * FROM chapters WHERE number = :chapterNumber")
    suspend fun getChapterByNumber(chapterNumber: Int): Chapter?
    
    @Query("SELECT * FROM chapters WHERE :pageNumber >= start_page")
    suspend fun getChapterForPage(pageNumber: Int): Chapter?
}
```

### 2. ContentChunkDao
```kotlin
@Dao
interface ContentChunkDao {
    @Query("""
        SELECT * FROM content_chunks 
        WHERE content LIKE '%' || :query || '%' 
           OR condition_name LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN condition_name LIKE :query THEN 1
                WHEN condition_name LIKE '%' || :query || '%' THEN 2
                ELSE 3
            END
        LIMIT :limit
    """)
    suspend fun searchChunks(query: String, limit: Int = 10): List<ContentChunk>
    
    @Query("""
        SELECT * FROM content_chunks 
        WHERE chunk_type = :type 
        LIMIT :limit
    """)
    suspend fun getChunksByType(type: String, limit: Int = 50): List<ContentChunk>
    
    @Query("""
        SELECT * FROM content_chunks 
        WHERE condition_name = :conditionName
        ORDER BY chunk_type
    """)
    suspend fun getChunksForCondition(conditionName: String): List<ContentChunk>
    
    @Query("SELECT * FROM content_chunks WHERE embedding IS NOT NULL")
    suspend fun getAllChunksWithEmbeddings(): List<ContentChunk>
    
    @Query("SELECT COUNT(*) FROM content_chunks")
    suspend fun getTotalChunks(): Int
}
```

### 3. ConditionDao
```kotlin
@Dao
interface ConditionDao {
    @Query("SELECT * FROM conditions_enhanced ORDER BY name")
    suspend fun getAllConditions(): List<ConditionEnhanced>
    
    @Query("""
        SELECT * FROM conditions_enhanced 
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT :limit
    """)
    suspend fun searchConditions(query: String, limit: Int = 20): List<ConditionEnhanced>
    
    @Query("SELECT * FROM conditions_enhanced WHERE name = :name")
    suspend fun getConditionByName(name: String): ConditionEnhanced?
    
    @Query("""
        SELECT * FROM conditions_enhanced 
        WHERE chapter_number = :chapterNumber
        ORDER BY page_number
    """)
    suspend fun getConditionsByChapter(chapterNumber: Int): List<ConditionEnhanced>
}
```

### 4. MedicationDao
```kotlin
@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications_enhanced ORDER BY generic_name")
    suspend fun getAllMedications(): List<MedicationEnhanced>
    
    @Query("""
        SELECT * FROM medications_enhanced 
        WHERE generic_name LIKE '%' || :query || '%'
        ORDER BY generic_name
        LIMIT :limit
    """)
    suspend fun searchMedications(query: String, limit: Int = 20): List<MedicationEnhanced>
    
    @Query("SELECT * FROM medications_enhanced WHERE generic_name = :name")
    suspend fun getMedicationByName(name: String): MedicationEnhanced?
}
```

### 5. EmbeddingDao
```kotlin
@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings WHERE chunk_id = :chunkId")
    suspend fun getEmbeddingForChunk(chunkId: Int): Embedding?
    
    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun getTotalEmbeddings(): Int
    
    @Insert
    suspend fun insertEmbedding(embedding: Embedding)
    
    @Update
    suspend fun updateEmbedding(embedding: Embedding)
}
```

---

## Room Database Class

```kotlin
@Database(
    entities = [
        Chapter::class,
        Section::class,
        ConditionEnhanced::class,
        MedicationEnhanced::class,
        ContentChunk::class,
        Embedding::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(STGTypeConverters::class)
abstract class STGDatabase : RoomDatabase() {
    
    abstract fun chapterDao(): ChapterDao
    abstract fun contentChunkDao(): ContentChunkDao
    abstract fun conditionDao(): ConditionDao
    abstract fun medicationDao(): MedicationDao
    abstract fun embeddingDao(): EmbeddingDao
    
    companion object {
        @Volatile
        private var INSTANCE: STGDatabase? = null
        
        fun getInstance(context: Context): STGDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    STGDatabase::class.java,
                    "stg_rag.db"
                )
                .createFromAsset("databases/stg_rag.db") // Pre-populated database
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
```

## Type Converters

```kotlin
class STGTypeConverters {
    
    @TypeConverter
    fun fromByteArray(bytes: ByteArray?): String? {
        return bytes?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }
    
    @TypeConverter
    fun toByteArray(base64: String?): ByteArray? {
        return base64?.let { Base64.decode(it, Base64.DEFAULT) }
    }
    
    @TypeConverter
    fun fromFloatArray(floats: FloatArray?): ByteArray? {
        return floats?.let { array ->
            val buffer = ByteBuffer.allocate(array.size * 4)
            buffer.asFloatBuffer().put(array)
            buffer.array()
        }
    }
    
    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        return bytes?.let { array ->
            val buffer = ByteBuffer.wrap(array)
            val floatBuffer = buffer.asFloatBuffer()
            val floats = FloatArray(floatBuffer.remaining())
            floatBuffer.get(floats)
            floats
        }
    }
}
```

---

## Repository Pattern

```kotlin
class STGRepository(
    private val chapterDao: ChapterDao,
    private val contentChunkDao: ContentChunkDao,
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao,
    private val embeddingDao: EmbeddingDao
) {
    
    // Search for medical content with citations
    suspend fun searchMedicalContent(query: String): MedicalSearchResult {
        val chunks = contentChunkDao.searchChunks(query, limit = 10)
        val conditions = conditionDao.searchConditions(query, limit = 5)
        val medications = medicationDao.searchMedications(query, limit = 5)
        
        return MedicalSearchResult(
            chunks = chunks,
            conditions = conditions,
            medications = medications
        )
    }
    
    // Get content for RAG pipeline
    suspend fun getRAGContext(queryEmbedding: FloatArray, topK: Int = 5): List<ContentChunk> {
        val allChunks = contentChunkDao.getAllChunksWithEmbeddings()
        
        // Calculate similarities and return top K
        return allChunks
            .map { chunk ->
                val similarity = calculateCosineSimilarity(
                    queryEmbedding,
                    chunk.embedding?.toFloatArray() ?: floatArrayOf()
                )
                Pair(chunk, similarity)
            }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }
    
    // Get complete information for a condition
    suspend fun getConditionDetails(conditionName: String): ConditionDetail {
        val condition = conditionDao.getConditionByName(conditionName)
        val chunks = contentChunkDao.getChunksForCondition(conditionName)
        
        return ConditionDetail(
            condition = condition,
            treatmentChunks = chunks.filter { it.chunkType == "treatment" },
            clinicalChunks = chunks.filter { it.chunkType == "clinical_features" },
            investigationChunks = chunks.filter { it.chunkType == "investigations" }
        )
    }
    
    private fun calculateCosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return if (normA > 0 && normB > 0) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else 0f
    }
}
```

---

## Data Models

```kotlin
data class MedicalSearchResult(
    val chunks: List<ContentChunk>,
    val conditions: List<ConditionEnhanced>,
    val medications: List<MedicationEnhanced>
)

data class ConditionDetail(
    val condition: ConditionEnhanced?,
    val treatmentChunks: List<ContentChunk>,
    val clinicalChunks: List<ContentChunk>,
    val investigationChunks: List<ContentChunk>
)

data class Citation(
    val chapterNumber: Int?,
    val sectionNumber: String?,
    val pageNumber: Int,
    val reference: String
)
```

---

## Usage Examples

### 1. Basic Setup
```kotlin
class MyApplication : Application() {
    val database by lazy { STGDatabase.getInstance(this) }
    val repository by lazy {
        STGRepository(
            database.chapterDao(),
            database.contentChunkDao(),
            database.conditionDao(),
            database.medicationDao(),
            database.embeddingDao()
        )
    }
}
```

### 2. Search Query
```kotlin
// In ViewModel
viewModelScope.launch {
    val results = repository.searchMedicalContent("malaria")
    
    // Process results
    results.chunks.forEach { chunk ->
        println("Content: ${chunk.content}")
        println("Reference: ${chunk.referenceCitation}")
    }
}
```

### 3. RAG Query with Embeddings
```kotlin
// Generate embedding for query
val queryEmbedding = tensorflowModel.generateEmbedding(query)

// Get relevant context
val context = repository.getRAGContext(queryEmbedding, topK = 5)

// Build prompt for Gemma
val prompt = buildPrompt(query, context)

// Get response from Gemma
val response = gemmaModel.generate(prompt)
```

---

## Pre-populated Database Setup

### Asset Configuration
Place `stg_rag.db` in: `app/src/main/assets/databases/stg_rag.db`

### Gradle Configuration
```gradle
android {
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += ["room.schemaLocation": "$projectDir/schemas"]
            }
        }
    }
}

dependencies {
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
}
```

---

## Performance Considerations

1. **Embedding Storage**: Store as BLOB (ByteArray) for efficiency
2. **Indexing**: Critical indexes on chunk_type, condition_name, page_number
3. **Batch Operations**: Use transactions for multiple operations
4. **Lazy Loading**: Load embeddings only when needed
5. **Caching**: Consider caching frequent queries

---

## Migration Strategy

For future updates:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns or tables as needed
    }
}
```

---

## Summary

This complete Room implementation provides:
- ✅ All 6 entity classes matching the database schema
- ✅ 5 DAO interfaces with essential queries
- ✅ Room database configuration with pre-populated data
- ✅ Type converters for BLOB/embedding handling
- ✅ Repository pattern for clean architecture
- ✅ Full citation support for medical queries
- ✅ Ready for RAG pipeline integration