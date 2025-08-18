package co.kobby.clinicalaide.data.pdf.models

data class ParsedChapter(
    val chapterNumber: Int,
    val title: String,
    val pageStart: Int,
    val pageEnd: Int,
    val rawContent: String,
    val conditions: List<ParsedCondition> = emptyList()
)