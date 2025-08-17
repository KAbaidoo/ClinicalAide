# PDF Parsing Implementation Guide

## Overview

This document provides a comprehensive guide for parsing the Ghana Standard Treatment Guidelines (STG) 7th Edition PDF into the structured database schema. The parsing process converts 708 pages of clinical content into a queryable format optimized for semantic search and LLM context generation.

## Parsing Strategy

### Multi-Phase Approach

The parsing process is divided into sequential phases to handle the complexity and size of the document:

1. **Document Structure Analysis** - Extract overall document organization
2. **Chapter Parsing** - Identify and extract chapter boundaries  
3. **Condition Parsing** - Extract individual medical conditions
4. **Content Block Parsing** - Parse structured content within each condition
5. **Medication Extraction** - Extract specific medication information
6. **Embedding Generation** - Create vector embeddings for semantic search

## Phase 1: Document Structure Analysis

### Table of Contents Extraction

```kotlin
class StgPdfParser(private val pdfPath: String, private val database: StgDatabase) {
    
    private val dao = database.stgDao()
    
    suspend fun parseDocument() {
        val pdf = PDDocument.load(File(pdfPath))
        
        try {
            // Phase 1: Extract structure
            val documentStructure = analyzeDocumentStructure(pdf)
            
            // Phase 2-6: Sequential parsing phases
            parseChapters(pdf, documentStructure)
            parseConditions(pdf, documentStructure)
            parseContentBlocks(pdf, documentStructure)
            extractMedications(pdf, documentStructure)
            generateEmbeddings()
            
        } finally {
            pdf.close()
        }
    }
}

data class DocumentStructure(
    val tocPages: IntRange,
    val chapterStarts: Map<Int, Int>, // chapter number to start page
    val conditionMappings: Map<String, ConditionLocation>
)

data class ConditionLocation(
    val chapterNumber: Int,
    val conditionNumber: Int,
    val conditionName: String,
    val startPage: Int,
    val endPage: Int
)
```

### TOC Analysis Implementation

```kotlin
private suspend fun analyzeDocumentStructure(pdf: PDDocument): DocumentStructure {
    val tocPages = findTableOfContentsPages(pdf) // Pages 3-10 based on analysis
    val structure = extractStructureFromTOC(pdf, tocPages)
    
    return structure
}

private fun findTableOfContentsPages(pdf: PDDocument): IntRange {
    for (pageNum in 2..15) { // Check pages 3-16
        val pageText = extractPageText(pdf, pageNum)
        if (pageText.contains("Table of Contents", ignoreCase = true)) {
            // Find where TOC ends
            var endPage = pageNum
            for (nextPage in pageNum + 1..20) {
                val nextText = extractPageText(pdf, nextPage)
                if (nextText.contains("Introduction") || nextText.contains("Chapter 1")) {
                    endPage = nextPage - 1
                    break
                }
            }
            return pageNum..endPage
        }
    }
    return 3..10 // Fallback based on analysis
}

private fun extractPageText(pdf: PDDocument, pageNum: Int): String {
    return try {
        val stripper = PDFTextStripper().apply {
            startPage = pageNum + 1 // PDFBox uses 1-based indexing
            endPage = pageNum + 1
        }
        stripper.getText(pdf)
    } catch (e: Exception) {
        ""
    }
}
```

### Structure Extraction from TOC

```kotlin
private fun extractStructureFromTOC(pdf: PDDocument, tocPages: IntRange): DocumentStructure {
    val chapterStarts = mutableMapOf<Int, Int>()
    val conditionMappings = mutableMapOf<String, ConditionLocation>()
    
    val chapterPattern = Regex("""Chapter\s+(\d+)\.\s+(.+?)\.{3,}(\d+)""")
    val conditionPattern = Regex("""(\d+)\.\s+(.+?)\.{3,}(\d+)""")
    
    tocPages.forEach { pageNum ->
        val pageText = extractPageText(pdf, pageNum)
        
        // Extract chapters
        chapterPattern.findAll(pageText).forEach { match ->
            val chapterNum = match.groupValues[1].toInt()
            val startPage = match.groupValues[3].toInt()
            chapterStarts[chapterNum] = startPage
        }
        
        // Extract conditions with chapter context
        var currentChapter = 1
        conditionPattern.findAll(pageText).forEach { match ->
            val conditionNum = match.groupValues[1].toInt()
            val conditionName = cleanConditionName(match.groupValues[2])
            val startPage = match.groupValues[3].toInt()
            
            // Determine which chapter this condition belongs to
            currentChapter = determineChapterForCondition(chapterStarts, startPage)
            
            val endPage = findConditionEndPage(pdf, startPage, conditionNum)
            
            conditionMappings[conditionName] = ConditionLocation(
                chapterNumber = currentChapter,
                conditionNumber = conditionNum,
                conditionName = conditionName,
                startPage = startPage,
                endPage = endPage
            )
        }
    }
    
    return DocumentStructure(tocPages, chapterStarts, conditionMappings)
}

private fun cleanConditionName(rawName: String): String {
    return rawName
        .replace(Regex("""\.{3,}.*"""), "") // Remove dots and page numbers
        .trim()
        .replace(Regex("""\s+"""), " ") // Normalize whitespace
}
```

