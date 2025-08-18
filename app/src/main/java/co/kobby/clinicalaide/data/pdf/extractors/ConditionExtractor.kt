package co.kobby.clinicalaide.data.pdf.extractors

import co.kobby.clinicalaide.data.database.entities.ContentType
import co.kobby.clinicalaide.data.pdf.models.ParsedChapter
import co.kobby.clinicalaide.data.pdf.models.ParsedCondition
import co.kobby.clinicalaide.data.pdf.models.ParsedContentBlock

class ConditionExtractor {
    
    companion object {
        // Updated patterns based on actual Ghana STG format
        // Example: "1. Diarrhoea" or "24. Measles"
        private val CONDITION_PATTERN = Regex(
            """(?i)^\s*(\d+)\.\s+([A-Za-z][A-Za-z\s\-]+)(?:\s*\(([A-Z]\d{2}(?:\.\d+)?)\))?$""",
            RegexOption.MULTILINE
        )
        
        // Alternative pattern for conditions with section numbers like "1.1 Acute Diarrhoea"
        private val SECTION_CONDITION_PATTERN = Regex(
            """(?i)^(\d+\.\d+)\s+(.+?)(?:\s*\(([A-Z]\d{2}(?:\.\d+)?)\))?$""",
            RegexOption.MULTILINE
        )
        
        private val ICD_CODE_PATTERN = Regex(
            """(?i)\(([A-Z]\d{2}(?:\.\d+)?)\)"""
        )
        
        private val CONTENT_SECTION_HEADERS = mapOf(
            "DEFINITION" to ContentType.DEFINITION,
            "CAUSES" to ContentType.CAUSES,
            "SIGNS AND SYMPTOMS" to ContentType.SYMPTOMS,
            "CLINICAL FEATURES" to ContentType.SYMPTOMS,
            "DIFFERENTIAL DIAGNOSIS" to ContentType.DIFFERENTIAL_DIAGNOSIS,
            "INVESTIGATIONS" to ContentType.INVESTIGATIONS,
            "TREATMENT" to ContentType.TREATMENT,
            "NON-PHARMACOLOGICAL" to ContentType.NON_PHARMACOLOGICAL,
            "PHARMACOLOGICAL" to ContentType.PHARMACOLOGICAL,
            "REFERRAL" to ContentType.REFERRAL,
            "PREVENTION" to ContentType.PREVENTION,
            "COMPLICATIONS" to ContentType.COMPLICATIONS,
            "PROGNOSIS" to ContentType.PROGNOSIS,
            "PATIENT EDUCATION" to ContentType.PATIENT_EDUCATION
        )
    }
    
    fun extractConditions(chapter: ParsedChapter): List<ParsedCondition> {
        val conditions = mutableListOf<ParsedCondition>()
        val lines = chapter.rawContent.lines()
        
        var currentConditionName = ""
        var currentIcdCode: String? = null
        var currentPageNumber = chapter.pageStart
        var currentContentBlocks = mutableListOf<ParsedContentBlock>()
        var currentBlockType: ContentType? = null
        var currentBlockContent = StringBuilder()
        var blockOrder = 0
        
        for (line in lines) {
            // Try both patterns
            val conditionMatch = CONDITION_PATTERN.find(line) ?: SECTION_CONDITION_PATTERN.find(line)
            
            if (conditionMatch != null) {
                if (currentConditionName.isNotEmpty()) {
                    if (currentBlockType != null && currentBlockContent.isNotEmpty()) {
                        currentContentBlocks.add(
                            ParsedContentBlock(
                                contentType = currentBlockType,
                                content = currentBlockContent.toString().trim(),
                                orderInCondition = blockOrder++
                            )
                        )
                    }
                    
                    conditions.add(
                        ParsedCondition(
                            name = currentConditionName,
                            icdCode = currentIcdCode,
                            pageNumber = currentPageNumber,
                            contentBlocks = currentContentBlocks.toList()
                        )
                    )
                }
                
                currentConditionName = conditionMatch.groupValues[2].trim()
                currentIcdCode = conditionMatch.groupValues[3].takeIf { it.isNotEmpty() }
                currentContentBlocks = mutableListOf()
                currentBlockType = null
                currentBlockContent = StringBuilder()
                blockOrder = 0
            } else {
                val headerType = findContentType(line)
                if (headerType != null) {
                    if (currentBlockType != null && currentBlockContent.isNotEmpty()) {
                        currentContentBlocks.add(
                            ParsedContentBlock(
                                contentType = currentBlockType,
                                content = currentBlockContent.toString().trim(),
                                orderInCondition = blockOrder++
                            )
                        )
                    }
                    currentBlockType = headerType
                    currentBlockContent = StringBuilder()
                } else if (currentBlockType != null) {
                    currentBlockContent.append(line).append("\n")
                }
                
                if (line.contains("Page", ignoreCase = true)) {
                    val pageMatch = Regex("""(?i)page\s+(\d+)""").find(line)
                    pageMatch?.let {
                        currentPageNumber = it.groupValues[1].toIntOrNull() ?: currentPageNumber
                    }
                }
            }
        }
        
        if (currentConditionName.isNotEmpty()) {
            if (currentBlockType != null && currentBlockContent.isNotEmpty()) {
                currentContentBlocks.add(
                    ParsedContentBlock(
                        contentType = currentBlockType,
                        content = currentBlockContent.toString().trim(),
                        orderInCondition = blockOrder
                    )
                )
            }
            
            conditions.add(
                ParsedCondition(
                    name = currentConditionName,
                    icdCode = currentIcdCode,
                    pageNumber = currentPageNumber,
                    contentBlocks = currentContentBlocks.toList()
                )
            )
        }
        
        return conditions
    }
    
    private fun findContentType(line: String): ContentType? {
        val upperLine = line.uppercase().trim()
        for ((header, type) in CONTENT_SECTION_HEADERS) {
            if (upperLine.startsWith(header) || upperLine == header) {
                return type
            }
        }
        return null
    }
    
    fun extractAlternativeNames(conditionText: String): List<String> {
        val alternativeNames = mutableListOf<String>()
        
        val alsoKnownAsPattern = Regex(
            """(?i)(?:also known as|aka|synonym[s]?)[:\s]+([^.]+)""",
            RegexOption.MULTILINE
        )
        
        alsoKnownAsPattern.findAll(conditionText).forEach { match ->
            val names = match.groupValues[1].split(Regex(""",|;|/"""))
            alternativeNames.addAll(names.map { it.trim() })
        }
        
        return alternativeNames
    }
}