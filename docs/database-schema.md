# Ghana STG Hierarchical Database Schema

## Database Overview

The hierarchical database (`stg_rag.db`) contains structured medical content from the Ghana STG 7th Edition with full semantic search support. This database was created through PyMuPDF extraction and is optimized for Retrieval-Augmented Generation (RAG) with local AI models and Android Room compatibility.

### Database Statistics
- **23 chapters** covering all medical systems
- **831 sections** with hierarchical relationships
- **664 content entries** with page references  
- **957 metadata entries** for classification
- **664 embeddings** (384 dimensions each)
- **Database size**: 3.33MB (with embeddings)

## Architecture Overview

The database uses a hierarchical structure reflecting the medical document organization:

```
chapters (23)
    │
    ├── sections (831) ──── content (664) ──── embeddings (664)
    │                           │
    │                           └── metadata (957)
    └── parent_section_id (hierarchical sections)
```

## Table Definitions

### 1. chapters
Medical system chapters from the Ghana STG document.

```sql
CREATE TABLE chapters (
    chapter_id INTEGER NOT NULL PRIMARY KEY,
    chapter_number TEXT NOT NULL,
    chapter_title TEXT NOT NULL
);
```

**Sample Data:**
```
chapter_id=1, chapter_number="Chapter 1", chapter_title="Disorders of the Gastrointestinal Tract"
chapter_id=7, chapter_number="Chapter 7", chapter_title="Cardiovascular System"
```

**Room Entity:**
```kotlin
@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chapter_id")
    val chapterId: Int = 0,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: String,
    
    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String
)
```

### 2. sections
Hierarchical sections within chapters, supporting nested subsections.

```sql
CREATE TABLE sections (
    section_id INTEGER NOT NULL PRIMARY KEY,
    chapter_id INTEGER NOT NULL,
    section_number TEXT,
    section_title TEXT NOT NULL,
    parent_section_id INTEGER,
    FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_section_id) REFERENCES sections(section_id) ON DELETE CASCADE
);
```

**Sample Data:**
```
section_id=1, chapter_id=1, section_number="8", section_title="Diarrhoea", parent_section_id=NULL
section_id=2, chapter_id=1, section_number="A", section_title="Bacterial gastroenteritis", parent_section_id=1
```

**Room Entity:**
```kotlin
@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["chapter_id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Section::class,
            parentColumns = ["section_id"],
            childColumns = ["parent_section_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chapter_id"]),
        Index(value = ["parent_section_id"])
    ]
)
data class Section(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "section_id")
    val sectionId: Int = 0,
    
    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "section_title")
    val sectionTitle: String,
    
    @ColumnInfo(name = "parent_section_id")
    val parentSectionId: Int?
)
```

### 3. content
Medical content blocks with page references and type classification.

```sql
CREATE TABLE content (
    content_id INTEGER NOT NULL PRIMARY KEY,
    section_id INTEGER NOT NULL,
    page_number INTEGER NOT NULL,
    content_text TEXT NOT NULL,
    content_type TEXT NOT NULL,
    FOREIGN KEY (section_id) REFERENCES sections(section_id) ON DELETE CASCADE
);
```

**Content Types:**
- `paragraph` - Regular text paragraphs
- `bullet` - Bullet point items
- `table` - ASCII formatted tables
- `note` - Special notes and warnings

**Sample Data:**
```
content_id=1, section_id=1, page_number=29, content_type="paragraph"
content_text="Diarrhoea is defined as the passage of frequent, loose, watery stools..."
```

**Room Entity:**
```kotlin
@Entity(
    tableName = "content",
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["section_id"],
            childColumns = ["section_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["section_id"]),
        Index(value = ["page_number"]),
        Index(value = ["content_type"])
    ]
)
data class Content(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "content_id")
    val contentId: Int = 0,
    
    @ColumnInfo(name = "section_id")
    val sectionId: Int,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "content_text")
    val contentText: String,
    
    @ColumnInfo(name = "content_type")
    val contentType: String
)
```

