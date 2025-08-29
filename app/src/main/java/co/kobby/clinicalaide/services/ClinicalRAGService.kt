package co.kobby.clinicalaide.services

import co.kobby.clinicalaide.data.app.entities.Citation
import co.kobby.clinicalaide.data.app.entities.ClinicalResponse
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that combines semantic search with clinical response generation.
 * This is the main RAG (Retrieval-Augmented Generation) implementation.
 */
@Singleton
class ClinicalRAGService @Inject constructor(
    private val semanticSearchService: SemanticSearchService
) {
    
    /**
     * Generate a clinical response based on user query and context.
     */
    suspend fun generateClinicalResponse(
        query: String,
        previousContext: String? = null,
        useSemanticSearch: Boolean = true
    ): ClinicalResponse {
        val startTime = System.currentTimeMillis()
        
        // Perform semantic search to find relevant content
        val searchResults = if (useSemanticSearch) {
            semanticSearchService.searchWithContext(
                query = query,
                previousContext = previousContext,
                limit = 5
            )
        } else {
            emptyList()
        }
        
        // Build context from search results
        val context = if (searchResults.isNotEmpty()) {
            semanticSearchService.buildContext(searchResults)
        } else {
            "No relevant content found in the Ghana STG database."
        }
        
        // Generate response based on context
        val response = generateResponseFromContext(query, context, searchResults)
        
        // Create citations from search results
        val citations = searchResults.map { result ->
            Citation(
                source = "Ghana STG 7th Edition",
                pageNumbers = result.content.pageNumber.toString(),
                relevance = result.similarity,
                contentId = result.content.contentId
            )
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        
        // Add a small delay to simulate processing
        if (processingTime < 500) {
            delay(500 - processingTime)
        }
        
        return ClinicalResponse(
            content = response,
            citations = citations,
            confidence = calculateConfidence(searchResults),
            processingTimeMs = System.currentTimeMillis() - startTime,
            contextUsed = context
        )
    }
    
    /**
     * Generate a response from the retrieved context.
     * In a real implementation, this would use a local LLM.
     */
    private fun generateResponseFromContext(
        query: String,
        context: String,
        searchResults: List<SemanticSearchService.SearchResult>
    ): String {
        if (searchResults.isEmpty()) {
            return "I couldn't find specific information about \"$query\" in the Ghana STG database. " +
                   "Please try rephrasing your question or asking about a specific condition, medication, or treatment protocol."
        }
        
        val response = StringBuilder()
        response.append("Based on the Ghana Standard Treatment Guidelines:\n\n")
        
        // Analyze query intent
        val queryLower = query.lowercase()
        val isAskingForTreatment = queryLower.contains("treatment") || queryLower.contains("treat") || 
                                   queryLower.contains("management") || queryLower.contains("therapy")
        val isAskingForDosage = queryLower.contains("dose") || queryLower.contains("dosage") || 
                                queryLower.contains("dosing")
        val isAskingForDiagnosis = queryLower.contains("diagnos") || queryLower.contains("symptom") || 
                                   queryLower.contains("sign")
        
        // Structure response based on query intent and content
        if (searchResults.isNotEmpty()) {
            val topResult = searchResults.first()
            val contentText = topResult.content.contentText
            
            // Extract and format key information
            when {
                isAskingForTreatment -> {
                    response.append("**Treatment Guidelines:**\n")
                    response.append(extractTreatmentInfo(contentText))
                }
                isAskingForDosage -> {
                    response.append("**Dosage Information:**\n")
                    response.append(extractDosageInfo(contentText))
                }
                isAskingForDiagnosis -> {
                    response.append("**Clinical Presentation:**\n")
                    response.append(extractDiagnosticInfo(contentText))
                }
                else -> {
                    // General response - show most relevant content
                    val condensed = contentText.take(500)
                    response.append(condensed)
                    if (contentText.length > 500) {
                        response.append("...")
                    }
                }
            }
            
            // Add additional relevant information from other results
            if (searchResults.size > 1) {
                response.append("\n\n**Additional Information:**\n")
                searchResults.drop(1).take(2).forEach { result ->
                    val snippet = result.content.contentText.take(200)
                    response.append("• $snippet")
                    if (result.content.contentText.length > 200) {
                        response.append("...")
                    }
                    response.append("\n")
                }
            }
            
            // Add important notes if present
            val hasReferralCriteria = contentText.contains("refer", ignoreCase = true)
            val hasContraindications = contentText.contains("contraindicated", ignoreCase = true)
            val hasWarnings = contentText.contains("warning", ignoreCase = true) || 
                             contentText.contains("caution", ignoreCase = true)
            
            if (hasReferralCriteria || hasContraindications || hasWarnings) {
                response.append("\n**Important Notes:**\n")
                if (hasReferralCriteria) {
                    response.append("• See full guidelines for referral criteria\n")
                }
                if (hasContraindications) {
                    response.append("• Check contraindications before prescribing\n")
                }
                if (hasWarnings) {
                    response.append("• Important warnings apply - consult full text\n")
                }
            }
        }
        
        return response.toString()
    }
    
    /**
     * Extract treatment information from content.
     */
    private fun extractTreatmentInfo(content: String): String {
        val lines = content.lines()
        val treatmentLines = mutableListOf<String>()
        
        for (line in lines) {
            val lineLower = line.lowercase()
            if (lineLower.contains("treatment") || 
                lineLower.contains("therapy") ||
                lineLower.contains("management") ||
                lineLower.contains("first-line") ||
                lineLower.contains("second-line") ||
                lineLower.contains("alternative") ||
                line.contains("mg") || 
                line.contains("ml") ||
                line.contains("•")) {
                treatmentLines.add(line)
            }
        }
        
        return if (treatmentLines.isNotEmpty()) {
            treatmentLines.take(10).joinToString("\n")
        } else {
            content.take(400)
        }
    }
    
    /**
     * Extract dosage information from content.
     */
    private fun extractDosageInfo(content: String): String {
        val lines = content.lines()
        val dosageLines = mutableListOf<String>()
        
        for (line in lines) {
            if (line.contains("mg") || 
                line.contains("ml") || 
                line.contains("kg") ||
                line.contains("dose") ||
                line.contains("daily") ||
                line.contains("bd") ||
                line.contains("tds") ||
                line.contains("qds") ||
                line.matches(Regex(".*\\d+.*"))) {
                dosageLines.add(line)
            }
        }
        
        return if (dosageLines.isNotEmpty()) {
            dosageLines.take(8).joinToString("\n")
        } else {
            content.take(400)
        }
    }
    
    /**
     * Extract diagnostic information from content.
     */
    private fun extractDiagnosticInfo(content: String): String {
        val lines = content.lines()
        val diagnosticLines = mutableListOf<String>()
        
        for (line in lines) {
            val lineLower = line.lowercase()
            if (lineLower.contains("symptom") || 
                lineLower.contains("sign") ||
                lineLower.contains("presentation") ||
                lineLower.contains("diagnosis") ||
                lineLower.contains("criteria") ||
                lineLower.contains("feature") ||
                line.contains("•")) {
                diagnosticLines.add(line)
            }
        }
        
        return if (diagnosticLines.isNotEmpty()) {
            diagnosticLines.take(10).joinToString("\n")
        } else {
            content.take(400)
        }
    }
    
    /**
     * Calculate confidence score based on search results.
     */
    private fun calculateConfidence(searchResults: List<SemanticSearchService.SearchResult>): Float {
        return if (searchResults.isEmpty()) {
            0.0f
        } else {
            // Average of top 3 similarities, weighted by position
            val weights = listOf(0.5f, 0.3f, 0.2f)
            searchResults.take(3).mapIndexed { index, result ->
                result.similarity * weights.getOrElse(index) { 0.1f }
            }.sum()
        }
    }
}