## Phase 2: Chapter Parsing

```kotlin
private suspend fun parseChapters(pdf: PDDocument, structure: DocumentStructure) {
    structure.chapterStarts.forEach { (chapterNum, startPage) ->
        val endPage = structure.chapterStarts[chapterNum + 1] ?: pdf.numberOfPages
        
        val chapterTitle = findChapterTitle(pdf, startPage, chapterNum)
        val description = extractChapterDescription(pdf, startPage)
        
        val chapter = StgChapter(
            chapterNumber = chapterNum,
            chapterTitle = chapterTitle,
            startPage = startPage,
            endPage = endPage - 1,
            description = description
        )
        
        dao.insertChapter(chapter)
    }
}

private fun findChapterTitle(pdf: PDDocument, startPage: Int, chapterNum: Int): String {
    // Look for chapter title patterns around the start page
    val titlePatterns = listOf(
        Regex("""Chapter\s+$chapterNum[:\.]?\s*(.+?)(?:\n|$)"""),
        Regex("""$chapterNum\.\s*(.+?)(?:\n|$)"""),
        Regex("""(.+?)\s*Chapter\s+$chapterNum""")
    )
    
    for (pageOffset in -1..2) {
        val pageText = extractPageText(pdf, startPage + pageOffset)
        titlePatterns.forEach { pattern ->
            pattern.find(pageText)?.let { match ->
                return cleanTitle(match.groupValues[1])
            }
        }
    }
    
    return "Chapter $chapterNum" // Fallback
}

private fun cleanTitle(title: String): String {
    return title
        .replace(Regex("""^\d+\.\s*"""), "") // Remove leading numbers
        .replace(Regex("""\s+"""), " ") // Normalize whitespace
        .trim()
}
```

## Phase 3: Condition Parsing

```kotlin
private suspend fun parseConditions(pdf: PDDocument, structure: DocumentStructure) {
    structure.conditionMappings.forEach { (_, conditionLocation) ->
        
        val chapter = dao.getAllChapters()
            .find { it.chapterNumber == conditionLocation.chapterNumber }
            ?: return@forEach
        
        val condition = StgCondition(
            chapterId = chapter.id,
            conditionNumber = conditionLocation.conditionNumber,
            conditionName = conditionLocation.conditionName,
            startPage = conditionLocation.startPage,
            endPage = conditionLocation.endPage,
            keywords = generateKeywords(conditionLocation.conditionName)
        )
        
        dao.insertCondition(condition)
    }
}

private fun generateKeywords(conditionName: String): String {
    val baseKeywords = conditionName.lowercase()
        .split(Regex("""[,\s\-]+"""))
        .filter { it.length > 2 }
    
    val medicalVariants = generateMedicalVariants(conditionName)
    val allKeywords = (baseKeywords + medicalVariants).distinct()
    
    return Gson().toJson(allKeywords)
}

private fun generateMedicalVariants(conditionName: String): List<String> {
    val variants = mutableListOf<String>()
    
    // Add common medical synonyms
    val synonymMap = mapOf(
        "diarrhoea" to listOf("diarrhea", "loose stools", "gastroenteritis"),
        "hypertension" to listOf("high blood pressure", "elevated bp"),
        "diabetes" to listOf("diabetes mellitus", "dm", "high blood sugar"),
        // Add more medical synonyms as needed
    )
    
    synonymMap.forEach { (term, synonyms) ->
        if (conditionName.contains(term, ignoreCase = true)) {
            variants.addAll(synonyms)
        }
    }
    
    return variants
}
```

## Phase 4: Content Block Parsing

### Content Type Pattern Matching

