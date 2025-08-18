package co.kobby.clinicalaide.data.pdf.models

import co.kobby.clinicalaide.data.database.entities.ContentType

data class ParsedContentBlock(
    val contentType: ContentType,
    val content: String,
    val orderInCondition: Int,
    val metadata: Map<String, String> = emptyMap()
)