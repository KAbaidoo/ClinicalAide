package co.kobby.clinicalaide.data.pdf

import android.content.Context
import android.util.Log
import co.kobby.clinicalaide.data.pdf.models.ParsedCondition
import co.kobby.clinicalaide.data.pdf.models.ParsedMedication
import co.kobby.clinicalaide.data.pdf.models.ParsedContentBlock
import co.kobby.clinicalaide.data.pdf.extractors.ContentBlockExtractor
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * File-based PDF parser that copies PDF to temp file for processing
 * This avoids loading entire document into memory from InputStream
 * 
 * @param context Android context for file operations
 * @param chunkSize Number of pages to process at once (default: 3)
 */
class FileBasedStgPdfParser(
    private val context: Context,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    private val contentBlockExtractor = ContentBlockExtractor()
    
    companion object {
        private const val TAG = "FileBasedParser"
        private const val TEMP_FILE_PREFIX = "stg_pdf_"
        private const val TEMP_FILE_SUFFIX = ".pdf"
        const val DEFAULT_CHUNK_SIZE = 3 // Process 3 pages at a time
        const val MAX_CHUNK_SIZE = 10 // Maximum pages to process at once
        const val MIN_CHUNK_SIZE = 1 // Minimum pages to process at once
    }
    
    init {
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) {
            "Chunk size must be between $MIN_CHUNK_SIZE and $MAX_CHUNK_SIZE"
        }
    }
    
    data class ChapterInfo(
        val chapterNumber: Int,
        val title: String,
        val startPage: Int,
        val endPage: Int? = null
    )
    
    data class ProcessingResult(
        val currentPage: Int,
        val totalPages: Int,
        val text: String,
        val conditions: List<ParsedCondition>,
        val medications: List<ParsedMedication>,
        val chapter: ChapterInfo? = null
    )
    
    /**
     * Copy PDF from assets to temp file for efficient processing
     */
    private suspend fun copyToTempFile(assetFileName: String): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, context.cacheDir)
        
        context.assets.open(assetFileName).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        Log.d(TAG, "PDF copied to temp file: ${tempFile.absolutePath} (${tempFile.length() / 1024 / 1024} MB)")
        tempFile
    }
    
    /**
     * Process STG PDF using file-based approach
     * Returns a Flow of processing results
     */
    fun processStgPdf(assetFileName: String): Flow<ProcessingResult> = flow {
        var tempFile: File? = null
        
        try {
            // Copy PDF to temp file
            tempFile = copyToTempFile(assetFileName)
            
            // Get metadata
            val totalPages = getPageCount(tempFile)
            Log.d(TAG, "Processing PDF with $totalPages pages")
            
            // Extract chapters first
            val chapters = extractChapters(tempFile)
            Log.d(TAG, "Found ${chapters.size} chapters")
            
            // Process PDF in chunks
            var currentPage = 1
            while (currentPage <= totalPages) {
                val endPage = minOf(currentPage + chunkSize - 1, totalPages)
                
                // Find current chapter
                val currentChapter = chapters.find { chapter ->
                    currentPage >= chapter.startPage && 
                    (chapter.endPage == null || currentPage <= chapter.endPage)
                }
                
                // Extract chunk text
                val chunkText = extractPageRange(tempFile, currentPage, endPage)
                
                // Parse content
                val conditions = extractConditions(chunkText)
                val medications = extractMedications(chunkText)
                
                // Emit result
                emit(ProcessingResult(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    text = chunkText,
                    conditions = conditions,
                    medications = medications,
                    chapter = currentChapter
                ))
                
                currentPage = endPage + 1
                
                // Force garbage collection between chunks
                if (currentPage % 20 == 0) {
                    System.gc()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing PDF", e)
            throw e
        } finally {
            // Clean up temp file
            tempFile?.let {
                if (it.exists()) {
                    it.delete()
                    Log.d(TAG, "Temp file deleted")
                }
            }
        }
    }
    
    /**
     * Get total page count from PDF file
     */
    private suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        RandomAccessBufferedFileInputStream(file).use { input ->
            PDDocument.load(input).use { document ->
                document.numberOfPages
            }
        }
    }
    
    /**
     * Extract chapters from PDF file, skipping TOC
     */
    private suspend fun extractChapters(file: File): List<ChapterInfo> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<ChapterInfo>()
        
        // Patterns for chapter detection
        // Handle both clean format and mangled text (e.g., "1Chapter 11" on page 29)
        val mainChapterPattern = Regex("""(?i)Chapter\s+(\d+)[:.]\s*(.+)""")
        val mangledChapterPattern = Regex("""(?i)\d*Chapter\s*(\d+)""") // Handles "1Chapter 11"
        val runningHeaderPattern = Regex("""(?i)—\s*.+\s*—Chapter\s+(\d+):\s*(.+)""")
        
        // TOC detection pattern - pages with many dots typically indicate TOC
        val tocPattern = Regex("""\.{5,}""") // 5 or more dots in a row
        
        RandomAccessBufferedFileInputStream(file).use { input ->
            PDDocument.load(input).use { document ->
                val stripper = PDFTextStripper()
                val totalPages = document.numberOfPages
                
                // Ghana STG structure: TOC is pages 3-11, main content starts page 29
                // For sample PDFs that start from page 29, scan from page 1
                // For full PDFs, scan from page 29
                val startScanPage = if (totalPages < 100) 1 else 29
                
                Log.d(TAG, "Scanning for chapters starting from page $startScanPage")
                
                // Track which chapters we've already found to avoid duplicates
                val foundChapters = mutableSetOf<Int>()
                
                for (pageNum in startScanPage..minOf(totalPages, 200)) { // Check up to page 200
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    val pageText = stripper.getText(document)
                    
                    // Skip if this looks like a TOC page (has many dots)
                    if (pageNum < 30 && tocPattern.find(pageText) != null) {
                        Log.d(TAG, "Skipping page $pageNum - appears to be TOC")
                        continue
                    }
                    
                    // Try to match chapter patterns
                    val mainMatch = mainChapterPattern.find(pageText)
                    val mangledMatch = mangledChapterPattern.find(pageText)
                    val headerMatch = runningHeaderPattern.find(pageText)
                    
                    // Try patterns in order of preference
                    val match = headerMatch ?: mainMatch ?: mangledMatch
                    
                    if (match != null) {
                        // Extract chapter number (handle mangled format like "1Chapter 11" -> Chapter 1)
                        val chapterNum = when {
                            match === mangledMatch -> {
                                // For mangled format, take the first digit after "Chapter"
                                val num = match.groupValues[1].take(1).toIntOrNull() ?: 0
                                num
                            }
                            match.groups.size > 2 -> {
                                match.groupValues[1].toIntOrNull() ?: 0
                            }
                            else -> 0
                        }
                        
                        // Only add if we haven't seen this chapter number yet
                        if (chapterNum > 0 && !foundChapters.contains(chapterNum)) {
                            // Update end page for previous chapter
                            if (chapters.isNotEmpty()) {
                                val lastChapter = chapters.last()
                                chapters[chapters.lastIndex] = lastChapter.copy(endPage = pageNum - 1)
                            }
                            
                            // Extract title
                            val title = when {
                                match === mangledMatch -> {
                                    // For mangled format, try to extract title from following lines
                                    val lines = pageText.lines()
                                    val chapterLine = lines.indexOfFirst { it.contains("Chapter", ignoreCase = true) }
                                    if (chapterLine >= 0 && chapterLine + 2 < lines.size) {
                                        // Combine next two lines as title (e.g., "Disorders of the" + "Gastrointestinal Tract")
                                        "${lines[chapterLine + 1].trim()} ${lines[chapterLine + 2].trim()}"
                                            .replace(Regex("\\d+"), "") // Remove stray numbers
                                            .trim()
                                    } else {
                                        "Chapter $chapterNum"
                                    }
                                }
                                match.groups.size > 2 -> match.groupValues[2].trim()
                                else -> "Chapter $chapterNum"
                            }
                            
                            // Add new chapter
                            chapters.add(ChapterInfo(
                                chapterNumber = chapterNum,
                                title = title,
                                startPage = pageNum
                            ))
                            
                            foundChapters.add(chapterNum)
                            Log.d(TAG, "Found Chapter $chapterNum on page $pageNum: $title")
                        }
                    }
                }
                
                // Set end page for last chapter
                if (chapters.isNotEmpty()) {
                    val lastChapter = chapters.last()
                    chapters[chapters.lastIndex] = lastChapter.copy(endPage = totalPages)
                }
                
                Log.d(TAG, "Extracted ${chapters.size} chapters from main body")
            }
        }
        
        chapters
    }
    
    /**
     * Extract text from specific page range
     */
    private suspend fun extractPageRange(file: File, startPage: Int, endPage: Int): String = 
        withContext(Dispatchers.IO) {
            RandomAccessBufferedFileInputStream(file).use { input ->
                PDDocument.load(input).use { document ->
                    val stripper = PDFTextStripper().apply {
                        this.startPage = startPage
                        this.endPage = endPage
                    }
                    stripper.getText(document)
                }
            }
        }
    
    /**
     * Extract conditions from text with their content blocks
     */
    private fun extractConditions(text: String): List<ParsedCondition> {
        val conditions = mutableListOf<ParsedCondition>()
        val conditionPattern = Regex("""^\s*(\d+)\.\s+([A-Za-z][A-Za-z\s]+)""")
        
        // Split text into potential condition sections
        val lines = text.lines()
        var currentConditionName: String? = null
        var currentConditionText = StringBuilder()
        var currentPageNumber = 0
        
        for ((index, line) in lines.withIndex()) {
            val match = conditionPattern.find(line)
            
            if (match != null && match.groupValues[2].trim().length > 3) {
                // Found a new condition - save the previous one if exists
                if (currentConditionName != null && currentConditionText.isNotEmpty()) {
                    val contentBlocks = contentBlockExtractor.extractConditionContentBlocks(
                        currentConditionName,
                        currentConditionText.toString()
                    )
                    
                    conditions.add(
                        ParsedCondition(
                            name = currentConditionName,
                            pageNumber = currentPageNumber,
                            contentBlocks = contentBlocks
                        )
                    )
                }
                
                // Start new condition
                currentConditionName = match.groupValues[2].trim()
                currentConditionText = StringBuilder()
                currentPageNumber = 0 // Will be set properly in actual usage
            } else if (currentConditionName != null) {
                // Add line to current condition content
                currentConditionText.append(line).append("\n")
            }
        }
        
        // Save last condition
        if (currentConditionName != null && currentConditionText.isNotEmpty()) {
            val contentBlocks = contentBlockExtractor.extractConditionContentBlocks(
                currentConditionName,
                currentConditionText.toString()
            )
            
            conditions.add(
                ParsedCondition(
                    name = currentConditionName,
                    pageNumber = currentPageNumber,
                    contentBlocks = contentBlocks
                )
            )
        }
        
        return conditions
    }
    
    /**
     * Extract medications from text
     */
    private fun extractMedications(text: String): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        
        // Debug logging to see what text we're searching
        if (text.contains("ORS", ignoreCase = true) || 
            text.contains("zinc", ignoreCase = true) ||
            text.contains("diarrhoea", ignoreCase = true)) {
            Log.d(TAG, "Found relevant text for medication extraction, sample: ${text.take(200)}")
        }
        
        // Pattern 1: Medication with dosage on same line
        val sameLinePatterns = listOf(
            Regex("""(?i)(paracetamol|acetaminophen)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg|g)"""),
            Regex("""(?i)(amoxicillin)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg|g)"""),
            Regex("""(?i)(metronidazole)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg|g)"""),
            Regex("""(?i)(artemether)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg)"""),
            Regex("""(?i)(ORS|oral rehydration)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(ml|L)"""),
            Regex("""(?i)(zinc)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg)"""),
            Regex("""(?i)(ciprofloxacin)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg|g)"""),
            Regex("""(?i)(tetracycline)\s*[:,]?\s*(\d+(?:\.\d+)?)\s*(mg|g)""")
        )
        
        // Pattern 2: Just medication names (without dosage)
        val medicationNamesPattern = Regex("""(?i)\b(ORS|oral rehydration salt|oral rehydration solution|zinc sulphate|zinc|metronidazole|ciprofloxacin|tetracycline|cotrimoxazole|amoxicillin|paracetamol|artemether|lumefantrine|artesunate|amodiaquine|quinine|chloroquine|doxycycline|clindamycin|ceftriaxone|benzylpenicillin|gentamicin|erythromycin|azithromycin)\b""")
        
        // Pattern 3: Bullet points or numbered lists with medications
        val bulletPattern = Regex("""(?i)[•·\-]\s*([\w\s]+?)\s*(?:,|\s+|$)""")
        
        // Try same line patterns first
        sameLinePatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                medications.add(
                    ParsedMedication(
                        name = match.groupValues[1].replaceFirstChar { it.uppercase() },
                        dosage = "${match.groupValues[2]}${match.groupValues[3]}"
                    )
                )
            }
        }
        
        // If no medications found with dosage, try to find just medication names
        if (medications.isEmpty()) {
            medicationNamesPattern.findAll(text).forEach { match ->
                val medName = match.groupValues[1].replaceFirstChar { it.uppercase() }
                // Look for dosage in the next 50 characters after the medication name
                val startIndex = match.range.last + 1
                val endIndex = minOf(startIndex + 50, text.length)
                val followingText = if (startIndex < text.length) text.substring(startIndex, endIndex) else ""
                
                val dosagePattern = Regex("""(\d+(?:\.\d+)?)\s*(mg|g|ml|L)""")
                val dosageMatch = dosagePattern.find(followingText)
                
                medications.add(
                    ParsedMedication(
                        name = medName,
                        dosage = if (dosageMatch != null) {
                            "${dosageMatch.groupValues[1]}${dosageMatch.groupValues[2]}"
                        } else {
                            ""  // No dosage found
                        }
                    )
                )
            }
        }
        
        // Log what we found
        if (medications.isNotEmpty()) {
            Log.d(TAG, "Extracted ${medications.size} medications from chunk")
            medications.take(3).forEach { med ->
                Log.d(TAG, "  - ${med.name} ${med.dosage}")
            }
        }
        
        return medications.distinctBy { "${it.name}_${it.dosage}" }
    }
    
    /**
     * Clean up any temp files in cache directory
     */
    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(TEMP_FILE_PREFIX) && file.name.endsWith(TEMP_FILE_SUFFIX)) {
                file.delete()
                Log.d(TAG, "Deleted old temp file: ${file.name}")
            }
        }
    }
}