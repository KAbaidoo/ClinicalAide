package co.kobby.clinicalaide.data.app.entities

/**
 * Represents a clinical response from the RAG system.
 */
data class ClinicalResponse(
    val content: String,
    val citations: List<Citation>,
    val confidence: Float,
    val processingTimeMs: Long,
    val contextUsed: String? = null
)