```kotlin
private suspend fun parseContentBlocks(pdf: PDDocument, structure: DocumentStructure) {
    structure.conditionMappings.forEach { (_, conditionLocation) ->
        val condition = dao.getConditionsByChapter(conditionLocation.chapterNumber)
            .find { it.conditionNumber == conditionLocation.conditionNumber }
            ?: return@forEach
        
        val contentBlocks = extractContentBlocks(pdf, condition, conditionLocation)
        dao.insertContentBlocks(contentBlocks)
    }
}

private fun extractContentBlocks(
    pdf: PDDocument, 
    condition: StgCondition, 
    location: ConditionLocation
): List<StgContentBlock> {
    
    val contentBlocks = mutableListOf<StgContentBlock>()
    var orderCounter = 1
    
    // Combine text from all pages for this condition
    val fullConditionText = buildString {
        for (pageNum in location.startPage..location.endPage) {
            val pageText = extractPageText(pdf, pageNum)
            append(cleanPageText(pageText))
            append("\n")
        }
    }
    
    // Parse different content types using patterns
    val parsedBlocks = parseContentTypes(fullConditionText, location.startPage, condition.id, orderCounter)
    contentBlocks.addAll(parsedBlocks)
    
    return contentBlocks
}
```

### Content Pattern Definitions

```kotlin
private fun parseContentTypes(
    conditionText: String, 
    startPageNumber: Int, 
    conditionId: Long, 
    startOrder: Int
): List<StgContentBlock> {
    
    val blocks = mutableListOf<StgContentBlock>()
    var currentOrder = startOrder
    
    // Define content section patterns based on Ghana STG structure
    val contentPatterns = mapOf(
        "definition" to listOf(
            Regex("""(.+?)\s+is\s+defined\s+as\s+(.+?)(?=\n\n|Causes|Treatment|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Definition[:\s]*(.+?)(?=\n\n|Causes|Treatment|$)""", RegexOption.DOT_MATCHES_ALL)
        ),
        "causes" to listOf(
            Regex("""Causes[:\s]*(.+?)(?=\n\n|Clinical|Treatment|Symptoms|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Aetiology[:\s]*(.+?)(?=\n\n|Clinical|Treatment|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Acute\s+diarrhoea[:\s]*(.+?)(?=\n\n|Chronic|Treatment|$)""", RegexOption.DOT_MATCHES_ALL)
        ),
        "symptoms" to listOf(
            Regex("""Clinical\s+presentation[:\s]*(.+?)(?=\n\n|Treatment|Diagnosis|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Symptoms?[:\s]*(.+?)(?=\n\n|Treatment|Diagnosis|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Signs?\s+and\s+symptoms?[:\s]*(.+?)(?=\n\n|Treatment|$)""", RegexOption.DOT_MATCHES_ALL)
        ),
        "treatment" to listOf(
            Regex("""Treatment[:\s]*(.+?)(?=\n\n|Dosage|Dose|Refer|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Management[:\s]*(.+?)(?=\n\n|Dosage|Dose|Refer|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Therapy[:\s]*(.+?)(?=\n\n|Dosage|Dose|Refer|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""First.line\s+treatment[:\s]*(.+?)(?=\n\n|Dosage|$)""", RegexOption.DOT_MATCHES_ALL)
        ),
        "dosage" to listOf(
            Regex("""Dosage?[:\s]*(.+?)(?=\n\n|Refer|Side\s+effects|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Dose[:\s]*(.+?)(?=\n\n|Refer|Side\s+effects|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""ORS\s+dosage[:\s]*(.+?)(?=\n\n|Refer|$)""", RegexOption.DOT_MATCHES_ALL)
        ),
        "referral" to listOf(
            Regex("""Refer[:\s]*(.+?)(?=\n\n|Prevention|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""Referral\s+criteria[:\s]*(.+?)(?=\n\n|Prevention|$)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""When\s+to\s+refer[:\s]*(.+?)(?=\n\n|Prevention|$)""", RegexOption.DOT_MATCHES_ALL)
        )
    )
    
    contentPatterns.forEach { (blockType, patterns) ->
        patterns.forEach { pattern ->
            pattern.findAll(conditionText).forEach { match ->
                val content = cleanContent(match.groupValues[1])
                if (content.isNotBlank() && content.length > 20) { // Filter too short content
                    
                    val clinicalContext = determineClinicalContext(content)
                    val severityLevel = determineSeverityLevel(content)
                    val evidenceLevel = extractEvidenceLevel(content)
                    
                    blocks.add(
                        StgContentBlock(
                            conditionId = conditionId,
                            blockType = blockType,
                            content = content,
                            pageNumber = startPageNumber,
                            orderInCondition = currentOrder++,
                            clinicalContext = clinicalContext,
                            severityLevel = severityLevel,
                            evidenceLevel = evidenceLevel,
                            keywords = generateContentKeywords(content, blockType)
                        )
                    )
                }
            }
        }
    }
    
    return blocks
}
```

