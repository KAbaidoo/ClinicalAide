package co.kobby.clinicalaide.data

import co.kobby.clinicalaide.data.database.entities.*

/**
 * Factory class for generating consistent test data across all database tests.
 * Provides builder patterns and default values for easy test data creation.
 */
object TestDataFactory {
    
    // ==================== CHAPTERS ====================
    
    fun createChapter(
        id: Long = 0,
        chapterNumber: Int = 1,
        chapterTitle: String = "Gastrointestinal Disorders",
        startPage: Int = 29,
        endPage: Int = 57,
        description: String? = "Common GI tract disorders and their management"
    ) = StgChapter(
        id = id,
        chapterNumber = chapterNumber,
        chapterTitle = chapterTitle,
        startPage = startPage,
        endPage = endPage,
        description = description
    )
    
    fun createChapters(count: Int = 3): List<StgChapter> {
        return (1..count).map { i ->
            createChapter(
                chapterNumber = i,
                chapterTitle = "Chapter $i",
                startPage = (i - 1) * 50 + 1,
                endPage = i * 50
            )
        }
    }
    
    // ==================== CONDITIONS ====================
    
    fun createCondition(
        id: Long = 0,
        chapterId: Long,
        conditionNumber: Int = 1,
        conditionName: String = "Diarrhoea",
        startPage: Int = 29,
        endPage: Int = 32,
        keywords: String = "[\"diarrhoea\", \"gastroenteritis\", \"dehydration\"]"
    ) = StgCondition(
        id = id,
        chapterId = chapterId,
        conditionNumber = conditionNumber,
        conditionName = conditionName,
        startPage = startPage,
        endPage = endPage,
        keywords = keywords
    )
    
    fun createConditions(chapterId: Long, count: Int = 3): List<StgCondition> {
        val conditionNames = listOf("Diarrhoea", "Constipation", "Peptic Ulcer", "Gastritis", "Appendicitis")
        return (1..count).map { i ->
            createCondition(
                chapterId = chapterId,
                conditionNumber = i,
                conditionName = conditionNames.getOrElse(i - 1) { "Condition $i" },
                startPage = 29 + (i - 1) * 5,
                endPage = 29 + i * 5 - 1
            )
        }
    }
    
    // ==================== CONTENT BLOCKS ====================
    
    fun createContentBlock(
        id: Long = 0,
        conditionId: Long,
        blockType: String = "treatment",
        content: String = "Standard treatment protocol for this condition",
        pageNumber: Int = 30,
        orderInCondition: Int = 1,
        clinicalContext: String = "general",
        severityLevel: String? = null,
        evidenceLevel: String? = null,
        keywords: String = "[\"treatment\", \"protocol\"]",
        relatedBlockIds: String = "[]"
    ) = StgContentBlock(
        id = id,
        conditionId = conditionId,
        blockType = blockType,
        content = content,
        pageNumber = pageNumber,
        orderInCondition = orderInCondition,
        clinicalContext = clinicalContext,
        severityLevel = severityLevel,
        evidenceLevel = evidenceLevel,
        keywords = keywords,
        relatedBlockIds = relatedBlockIds
    )
    
    fun createContentBlocks(conditionId: Long, count: Int = 5): List<StgContentBlock> {
        val blockTypes = listOf("definition", "causes", "symptoms", "diagnosis", "treatment")
        val contents = mapOf(
            "definition" to "Medical definition and classification of the condition",
            "causes" to "Common causes and risk factors for developing this condition",
            "symptoms" to "Clinical presentation and signs to look for",
            "diagnosis" to "Diagnostic criteria and required investigations",
            "treatment" to "Evidence-based treatment protocols and management"
        )
        
        return blockTypes.take(count).mapIndexed { index, type ->
            createContentBlock(
                conditionId = conditionId,
                blockType = type,
                content = contents[type] ?: "Content for $type",
                orderInCondition = index + 1,
                pageNumber = 30 + index
            )
        }
    }
    
