package co.kobby.clinicalaide.data

import co.kobby.clinicalaide.data.rag.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Utility functions and helpers for testing the RAG database and search functionality.
 */
object TestUtils {
    
    // ==================== MOCK DATA GENERATORS ====================
    
    /**
     * Create mock content for testing
     */
    fun createMockContent(
        contentId: Int = Random.nextInt(10000, 99999),
        sectionId: Int = 1,
        pageNumber: Int = Random.nextInt(29, 692),
        contentType: String = "paragraph",
        contentText: String = "Mock content text for testing purposes"
    ): Content {
        return Content(
            contentId = contentId,
            sectionId = sectionId,
            pageNumber = pageNumber,
            contentText = contentText,
            contentType = contentType
        )
    }
    
    /**
     * Create mock chapter for testing
     */
    fun createMockChapter(
        chapterId: Int = Random.nextInt(100, 999),
        chapterNumber: String = "Chapter ${Random.nextInt(1, 50)}",
        chapterTitle: String = "Mock Chapter Title"
    ): Chapter {
        return Chapter(
            chapterId = chapterId,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle
        )
    }
    
    /**
     * Create mock section for testing
     */
    fun createMockSection(
        sectionId: Int = Random.nextInt(1000, 9999),
        chapterId: Int = 1,
        sectionNumber: String? = "${Random.nextInt(1, 20)}",
        sectionTitle: String = "Mock Section Title",
        parentSectionId: Int? = null
    ): Section {
        return Section(
            sectionId = sectionId,
            chapterId = chapterId,
            sectionNumber = sectionNumber,
            sectionTitle = sectionTitle,
            parentSectionId = parentSectionId
        )
    }
    
    /**
     * Create mock metadata for testing
     */
    fun createMockMetadata(
        metadataId: Int = Random.nextInt(10000, 99999),
        contentId: Int = 1,
        key: String = "test_key",
        value: String = "test_value"
    ): Metadata {
        return Metadata(
            metadataId = metadataId,
            contentId = contentId,
            key = key,
            value = value
        )
    }
    
    /**
     * Create mock embedding data (384 dimensions for all-MiniLM-L6-v2)
     */
    fun createMockEmbedding(
        embeddingId: Int = Random.nextInt(10000, 99999),
        contentId: Int = 1
    ): Embedding {
        val floatArray = FloatArray(384) { Random.nextFloat() }
        val byteArray = floatArrayToByteArray(floatArray)
        
        return Embedding(
            embeddingId = embeddingId,
            contentId = contentId,
            embedding = byteArray
        )
    }
    
    // ==================== CUSTOM ASSERTIONS ====================
    
    /**
     * Assert that two content objects are equal (ignoring IDs)
     */
    fun assertContentEquals(expected: Content, actual: Content, message: String = "") {
        assertEquals("$message - Section ID should match", expected.sectionId, actual.sectionId)
        assertEquals("$message - Page number should match", expected.pageNumber, actual.pageNumber)
        assertEquals("$message - Content type should match", expected.contentType, actual.contentType)
        assertEquals("$message - Content text should match", expected.contentText, actual.contentText)
    }
    
    /**
     * Assert that a list contains content with specific text
     */
    fun assertContainsText(contents: List<Content>, searchText: String, ignoreCase: Boolean = true) {
        val found = contents.any { content ->
            content.contentText.contains(searchText, ignoreCase)
        }
        assertTrue(
            "Content list should contain text: $searchText",
            found
        )
    }
    
    /**
     * Assert that content is properly ordered by page number
     */
    fun assertOrderedByPage(contents: List<Content>) {
        for (i in 1 until contents.size) {
            assertTrue(
                "Content should be ordered by page (${contents[i-1].pageNumber} <= ${contents[i].pageNumber})",
                contents[i - 1].pageNumber <= contents[i].pageNumber
            )
        }
    }
    
    /**
     * Assert that all content has valid page numbers (29-692 for Ghana STG)
     */
    fun assertValidPageNumbers(contents: List<Content>) {
        contents.forEach { content ->
            assertTrue(
                "Page number ${content.pageNumber} should be between 29 and 692",
                content.pageNumber in 29..692
            )
        }
    }
    
    // ==================== PERFORMANCE HELPERS ====================
    
    /**
     * Measure query execution time with warm-up
     */
    suspend fun <T> measureQueryTime(
        warmupRuns: Int = 2,
        testRuns: Int = 5,
        query: suspend () -> T
    ): Pair<T?, Long> {
        // Warm up
        repeat(warmupRuns) {
            query()
        }
        
        // Measure
        var result: T? = null
        val times = mutableListOf<Long>()
        
        repeat(testRuns) {
            val time = measureTimeMillis {
                result = query()
            }
            times.add(time)
        }
        
        return result to times.average().toLong()
    }
    