## Content Processing Helpers

```kotlin
private fun cleanPageText(rawText: String): String {
    return rawText
        .replace(Regex("""Standard Treatment Guidelines, 7th Edition, 2017"""), "")
        .replace(Regex("""\d+\s*$"""), "") // Remove page numbers at end
        .replace(Regex("""^[ivx]+\s*$"""), "") // Remove roman numerals
        .replace(Regex("""\s+"""), " ") // Normalize whitespace
        .trim()
}

private fun cleanContent(content: String): String {
    return content
        .replace(Regex("""[•·▪▫]\s*"""), "- ") // Standardize bullets
        .replace(Regex("""\s+"""), " ") // Normalize whitespace
        .replace(Regex("""^\s*[:\-•]\s*"""), "") // Remove leading punctuation
        .trim()
}

private fun determineClinicalContext(content: String): String {
    val contentLower = content.lowercase()
    
    return when {
        contentLower.contains(Regex("""child|children|pediatric|paediatric|infant""")) -> "pediatric"
        contentLower.contains(Regex("""neonate|newborn|neonatal""")) -> "neonatal"
        contentLower.contains(Regex("""pregnancy|pregnant|prenatal""")) -> "pregnancy"
        contentLower.contains(Regex("""elderly|geriatric|old""")) -> "elderly"
        contentLower.contains(Regex("""emergency|urgent|immediate|severe""")) -> "emergency"
        contentLower.contains(Regex("""adult""")) -> "adult"
        else -> "general"
    }
}

private fun determineSeverityLevel(content: String): String? {
    val contentLower = content.lowercase()
    
    return when {
        contentLower.contains(Regex("""severe|critical|life.threatening""")) -> "severe"
        contentLower.contains(Regex("""moderate""")) -> "moderate"
        contentLower.contains(Regex("""mild|minor""")) -> "mild"
        else -> null
    }
}

private fun extractEvidenceLevel(content: String): String? {
    val evidencePattern = Regex("""Evidence\s+[Ll]evel\s+([ABC])""")
    return evidencePattern.find(content)?.groupValues?.get(1)
}

private fun generateContentKeywords(content: String, blockType: String): String {
    val medicalTerms = extractMedicalTerms(content)
    val dosageTerms = if (blockType == "dosage") extractDosageTerms(content) else emptyList()
    val allKeywords = (medicalTerms + dosageTerms + blockType).distinct()
    
    return Gson().toJson(allKeywords)
}
```

## Phase 5: Medication Extraction

```kotlin
private suspend fun extractMedications(pdf: PDDocument, structure: DocumentStructure) {
    structure.conditionMappings.forEach { (_, conditionLocation) ->
        val condition = dao.getConditionsByChapter(conditionLocation.chapterNumber)
            .find { it.conditionNumber == conditionLocation.conditionNumber }
            ?: return@forEach
        
        val medications = extractMedicationsFromCondition(pdf, condition, conditionLocation)
        dao.insertMedications(medications)
    }
}

private fun extractMedicationsFromCondition(
    pdf: PDDocument,
    condition: StgCondition,
    location: ConditionLocation
): List<StgMedication> {
    
    val medications = mutableListOf<StgMedication>()
    
    for (pageNum in location.startPage..location.endPage) {
        val pageText = extractPageText(pdf, pageNum)
        val pageContent = cleanPageText(pageText)
        
        val pageMedications = extractMedicationsFromText(pageContent, condition.id, pageNum)
        medications.addAll(pageMedications)
    }
    
    return medications.distinctBy { "${it.medicationName}-${it.dosage}-${it.ageGroup}" }
}

private fun extractMedicationsFromText(
    content: String, 
    conditionId: Long, 
    pageNumber: Int
): List<StgMedication> {
    
    val medications = mutableListOf<StgMedication>()
    
    // Pattern for medication with dosage
    val medicationPattern = Regex(
        """([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)\s*:?\s*(\d+(?:\.\d+)?)\s*(mg|g|ml|mcg|IU)(?:/kg)?\s*(?:every|q)?\s*(\d+)?\s*(hours?|hrs?|times?\s+daily|daily|bd|tds|qds)?""",
        RegexOption.IGNORE_CASE
    )
    
    medicationPattern.findAll(content).forEach { match ->
        val medicationName = match.groupValues[1].trim()
        val dose = match.groupValues[2]
        val unit = match.groupValues[3]
        val frequency = "${match.groupValues[4]} ${match.groupValues[5]}".trim()
        
        val ageGroup = determineClinicalContext(content)
        val weightBased = content.contains("/kg", ignoreCase = true)
        
        medications.add(
            StgMedication(
                conditionId = conditionId,
                medicationName = medicationName,
                dosage = "$dose$unit",
                frequency = frequency.ifBlank { "as directed" },
                duration = extractDuration(content),
                route = extractRoute(content),
                ageGroup = if (ageGroup == "general") "adult" else ageGroup,
                weightBased = weightBased,
                pageNumber = pageNumber
            )
        )
    }
    
    return medications
}

private fun extractDuration(content: String): String {
    val durationPattern = Regex("""for\s+(\d+)\s*(days?|weeks?|months?)""", RegexOption.IGNORE_CASE)
    return durationPattern.find(content)?.groupValues?.joinToString(" ") ?: "as needed"
}

private fun extractRoute(content: String): String {
    val routePattern = Regex("""(oral|IV|IM|topical|rectal|sublingual)""", RegexOption.IGNORE_CASE)
    return routePattern.find(content)?.groupValues?.get(1)?.lowercase() ?: "oral"
}
```