### 4. embeddings
Vector embeddings for semantic similarity search (384 dimensions).

```sql
CREATE TABLE embeddings (
    embedding_id INTEGER NOT NULL PRIMARY KEY,
    content_id INTEGER NOT NULL,
    embedding BLOB NOT NULL,
    FOREIGN KEY (content_id) REFERENCES content(content_id) ON DELETE CASCADE
);
```

**Sample Data:**
```
embedding_id=1, content_id=1, embedding=<384-dimensional float vector as BLOB>
```

**Room Entity:**
```kotlin
@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Content::class,
            parentColumns = ["content_id"],
            childColumns = ["content_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Embedding(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "embedding_id")
    val embeddingId: Int = 0,
    
    @ColumnInfo(name = "content_id")
    val contentId: Int,
    
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Embedding
        if (embeddingId != other.embeddingId) return false
        if (contentId != other.contentId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = embeddingId
        result = 31 * result + contentId
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
```

### 5. metadata
Key-value metadata for content classification and search enhancement.

```sql
CREATE TABLE metadata (
    metadata_id INTEGER NOT NULL PRIMARY KEY,
    content_id INTEGER NOT NULL,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    FOREIGN KEY (content_id) REFERENCES content(content_id) ON DELETE CASCADE
);
```

**Metadata Types:**
- `target_population` - children, adults, pregnant_women, elderly
- `severity` - mild, moderate, severe
- `treatment_type` - pharmacological, non-pharmacological

**Sample Data:**
```
metadata_id=1, content_id=1, key="target_population", value="children"
metadata_id=2, content_id=1, key="severity", value="moderate"
```

**Room Entity:**
```kotlin
@Entity(
    tableName = "metadata",
    foreignKeys = [
        ForeignKey(
            entity = Content::class,
            parentColumns = ["content_id"],
            childColumns = ["content_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["content_id"])
    ]
)
data class Metadata(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "metadata_id")
    val metadataId: Int = 0,
    
    @ColumnInfo(name = "content_id")
    val contentId: Int,
    
    @ColumnInfo(name = "key")
    val key: String,
    
    @ColumnInfo(name = "value")
    val value: String
)
```

## Database Access Object (DAO)

```kotlin
@Dao
interface RagDao {
    // Basic queries
    @Query("SELECT * FROM chapters ORDER BY chapter_number")
    suspend fun getAllChapters(): List<Chapter>
    
    @Query("SELECT * FROM sections WHERE chapter_id = :chapterId ORDER BY section_number")
    suspend fun getSectionsByChapter(chapterId: Int): List<Section>
    
    @Query("SELECT * FROM content WHERE section_id = :sectionId ORDER BY page_number")
    suspend fun getContentBySection(sectionId: Int): List<Content>
    
    // Search operations
    @Query("SELECT * FROM content WHERE content_text LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchContent(query: String, limit: Int = 10): List<Content>
    
    @Query("""
        SELECT c.* FROM content c 
        INNER JOIN sections s ON c.section_id = s.section_id 
        WHERE s.section_title LIKE '%' || :query || '%' 
        OR c.content_text LIKE '%' || :query || '%' 
        LIMIT :limit
    """)
    suspend fun searchContentWithSections(query: String, limit: Int = 10): List<Content>
    
    // Statistics
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM chapters) as chapterCount,
            (SELECT COUNT(*) FROM sections) as sectionCount,
            (SELECT COUNT(*) FROM content) as contentCount,
            (SELECT COUNT(*) FROM metadata) as metadataCount,
            (SELECT COUNT(*) FROM embeddings) as embeddingCount
    """)
    suspend fun getDatabaseStats(): DatabaseStats
    
    // Semantic search (for future vector similarity)
    @Query("SELECT * FROM embeddings WHERE content_id = :contentId")
    suspend fun getEmbedding(contentId: Int): Embedding?
}

data class DatabaseStats(
    val chapterCount: Int,
    val sectionCount: Int,
    val contentCount: Int,
    val metadataCount: Int,
    val embeddingCount: Int
)
```

