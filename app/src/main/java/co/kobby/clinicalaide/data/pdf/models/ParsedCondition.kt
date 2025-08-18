package co.kobby.clinicalaide.data.pdf.models

data class ParsedCondition(
    val name: String,
    val alternativeNames: List<String> = emptyList(),
    val icdCode: String? = null,
    val pageNumber: Int,
    val contentBlocks: List<ParsedContentBlock> = emptyList(),
    val medications: List<ParsedMedication> = emptyList()
)