    /**
     * Assert that a query completes within a time limit
     */
    suspend fun <T> assertCompletesWithin(
        timeMillis: Long,
        message: String = "Query should complete within ${timeMillis}ms",
        block: suspend () -> T
    ): T {
        val result: T
        val actualTime = measureTimeMillis {
            result = block()
        }
        
        assertTrue(
            "$message (actual: ${actualTime}ms)",
            actualTime <= timeMillis
        )
        
        return result
    }
    
    // ==================== COROUTINE/FLOW HELPERS ====================
    
    /**
     * Wait for a Flow to emit with timeout
     */
    suspend fun <T> waitForFlow(
        flow: Flow<T>,
        timeoutMillis: Long = 5000,
        message: String = "Flow should emit within ${timeoutMillis}ms"
    ): T {
        return withTimeout(timeoutMillis) {
            try {
                flow.first()
            } catch (e: Exception) {
                fail("$message - Exception: ${e.message}")
                throw e
            }
        }
    }
    
    // ==================== MEDICAL CONTENT HELPERS ====================
    
    /**
     * Common medical terms for testing
     */
    val commonMedicalTerms = listOf(
        "malaria", "fever", "diarrhea", "diarrhoea", "hypertension",
        "diabetes", "pregnancy", "pediatric", "treatment", "diagnosis",
        "medication", "dosage", "symptoms", "emergency", "infection"
    )
    
    /**
     * Generate a random medical query
     */
    fun generateMedicalQuery(): String {
        val terms = commonMedicalTerms.shuffled().take(Random.nextInt(1, 4))
        return terms.joinToString(" ")
    }
    
    /**
     * Check if content is medically relevant to a query
     */
    fun isMedicallyRelevant(content: Content, query: String): Boolean {
        val queryTerms = query.lowercase().split("\\s+".toRegex())
        val contentLower = content.contentText.lowercase()
        
        return queryTerms.any { term ->
            contentLower.contains(term) ||
            // Check for common medical synonyms
            when (term) {
                "fever" -> contentLower.contains("pyrexia") || contentLower.contains("temperature")
                "child", "children" -> contentLower.contains("pediatric") || contentLower.contains("paediatric")
                "medicine" -> contentLower.contains("medication") || contentLower.contains("drug")
                else -> false
            }
        }
    }
    
    // ==================== DATA CONVERSION HELPERS ====================
    
    /**
     * Convert float array to byte array for embedding storage
     */
    fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val bytes = ByteArray(floats.size * 4)
        for (i in floats.indices) {
            val bits = java.lang.Float.floatToIntBits(floats[i])
            bytes[i * 4] = (bits and 0xFF).toByte()
            bytes[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        return bytes
    }
    
    /**
     * Convert byte array to float array for embedding processing
     */
    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Invalid byte array size for float conversion" }
        
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) {
            val bits = (bytes[i * 4].toInt() and 0xFF) or
                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
            floats[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return floats
    }
    
    // ==================== DATABASE VALIDATION HELPERS ====================
    
    /**
     * Validate database statistics match expected values
     */
    fun validateDatabaseStats(
        chapterCount: Int,
        sectionCount: Int,
        contentCount: Int,
        metadataCount: Int,
        embeddingCount: Int
    ) {
        assertEquals("Chapter count should be correct", 23, chapterCount)
        assertEquals("Section count should be correct", 831, sectionCount)
        assertEquals("Content count should be correct", 664, contentCount)
        assertEquals("Metadata count should be correct", 957, metadataCount)
        assertEquals("Embedding count should be correct", 664, embeddingCount)
    }
    
    /**
     * Check if content types are valid
     */
    fun isValidContentType(type: String): Boolean {
        return type in listOf("paragraph", "bullet", "table", "note")
    }
    
    // ==================== TEST DATA SAMPLES ====================
    
    /**
     * Sample Ghana STG content for testing
     */
    val sampleMedicalQueries = listOf(
        "malaria treatment children",
        "oral rehydration therapy dosage",
        "hypertension management elderly",
        "pediatric fever emergency",
        "diabetes medication pregnancy",
        "bacterial infection antibiotics",
        "diarrhea dehydration symptoms",
        "pneumonia diagnosis treatment"
    )
    
    /**
     * Expected content types in Ghana STG database
     */
    val expectedContentTypes = listOf("paragraph", "bullet", "table", "note")
    
    /**
     * Valid metadata keys in the database
     */
    val validMetadataKeys = listOf(
        "target_population",
        "severity",
        "treatment_type",
        "evidence_level",
        "urgency"
    )
}