    // ==================== EMBEDDINGS ====================
    
    fun createEmbedding(
        id: Long = 0,
        contentBlockId: Long,
        embedding: String = "[0.1, 0.2, 0.3, 0.4, 0.5]",
        embeddingModel: String = "text-embedding-004",
        embeddingDimensions: Int = 768
    ) = StgEmbedding(
        id = id,
        contentBlockId = contentBlockId,
        embedding = embedding,
        embeddingModel = embeddingModel,
        embeddingDimensions = embeddingDimensions
    )
    
    fun createRandomEmbedding(contentBlockId: Long, dimensions: Int = 768): StgEmbedding {
        val values = (1..dimensions).map { (it * 0.001).toFloat() }
        val embeddingJson = values.joinToString(", ", "[", "]")
        return createEmbedding(
            contentBlockId = contentBlockId,
            embedding = embeddingJson,
            embeddingDimensions = dimensions
        )
    }
    
    // ==================== MEDICATIONS ====================
    
    fun createMedication(
        id: Long = 0,
        conditionId: Long,
        medicationName: String = "Paracetamol",
        dosage: String = "500mg",
        frequency: String = "QID",
        duration: String = "3 days",
        route: String = "oral",
        ageGroup: String = "adult",
        weightBased: Boolean = false,
        contraindications: String? = null,
        sideEffects: String? = null,
        evidenceLevel: String? = "A",
        pageNumber: Int = 31
    ) = StgMedication(
        id = id,
        conditionId = conditionId,
        medicationName = medicationName,
        dosage = dosage,
        frequency = frequency,
        duration = duration,
        route = route,
        ageGroup = ageGroup,
        weightBased = weightBased,
        contraindications = contraindications,
        sideEffects = sideEffects,
        evidenceLevel = evidenceLevel,
        pageNumber = pageNumber
    )
    
    fun createMedications(conditionId: Long, count: Int = 3): List<StgMedication> {
        val medications = listOf(
            Triple("Paracetamol", "500mg", "adult"),
            Triple("Paracetamol Syrup", "250mg/5ml", "pediatric"),
            Triple("ORS", "75ml/kg", "pediatric"),
            Triple("Loperamide", "4mg", "adult"),
            Triple("Ciprofloxacin", "500mg", "adult")
        )
        
        return medications.take(count).mapIndexed { index, (name, dose, age) ->
            createMedication(
                conditionId = conditionId,
                medicationName = name,
                dosage = dose,
                ageGroup = age,
                weightBased = age == "pediatric" && name == "ORS",
                pageNumber = 31 + index
            )
        }
    }
    
    // ==================== CROSS REFERENCES ====================
    
    fun createCrossReference(
        id: Long = 0,
        fromConditionId: Long,
        toConditionId: Long,
        referenceType: String = "see_also",
        description: String? = "Related condition for differential diagnosis"
    ) = StgCrossReference(
        id = id,
        fromConditionId = fromConditionId,
        toConditionId = toConditionId,
        referenceType = referenceType,
        description = description
    )
    
    // ==================== SEARCH CACHE ====================
    
    fun createSearchCache(
        queryHash: String = "hash_${System.currentTimeMillis()}",
        results: String = "[{\"id\": 1, \"score\": 0.95}]",
        timestamp: Long = System.currentTimeMillis(),
        hitCount: Int = 1
    ) = StgSearchCache(
        queryHash = queryHash,
        results = results,
        timestamp = timestamp,
        hitCount = hitCount
    )
    
    // ==================== COMPLETE HIERARCHY ====================
    
    /**
     * Creates a complete hierarchy for testing:
     * Chapter -> Conditions -> ContentBlocks -> Embeddings & Medications
     */
    data class CompleteHierarchy(
        val chapter: StgChapter,
        val conditions: List<StgCondition>,
        val contentBlocks: Map<Long, List<StgContentBlock>>, // conditionId -> blocks
        val embeddings: Map<Long, StgEmbedding>, // contentBlockId -> embedding
        val medications: Map<Long, List<StgMedication>> // conditionId -> medications
    )
    