## Phase 6: Embedding Generation

```kotlin
private suspend fun generateEmbeddings() {
    val contentBlocks = dao.getAllContentBlocks()
    val embeddingService = LocalEmbeddingService() // Your embedding service
    
    contentBlocks.chunked(10).forEach { batch ->
        val embeddings = batch.map { block ->
            val embeddingText = "${block.blockType} ${block.content}"
            val vector = embeddingService.generateEmbedding(embeddingText)
            
            StgEmbedding(
                contentBlockId = block.id,
                embedding = Gson().toJson(vector),
                embeddingModel = "local-use-v1",
                embeddingDimensions = vector.size
            )
        }
        
        dao.insertEmbeddings(embeddings)
        delay(100) // Rate limiting
    }
}
```

## Usage Example

```kotlin
class StgParsingService(private val context: Context) {
    
    suspend fun parseStgDocument(pdfPath: String) {
        val database = StgDatabase.getDatabase(context)
        val parser = StgPdfParser(pdfPath, database)
        
        try {
            parser.parseDocument()
            Log.i("StgParser", "Successfully parsed STG document")
        } catch (e: Exception) {
            Log.e("StgParser", "Error parsing document: ${e.message}")
            throw e
        }
    }
}
```

## Error Handling and Validation

```kotlin
class ContentValidator {
    
    fun validateContentBlock(block: StgContentBlock): Boolean {
        return block.content.isNotBlank() &&
                block.content.length > 10 &&
                block.blockType in validBlockTypes &&
                block.pageNumber > 0
    }
    
    fun validateMedication(medication: StgMedication): Boolean {
        return medication.medicationName.isNotBlank() &&
                medication.dosage.isNotBlank() &&
                medication.ageGroup in validAgeGroups
    }
    
    companion object {
        val validBlockTypes = setOf(
            "definition", "causes", "symptoms", "treatment", 
            "dosage", "referral", "contraindications"
        )
        
        val validAgeGroups = setOf(
            "adult", "pediatric", "neonatal", "elderly"
        )
    }
}
```

## Performance Optimizations

```kotlin
class OptimizedPdfParser {
    
    // Use coroutines for parallel processing
    suspend fun parseInParallel(structure: DocumentStructure) = coroutineScope {
        val chapterJobs = structure.chapterStarts.map { (chapterNum, _) ->
            async { parseChapterConditions(chapterNum) }
        }
        
        chapterJobs.awaitAll()
    }
    
    // Cache frequently used patterns
    private val patternCache = mutableMapOf<String, Regex>()
    
    private fun getCachedPattern(pattern: String): Regex {
        return patternCache.getOrPut(pattern) { Regex(pattern) }
    }
    
    // Batch database operations
    suspend fun insertInBatches(contentBlocks: List<StgContentBlock>) {
        contentBlocks.chunked(50).forEach { batch ->
            dao.insertContentBlocks(batch)
        }
    }
}
```

This comprehensive parsing implementation provides a robust foundation for converting the Ghana STG PDF into a structured, searchable database while maintaining clinical accuracy and supporting advanced AI features.
