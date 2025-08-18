package co.kobby.clinicalaide.data.pdf

import android.content.Context
import co.kobby.clinicalaide.data.pdf.models.ParsedChapter
import co.kobby.clinicalaide.data.pdf.models.ParsedCondition
import co.kobby.clinicalaide.data.pdf.models.ParsedContentBlock
import co.kobby.clinicalaide.data.pdf.models.ParsedMedication
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.InputStream

/**
 * Memory-efficient PDF parser that processes documents page by page
 * to avoid OutOfMemoryError on Android devices
 */
class StreamingStgPdfParser {
    
    data class ChapterInfo(
        val chapterNumber: Int,
        val title: String,
        val startPage: Int,
        val endPage: Int? = null
    )
    
    data class ParsedPage(
        val pageNumber: Int,
        val chapterNumber: Int? = null,
        val text: String,
        val conditions: List<ParsedCondition> = emptyList(),
        val medications: List<ParsedMedication> = emptyList()
    )
    
    /**
     * Parse PDF metadata without loading full content
     */
    suspend fun extractMetadata(inputStream: InputStream): PdfMetadata = withContext(Dispatchers.IO) {
        PDDocument.load(inputStream).use { document ->
            PdfMetadata(
                totalPages = document.numberOfPages,
                version = document.version,
                isEncrypted = document.isEncrypted
            )
        }
    }
    
    /**
     * Extract chapter information without storing full content
     */
    suspend fun extractChapterInfo(inputStream: InputStream): List<ChapterInfo> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<ChapterInfo>()
        
        PDDocument.load(inputStream).use { document ->
            val textStripper = PDFTextStripper()
            val totalPages = document.numberOfPages
            
            // Patterns for Ghana STG chapters
            val chapterPattern = Regex("""(?i)Chapter\s+(\d+)[:.]\s*(.+)""")
            val altPattern = Regex("""(?i)—\s*(.+?)\s*—Chapter\s+(\d+):\s*(.+)""")
            
            for (pageNum in 1..totalPages) {
                textStripper.startPage = pageNum
                textStripper.endPage = pageNum
                val pageText = textStripper.getText(document)
                
                // Check for chapter headers
                val match = chapterPattern.find(pageText) ?: altPattern.find(pageText)
                if (match != null) {
                    // Update end page for previous chapter
                    if (chapters.isNotEmpty()) {
                        val lastChapter = chapters.last()
                        chapters[chapters.lastIndex] = lastChapter.copy(endPage = pageNum - 1)
                    }
                    
                    // Add new chapter
                    val chapterNum = if (match.groups.size > 2) {
                        match.groupValues[1].toIntOrNull() ?: match.groupValues[2].toIntOrNull() ?: 0
                    } else {
                        match.groupValues[1].toIntOrNull() ?: 0
                    }
                    
                    val title = match.groupValues.lastOrNull { it.isNotEmpty() } ?: ""
                    
                    chapters.add(ChapterInfo(
                        chapterNumber = chapterNum,
                        title = title.trim(),
                        startPage = pageNum
                    ))
                }
                
                // Yield to prevent blocking
                if (pageNum % 10 == 0) {
                    yield()
                }
            }
            
            // Set end page for last chapter
            if (chapters.isNotEmpty()) {
                val lastChapter = chapters.last()
                chapters[chapters.lastIndex] = lastChapter.copy(endPage = totalPages)
            }
        }
        