## Schema Compatibility Notes

### Android Room Integration
The database has been successfully validated with Android Room ORM:

✅ **Schema Validation**: All tests pass with 100% success rate  
✅ **Foreign Key Constraints**: CASCADE deletes properly implemented  
✅ **Primary Keys**: Explicit `NOT NULL` constraints for Room compatibility  
✅ **Database Size**: 3.33MB optimized for mobile deployment  

### Key Technical Features
1. **Hierarchical Structure**: Proper parent-child relationships for sections
2. **Foreign Key Cascade**: Maintains referential integrity with automatic cleanup
3. **Vector Embeddings**: 384-dimensional embeddings for semantic search
4. **Content Classification**: Automatic metadata extraction and classification
5. **Room Compatibility**: Full Android Room ORM support with pre-packaged database

### Migration from Legacy Schema
The project successfully migrated from the legacy schema:
- **Old**: Flat RAG schema with content_chunks, conditions_enhanced, medications_enhanced
- **New**: Hierarchical schema with chapters → sections → content → embeddings/metadata
- **Benefit**: Better document structure representation and Room compatibility

## Query Examples

### Hierarchical Content Navigation
```kotlin
// Get all chapters
val chapters = ragDao.getAllChapters()

// Get sections for a chapter
val sections = ragDao.getSectionsByChapter(chapterId = 1)

// Get content for a section
val content = ragDao.getContentBySection(sectionId = 1)
```

### Medical Content Search
```kotlin
// Search within content text
val results = ragDao.searchContent("malaria treatment", limit = 5)

// Search across sections and content
val detailedResults = ragDao.searchContentWithSections("diarrhea", limit = 10)

// Each result includes:
// - contentText: The actual medical information
// - pageNumber: Page reference from Ghana STG
// - contentType: Type classification (paragraph, bullet, table, note)
```

### Database Statistics
```kotlin
val stats = ragDao.getDatabaseStats()
// Returns: DatabaseStats(
//   chapterCount=23,
//   sectionCount=831, 
//   contentCount=664,
//   metadataCount=957,
//   embeddingCount=664
// )
```

## Performance Characteristics

- **Database Size**: 3.33MB (includes 664 vector embeddings)
- **Search Performance**: Sub-second response for text searches
- **Memory Usage**: Hierarchical structure enables efficient selective loading
- **Offline Capability**: 100% offline functionality with local embeddings
- **Vector Search**: 384-dimensional embeddings ready for semantic similarity
- **Room Compatibility**: Optimized for Android Room ORM queries

## Semantic Search Implementation

### Vector Embedding Details
- **Model**: all-MiniLM-L6-v2 (384 dimensions)
- **Coverage**: 100% - all 664 content entries have embeddings
- **Storage**: Binary BLOB format for efficient mobile storage
- **Performance**: Ready for cosine similarity calculations

### Example Semantic Search (Future)
```kotlin
// Semantic similarity search using embeddings
class SemanticSearchService {
    suspend fun findSimilarContent(query: String, limit: Int = 5): List<Content> {
        // 1. Generate query embedding
        val queryEmbedding = generateEmbedding(query)
        
        // 2. Calculate cosine similarity with stored embeddings
        val similarities = calculateSimilarities(queryEmbedding)
        
        // 3. Return top matching content
        return getTopMatches(similarities, limit)
    }
}
```

## Current Integration Status

✅ **Database Ready**: All content extracted and embedded  
✅ **Room Validated**: Schema validation passes 100%  
✅ **Android Deployed**: Database in app assets (3.33MB)  
✅ **Embeddings Complete**: 664 vectors ready for semantic search  
⏳ **Next Phase**: Implement semantic search service in Android app