    fun createCompleteHierarchy(
        chapterNumber: Int = 1,
        conditionCount: Int = 2,
        blocksPerCondition: Int = 3,
        medicationsPerCondition: Int = 2,
        includeEmbeddings: Boolean = true
    ): CompleteHierarchy {
        val chapter = createChapter(chapterNumber = chapterNumber)
        
        // Create conditions - they will have id=0 initially
        val conditions = (1..conditionCount).map { i ->
            createCondition(
                id = 0, // Will be auto-generated
                chapterId = 0, // Will be updated when inserting
                conditionNumber = i,
                conditionName = "Condition $i"
            )
        }
        
        val contentBlocks = mutableMapOf<Long, List<StgContentBlock>>()
        val embeddings = mutableMapOf<Long, StgEmbedding>()
        val medications = mutableMapOf<Long, List<StgMedication>>()
        
        // Use index as temporary ID for mapping
        conditions.forEachIndexed { index, _ ->
            val tempId = index.toLong()
            val blockTypes = listOf("definition", "causes", "symptoms", "diagnosis", "treatment")
            val blocks = (1..blocksPerCondition).map { blockNum ->
                createContentBlock(
                    id = 0,
                    conditionId = 0, // Will be updated when inserting
                    blockType = blockTypes[(blockNum - 1) % blockTypes.size],
                    orderInCondition = blockNum
                )
            }
            contentBlocks[tempId] = blocks
            
            if (includeEmbeddings) {
                blocks.forEachIndexed { blockIndex, _ ->
                    val tempBlockId = (index * 100 + blockIndex).toLong()
                    embeddings[tempBlockId] = createEmbedding(contentBlockId = 0)
                }
            }
            
            val meds = (1..medicationsPerCondition).map { medNum ->
                createMedication(
                    id = 0,
                    conditionId = 0, // Will be updated when inserting
                    medicationName = "Medication $medNum"
                )
            }
            medications[tempId] = meds
        }
        
        return CompleteHierarchy(
            chapter = chapter,
            conditions = conditions,
            contentBlocks = contentBlocks,
            embeddings = embeddings,
            medications = medications
        )
    }
    
    // ==================== VALIDATION DATA ====================
    
    object ValidValues {
        val blockTypes = listOf(
            "definition", "causes", "symptoms", "treatment", 
            "dosage", "referral", "contraindications", "diagnosis"
        )
        
        val clinicalContexts = listOf(
            "general", "pediatric", "adult", "pregnancy", 
            "elderly", "neonatal", "emergency"
        )
        
        val severityLevels = listOf("mild", "moderate", "severe")
        
        val evidenceLevels = listOf("A", "B", "C")
        
        val referenceTypes = listOf(
            "see_also", "differential", "complication", "prerequisite"
        )
        
        val routes = listOf("oral", "IV", "IM", "topical", "rectal", "SC")
        
        val ageGroups = listOf("adult", "pediatric", "neonatal", "elderly")
    }
    
    object InvalidValues {
        const val invalidBlockType = "invalid_type"
        const val invalidContext = "invalid_context"
        const val invalidSeverity = "extreme"
        const val invalidEvidence = "D"
        const val invalidRoute = "telepathic"
        const val invalidAgeGroup = "alien"
        const val invalidReferenceType = "random"
    }
    
    object SpecialCharacters {
        const val withQuotes = "Patient's condition \"severe\""
        const val withNewlines = "Line 1\nLine 2\nLine 3"
        const val withTabs = "Column1\tColumn2\tColumn3"
        const val withUnicode = "Test with émojis 😊 and symbols ♥"
        const val sqlInjection = "'; DROP TABLE stg_chapters; --"
        const val jsonSpecial = "{\"key\": \"value with \\\"quotes\\\" and \\n newlines\"}"
    }
}