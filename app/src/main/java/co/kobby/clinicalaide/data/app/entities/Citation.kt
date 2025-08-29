package co.kobby.clinicalaide.data.app.entities

/**
 * Represents a citation to source material.
 */
data class Citation(
    val source: String,
    val pageNumbers: String,
    val relevance: Float,
    val contentId: Int? = null
)