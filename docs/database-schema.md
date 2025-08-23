# Ghana STG RAG Database Schema

## Database Overview

The RAG-ready database (`stg_rag.db`) contains 969 content chunks from the Ghana STG with complete citation support for medical queries. This database was created through OCR extraction and is optimized for Retrieval-Augmented Generation (RAG) with offline AI models.

### Database Statistics
- **31 chapters** with titles and page ranges
- **304 medical conditions** with references
- **555 medications** with dosages
- **969 content chunks** for RAG retrieval
- **Full citations** for every piece of content
- **Database size**: 598KB (optimized for mobile)

## Architecture Overview

```
chapters (31) ──────── conditions_enhanced (304)
                              │
                              │
                       content_chunks (969) ─── embeddings (future)
                              │
                              │
                       medications_enhanced (555)
```

## Table Definitions

### 1. chapters
Document structure and navigation for the Ghana STG.

```sql
CREATE TABLE chapters (
    id INTEGER PRIMARY KEY NOT NULL,
    number INTEGER NOT NULL,
    title TEXT NOT NULL,
    start_page INTEGER NOT NULL
);
```

**Sample Data:**
```
id=1, number=1, title="Cardiovascular System", start_page=15
id=18, number=18, title="Infectious Diseases", start_page=482
```

**Room Entity:**
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

### 2. content_chunks
RAG-ready content chunks with full citation support. This is the core table for semantic search and AI response generation.

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
    created_at TEXT
);
```

**Chunk Types:**
- `treatment` - Treatment protocols and guidelines
- `clinical_features` - Signs and symptoms
- `investigation` - Diagnostic procedures
- `medication` - Drug information and dosing
- `general` - Other medical content

**Sample Data:**
```
id=1
content="For uncomplicated malaria in adults: Artemether-Lumefantrine..."
chunk_type="treatment"
page_number=483
condition_name="Malaria"
reference_citation="Ghana STG 2017 - Chapter 18, Section 187, Page 483"
```

**Room Entity:**
```kotlin
@Entity(tableName = "content_chunks")
data class ContentChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "chunk_type")
    val chunkType: String,
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
    val metadata: String?,
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String?
)
```

### 3. conditions_enhanced
Medical conditions with complete references extracted via OCR.

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
    ocr_source INTEGER NOT NULL DEFAULT 1
);
```

**Sample Data:**
```
id=1, name="Malaria", chapter_number=18, page_number=483
id=2, name="Hypertension", chapter_number=1, page_number=25
```

**Room Entity:**
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

### 4. medications_enhanced
Medication information with dosing details extracted via OCR.

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
    ocr_source INTEGER NOT NULL DEFAULT 1
);
```

**Sample Data:**
```
id=1, generic_name="Artemether-Lumefantrine", strength="20mg/120mg", route="Oral"
id=2, generic_name="Paracetamol", strength="500mg", route="Oral"
```

**Room Entity:**
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

### 5. embeddings (Future Implementation)
Vector embeddings for semantic similarity search.

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

## Database Access Object (DAO)

```kotlin
@Dao
interface RagDao {
    // Search operations
    @Query("SELECT * FROM content_chunks WHERE content LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchContentChunks(query: String, limit: Int = 10): List<ContentChunk>
    
    @Query("SELECT * FROM conditions_enhanced WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchConditions(query: String, limit: Int = 10): List<ConditionEnhanced>
    
    @Query("SELECT * FROM medications_enhanced WHERE generic_name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchMedications(query: String, limit: Int = 10): List<MedicationEnhanced>
    
    // Statistics
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM chapters) as chapterCount,
            (SELECT COUNT(*) FROM content_chunks) as chunkCount,
            (SELECT COUNT(*) FROM conditions_enhanced) as conditionCount,
            (SELECT COUNT(*) FROM medications_enhanced) as medicationCount
    """)
    suspend fun getDatabaseStats(): DatabaseStats
}
```

## Schema Compatibility Notes

### Room Database Requirements
The following schema modifications were required for Room compatibility:

1. **Primary Keys**: All PRIMARY KEY fields must have explicit `NOT NULL` constraint
2. **Boolean Fields**: SQLite BOOLEAN mapped to INTEGER NOT NULL with default value
3. **Timestamps**: Changed from TIMESTAMP to TEXT for Room compatibility
4. **Indices**: Removed database-level indices (can be added via Room annotations if needed)

### Migration from Old Schema
The project migrated from the original StgDatabase schema to this RAG-optimized schema:
- Old: StgChapter, StgCondition, StgContentBlock entities
- New: Simplified RAG schema with content_chunks as the core table
- Benefit: Better suited for semantic search and AI response generation

## Query Examples

### Search for Medical Content
```kotlin
// Search for malaria treatment
val results = ragDao.searchContentChunks("malaria treatment", limit = 5)

// Each result includes:
// - content: The actual medical information
// - referenceCitation: "Ghana STG 2017 - Chapter 18, Page 483"
// - chunkType: "treatment"
```

### Get Database Statistics
```kotlin
val stats = ragDao.getDatabaseStats()
// Returns: chapterCount=31, chunkCount=969, conditionCount=304, medicationCount=555
```

### Full-Text Search with Citations
```kotlin
val chunks = ragDao.fullTextSearch("hypertension management")
chunks.forEach { chunk ->
    println("${chunk.content}")
    println("Source: ${chunk.referenceCitation}")
}
```

## Performance Characteristics

- **Database Size**: 598KB (optimized for mobile devices)
- **Search Performance**: Sub-second response for text searches
- **Memory Usage**: Efficient chunking prevents loading entire document
- **Offline Capability**: 100% offline functionality
- **Citation Coverage**: Every piece of content has verifiable page references

## Future Enhancements

1. **Vector Embeddings**: Generate 384-dimensional embeddings for semantic search
2. **Similarity Search**: Implement cosine similarity for better query matching
3. **Sections Table**: Complete section mappings for all chapters
4. **Cross-References**: Add relationships between related conditions
5. **Search Cache**: Implement caching for frequently accessed queries