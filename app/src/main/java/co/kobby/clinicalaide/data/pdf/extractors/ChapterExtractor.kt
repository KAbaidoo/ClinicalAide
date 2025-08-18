package co.kobby.clinicalaide.data.pdf.extractors

import co.kobby.clinicalaide.data.pdf.models.ParsedChapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChapterExtractor {
    
    companion object {
        // Updated pattern to match actual Ghana STG format: "Chapter 1. Disorders of..."
        private val CHAPTER_PATTERN = Regex(
            """(?i)^\s*Chapter\s+(\d+)[:.]\s*(.+)$""",
            RegexOption.MULTILINE
        )
        
        // Alternative pattern for chapters in different formats
        private val ALT_CHAPTER_PATTERN = Regex(
            """(?i)^—\s*(.+?)\s*—Chapter\s+(\d+):\s*(.+)$""",
            RegexOption.MULTILINE
        )
        
        private val SECTION_PATTERN = Regex(
            """(?i)^\s*(\d+)\.\s+(.+)$""",
            RegexOption.MULTILINE
        )
        
        // Pattern for numbered conditions like "1. Diarrhoea"
        private val CONDITION_PATTERN = Regex(
            """^\s*(\d+)\.\s+([A-Za-z\s]+)$""",
            RegexOption.MULTILINE
        )
    }
    
    suspend fun extractChapters(document: PDDocument): List<ParsedChapter> = withContext(Dispatchers.IO) {
        val textStripper = PDFTextStripper()
        val totalPages = document.numberOfPages
        val chapters = mutableListOf<ParsedChapter>()
        
        var currentChapterNumber = 0
        var currentChapterTitle = ""
        var currentChapterStartPage = 1
        var currentChapterContent = StringBuilder()
        
        for (pageNum in 1..totalPages) {
            textStripper.startPage = pageNum
            textStripper.endPage = pageNum
            val pageText = textStripper.getText(document)
            
            // Try both chapter patterns
            val chapterMatch = CHAPTER_PATTERN.find(pageText) 
            val altChapterMatch = ALT_CHAPTER_PATTERN.find(pageText)
            
            if (chapterMatch != null || altChapterMatch != null) {
                // Save previous chapter if exists
                if (currentChapterNumber > 0) {
                    chapters.add(
                        ParsedChapter(
                            chapterNumber = currentChapterNumber,
                            title = currentChapterTitle.trim(),
                            pageStart = currentChapterStartPage,
                            pageEnd = pageNum - 1,
                            rawContent = currentChapterContent.toString()
                        )
                    )
                }
                
                // Extract new chapter info based on which pattern matched
                if (chapterMatch != null) {
                    currentChapterNumber = chapterMatch.groupValues[1].toIntOrNull() ?: 0
                    currentChapterTitle = chapterMatch.groupValues[2]
                } else if (altChapterMatch != null) {
                    currentChapterNumber = altChapterMatch.groupValues[2].toIntOrNull() ?: 0
                    currentChapterTitle = altChapterMatch.groupValues[3]
                }
                
                currentChapterStartPage = pageNum
                currentChapterContent = StringBuilder()
            }
            
            currentChapterContent.append(pageText).append("\n")
        }
        
        if (currentChapterNumber > 0) {
            chapters.add(
                ParsedChapter(
                    chapterNumber = currentChapterNumber,
                    title = currentChapterTitle.trim(),
                    pageStart = currentChapterStartPage,
                    pageEnd = totalPages,
                    rawContent = currentChapterContent.toString()
                )
            )
        }
        
        chapters
    }
    
    fun extractTableOfContents(document: PDDocument): Map<Int, String> {
        val toc = mutableMapOf<Int, String>()
        val textStripper = PDFTextStripper()
        
        textStripper.startPage = 1
        textStripper.endPage = minOf(10, document.numberOfPages)
        val tocText = textStripper.getText(document)
        
        CHAPTER_PATTERN.findAll(tocText).forEach { match ->
            val chapterNum = match.groupValues[1].toIntOrNull()
            val chapterTitle = match.groupValues[2]
            if (chapterNum != null) {
                toc[chapterNum] = chapterTitle
            }
        }
        
        return toc
    }
}