        chapters
    }
    
    /**
     * Process PDF page by page with a callback for each page
     * This allows processing without loading entire document into memory
     */
    suspend fun processPages(
        inputStream: InputStream,
        startPage: Int = 1,
        endPage: Int? = null,
        onPageProcessed: suspend (ParsedPage) -> Unit
    ) = withContext(Dispatchers.IO) {
        PDDocument.load(inputStream).use { document ->
            val textStripper = PDFTextStripper()
            val lastPage = endPage ?: document.numberOfPages
            val pageProcessor = PageProcessor()
            
            for (pageNum in startPage..minOf(lastPage, document.numberOfPages)) {
                // Extract single page text
                textStripper.startPage = pageNum
                textStripper.endPage = pageNum
                val pageText = textStripper.getText(document)
                
                // Process the page
                val parsedPage = pageProcessor.processPage(pageText, pageNum)
                
                // Callback with parsed page
                onPageProcessed(parsedPage)
                
                // Clear memory periodically
                if (pageNum % 5 == 0) {
                    System.gc()
                    yield()
                }
            }
        }
    }
    
    /**
     * Process a single chapter efficiently
     */
    suspend fun processChapter(
        inputStream: InputStream,
        chapterInfo: ChapterInfo,
        onPageProcessed: suspend (ParsedPage) -> Unit
    ) {
        processPages(
            inputStream = inputStream,
            startPage = chapterInfo.startPage,
            endPage = chapterInfo.endPage,
            onPageProcessed = onPageProcessed
        )
    }
    
    /**
     * Extract table of contents from first few pages
     */
    suspend fun extractTableOfContents(inputStream: InputStream): List<String> = withContext(Dispatchers.IO) {
        val tocEntries = mutableListOf<String>()
        
        PDDocument.load(inputStream).use { document ->
            val textStripper = PDFTextStripper()
            
            // Check first 20 pages for TOC
            for (pageNum in 1..minOf(20, document.numberOfPages)) {
                textStripper.startPage = pageNum
                textStripper.endPage = pageNum
                val pageText = textStripper.getText(document)
                
                if (pageText.contains("TABLE OF CONTENTS", ignoreCase = true) ||
                    pageText.contains("CONTENTS", ignoreCase = true)) {
                    
                    // Extract lines that look like TOC entries
                    pageText.lines().forEach { line ->
                        if (line.matches(Regex(""".*\.\s*\d+$""")) || // Ends with page number
                            line.matches(Regex("""^Chapter\s+\d+.*"""))) { // Chapter heading
                            tocEntries.add(line.trim())
                        }
                    }
                }
            }
        }
        
        tocEntries
    }
    
    data class PdfMetadata(
        val totalPages: Int,
        val version: Float,
        val isEncrypted: Boolean
    )
}

/**
 * Processes individual pages to extract medical content
 */
class PageProcessor {
    
    private val conditionPattern = Regex("""^\s*(\d+)\.\s+([A-Za-z\s]+)""")
    private val medicationPattern = Regex("""(\d+(?:\.\d+)?)\s*(mg|ml|g|mcg|units?)""", RegexOption.IGNORE_CASE)
    
    fun processPage(pageText: String, pageNumber: Int): StreamingStgPdfParser.ParsedPage {
        val conditions = extractConditions(pageText)
        val medications = extractMedications(pageText)
        
        return StreamingStgPdfParser.ParsedPage(
            pageNumber = pageNumber,
            text = pageText,
            conditions = conditions,
            medications = medications
        )
    }
    
    private fun extractConditions(text: String): List<ParsedCondition> {
        val conditions = mutableListOf<ParsedCondition>()
        
        text.lines().forEach { line ->
            val match = conditionPattern.find(line)
            if (match != null) {
                conditions.add(
                    ParsedCondition(
                        name = match.groupValues[2].trim(),
                        pageNumber = 0 // Will be set by caller
                    )
                )
            }
        }
        
        return conditions
    }
    
    private fun extractMedications(text: String): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        val commonMeds = listOf("paracetamol", "amoxicillin", "metronidazole", "artemether")
        
        text.lines().forEach { line ->
            // Check for common medications
            commonMeds.forEach { med ->
                if (line.contains(med, ignoreCase = true)) {
                    val dosageMatch = medicationPattern.find(line)
                    if (dosageMatch != null) {
                        medications.add(
                            ParsedMedication(
                                name = med.replaceFirstChar { it.uppercase() },
                                dosage = "${dosageMatch.groupValues[1]}${dosageMatch.groupValues[2]}"
                            )
                        )
                    }
                }
            }
        }
        
        return medications.distinctBy { it.name }
    }
}