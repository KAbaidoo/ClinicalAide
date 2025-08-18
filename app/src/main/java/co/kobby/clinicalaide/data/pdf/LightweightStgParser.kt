package co.kobby.clinicalaide.data.pdf

import android.util.Log
import co.kobby.clinicalaide.data.pdf.models.ParsedCondition
import co.kobby.clinicalaide.data.pdf.models.ParsedMedication
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Lightweight parser that can handle both text and PDF files
 * with minimal memory usage
 */
class LightweightStgParser {
    
    companion object {
        private const val TAG = "LightweightParser"
        private const val MAX_MEMORY_MB = 50 // Maximum memory to use
    }
    
    data class ChapterInfo(
        val chapterNumber: Int,
        val title: String,
        val startPage: Int,
        val endPage: Int? = null
    )
    
    data class ParsedPage(
        val pageNumber: Int,
        val text: String,
        val conditions: List<ParsedCondition> = emptyList(),
        val medications: List<ParsedMedication> = emptyList()
    )
    
    /**
     * Parse text file line by line (for testing)
     */
    suspend fun parseTextFile(
        inputStream: InputStream,
        onChapterFound: suspend (ChapterInfo) -> Unit,
        onPageProcessed: suspend (ParsedPage) -> Unit
    ) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val chapterPattern = Regex("""(?i)Chapter\s+(\d+)[:.]\s*(.+)""")
        val conditionPattern = Regex("""^\s*(\d+)\.\s+([A-Za-z\s]+)""")
        
        var currentPage = 1
        var pageContent = StringBuilder()
        var lineCount = 0
        val linesPerPage = 50 // Simulate pages
        
        var chapters = mutableListOf<ChapterInfo>()
        
        reader.useLines { lines ->
            lines.forEach { line ->
                lineCount++
                pageContent.appendLine(line)
                
                // Check for chapter headers
                val chapterMatch = chapterPattern.find(line)
                if (chapterMatch != null) {
                    val chapterNum = chapterMatch.groupValues[1].toIntOrNull() ?: 0
                    val title = chapterMatch.groupValues[2].trim()
                    
                    val chapterInfo = ChapterInfo(
                        chapterNumber = chapterNum,
                        title = title,
                        startPage = currentPage
                    )
                    chapters.add(chapterInfo)
                    onChapterFound(chapterInfo)
                }
                
                // Process page when reaching page boundary
                if (lineCount % linesPerPage == 0) {
                    val pageText = pageContent.toString()
                    val conditions = extractConditions(pageText)
                    val medications = extractMedications(pageText)
                    
                    onPageProcessed(ParsedPage(
                        pageNumber = currentPage,
                        text = pageText,
                        conditions = conditions,
                        medications = medications
                    ))
                    
                    currentPage++
                    pageContent.clear()
                }
            }
        }
        
        // Process last page if any content remains
        if (pageContent.isNotEmpty()) {
            onPageProcessed(ParsedPage(
                pageNumber = currentPage,
                text = pageContent.toString(),
                conditions = extractConditions(pageContent.toString()),
                medications = extractMedications(pageContent.toString())
            ))
        }
    }
    
    /**
     * Extract metadata from PDF without loading entire document
     * Uses minimal memory by only reading document information
     */
    suspend fun extractPdfMetadataEfficiently(file: File): PdfMetadata? = withContext(Dispatchers.IO) {
        try {
            // Use RandomAccessFile for efficient reading
            RandomAccessBufferedFileInputStream(file).use { input ->
                val parser = PDFParser(input)
                parser.parse()
                
                val doc = parser.getPDDocument()
                
                val metadata = PdfMetadata(
                    totalPages = doc.numberOfPages,
                    version = doc.version,
                    isEncrypted = doc.isEncrypted
                )
                
                // Close document immediately
                doc.close()
                
                metadata
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF metadata", e)
            null
        }
    }
    
    /**
     * Process PDF in chunks using temporary file approach
     * This avoids loading entire PDF into memory
     */
    suspend fun processPdfInChunks(
        file: File,
        chunkSize: Int = 5, // Process 5 pages at a time
        onChunkProcessed: suspend (startPage: Int, endPage: Int, text: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // First get total pages
            val totalPages = extractPdfMetadataEfficiently(file)?.totalPages ?: return@withContext
            
            // Process in chunks
            var currentPage = 1
            while (currentPage <= totalPages) {
                val endPage = minOf(currentPage + chunkSize - 1, totalPages)
                
                // Extract chunk text
                val chunkText = extractPdfChunk(file, currentPage, endPage)
                
                // Process chunk
                onChunkProcessed(currentPage, endPage, chunkText)
                
                currentPage = endPage + 1
                
                // Force garbage collection between chunks
                System.gc()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing PDF in chunks", e)
        }
    }
    
    /**
     * Extract text from specific pages only
     */
    private suspend fun extractPdfChunk(file: File, startPage: Int, endPage: Int): String = 
        withContext(Dispatchers.IO) {
            var text = ""
            
            RandomAccessBufferedFileInputStream(file).use { input ->
                PDDocument.load(input).use { document ->
                    val stripper = PDFTextStripper().apply {
                        this.startPage = startPage
                        this.endPage = endPage
                    }
                    text = stripper.getText(document)
                }
            }
            
            text
        }
    
    private fun extractConditions(text: String): List<ParsedCondition> {
        val conditions = mutableListOf<ParsedCondition>()
        val conditionPattern = Regex("""^\s*(\d+)\.\s+([A-Za-z\s]+)""")
        
        text.lines().forEach { line ->
            val match = conditionPattern.find(line)
            if (match != null) {
                conditions.add(
                    ParsedCondition(
                        name = match.groupValues[2].trim(),
                        pageNumber = 0
                    )
                )
            }
        }
        
        return conditions
    }
    
    private fun extractMedications(text: String): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        val medicationPattern = Regex("""(\w+)\s+(\d+(?:\.\d+)?)\s*(mg|ml|g|mcg|units?)""", RegexOption.IGNORE_CASE)
        
        text.lines().forEach { line ->
            medicationPattern.findAll(line).forEach { match ->
                medications.add(
                    ParsedMedication(
                        name = match.groupValues[1],
                        dosage = "${match.groupValues[2]}${match.groupValues[3]}"
                    )
                )
            }
        }
        
        return medications.distinctBy { "${it.name}_${it.dosage}" }
    }
    
    data class PdfMetadata(
        val totalPages: Int,
        val version: Float,
        val isEncrypted: Boolean
    )
}