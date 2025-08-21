package co.kobby.clinicalaide.data.pdf.extractors

import android.util.Log
import co.kobby.clinicalaide.data.database.entities.ContentType
import co.kobby.clinicalaide.data.pdf.models.ParsedContentBlock

/**
 * Extracts and categorizes content blocks from medical text
 * Identifies different types of content (symptoms, treatment, etc.) based on section headers
 */
class ContentBlockExtractor {
    
    companion object {
        private const val TAG = "ContentBlockExtractor"
        
        // Section header patterns for Ghana STG format
        private val SECTION_PATTERNS = mapOf(
            ContentType.DEFINITION to listOf(
                Regex("""(?i)^(definition|description|overview|introduction):?\s*$"""),
                Regex("""(?i)^what\s+is\s+""")
            ),
            ContentType.SYMPTOMS to listOf(
                Regex("""(?i)^(clinical\s+features?|symptoms?|signs?\s+and\s+symptoms?|presentation):?\s*$"""),
                Regex("""(?i)^(signs?|manifestations?):?\s*$""")
            ),
            ContentType.TREATMENT to listOf(
                Regex("""(?i)^(treatment|management|therapy):?\s*$"""),
                Regex("""(?i)^(pharmacological\s+treatment|non[- ]?pharmacological\s+treatment):?\s*$"""),
                Regex("""(?i)^(first[- ]?line\s+treatment|second[- ]?line\s+treatment):?\s*$""")
            ),
            ContentType.DOSAGE to listOf(
                Regex("""(?i)^(dosage?|dose|dosing|administration):?\s*$"""),
                Regex("""(?i)^(adult\s+dose|child\s+dose|pediatric\s+dose):?\s*""")
            ),
            ContentType.REFERRAL to listOf(
                Regex("""(?i)^(referral?|refer|when\s+to\s+refer):?\s*$"""),
                Regex("""(?i)^(referral\s+criteria|indications?\s+for\s+referral):?\s*$""")
            ),
            ContentType.COMPLICATIONS to listOf(
                Regex("""(?i)^(complications?|adverse\s+effects?|side\s+effects?):?\s*$"""),
                Regex("""(?i)^(warnings?|precautions?|contraindications?):?\s*$""")
            ),
            ContentType.INVESTIGATIONS to listOf(
                Regex("""(?i)^(investigations?|diagnostic|tests?|laboratory):?\s*$"""),
                Regex("""(?i)^(diagnosis):?\s*$"""),
                Regex("""(?i)^(work[- ]?up|assessment|evaluation):?\s*$""")
            ),
            ContentType.PREVENTION to listOf(
                Regex("""(?i)^(prevention|prophylaxis|preventive\s+measures?):?\s*$"""),
                Regex("""(?i)^(immunization|vaccination):?\s*$""")
            ),
            ContentType.FOLLOW_UP to listOf(
                Regex("""(?i)^(follow[- ]?up|monitoring|surveillance):?\s*$"""),
                Regex("""(?i)^(review|reassessment):?\s*$""")
            )
        )
    }
    
    /**
     * Extract content blocks from a text section
     * Attempts to identify and categorize different types of medical content
     */
    fun extractContentBlocks(text: String): List<ParsedContentBlock> {
        val blocks = mutableListOf<ParsedContentBlock>()
        val lines = text.lines()
        
        var currentType: ContentType? = null
        var currentContent = StringBuilder()
        var blockOrder = 0
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // Skip empty lines
            if (trimmedLine.isEmpty()) {
                if (currentContent.isNotEmpty()) {
                    currentContent.append("\n")
                }
                continue
            }
            
            // Check if this line is a section header
            val detectedType = detectContentType(trimmedLine)
            
            if (detectedType != null) {
                // Save previous block if exists
                if (currentType != null && currentContent.isNotEmpty()) {
                    blocks.add(
                        ParsedContentBlock(
                            contentType = currentType,
                            content = currentContent.toString().trim(),
                            orderInCondition = blockOrder++
                        )
                    )
                }
                
                // Start new block
                currentType = detectedType
                currentContent = StringBuilder()
                
                // Remove the header from content if it's just a header
                val headerRemoved = removeHeader(trimmedLine, detectedType)
                if (headerRemoved.isNotEmpty()) {
                    currentContent.append(headerRemoved).append("\n")
                }
                
                Log.d(TAG, "Detected content type: $detectedType from line: $trimmedLine")
            } else {
                // Add to current content
                if (currentContent.isNotEmpty()) {
                    currentContent.append("\n")
                }
                currentContent.append(line)
            }
        }
        
        // Save last block
        if (currentType != null && currentContent.isNotEmpty()) {
            blocks.add(
                ParsedContentBlock(
                    contentType = currentType,
                    content = currentContent.toString().trim(),
                    orderInCondition = blockOrder
                )
            )
        } else if (blocks.isEmpty() && text.isNotEmpty()) {
            // If no sections detected, treat entire text as definition
            blocks.add(
                ParsedContentBlock(
                    contentType = ContentType.DEFINITION,
                    content = text.trim(),
                    orderInCondition = 0
                )
            )
        }
        
        return blocks
    }
    
    /**
     * Detect content type from a line of text
     */
    private fun detectContentType(line: String): ContentType? {
        for ((type, patterns) in SECTION_PATTERNS) {
            for (pattern in patterns) {
                if (pattern.containsMatchIn(line)) {
                    return type
                }
            }
        }
        return null
    }
    
    /**
     * Remove section header from line, keeping any content after it
     */
    private fun removeHeader(line: String, type: ContentType): String {
        val patterns = SECTION_PATTERNS[type] ?: return line
        
        for (pattern in patterns) {
            val match = pattern.find(line)
            if (match != null) {
                val remaining = line.substring(match.range.last + 1).trim()
                return remaining
            }
        }
        
        return line
    }
    
    /**
     * Extract content blocks specifically for a condition
     * This method can be more intelligent about context
     */
    fun extractConditionContentBlocks(
        conditionName: String,
        conditionText: String
    ): List<ParsedContentBlock> {
        Log.d(TAG, "Extracting content blocks for condition: $conditionName")
        
        val blocks = extractContentBlocks(conditionText)
        
        // Add metadata for condition
        return blocks.map { block ->
            block.copy(
                metadata = block.metadata + mapOf(
                    "condition" to conditionName
                )
            )
        }
    }
    
    /**
     * Identify if text contains medication dosing information
     */
    fun containsDosageInfo(text: String): Boolean {
        val dosagePatterns = listOf(
            Regex("""(?i)\d+\s*mg"""),
            Regex("""(?i)\d+\s*ml"""),
            Regex("""(?i)\d+\s*tablets?"""),
            Regex("""(?i)daily|twice|three\s+times"""),
            Regex("""(?i)every\s+\d+\s+hours?""")
        )
        
        return dosagePatterns.any { it.containsMatchIn(text) }
    }
}