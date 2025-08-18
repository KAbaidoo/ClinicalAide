package co.kobby.clinicalaide.data.pdf

import android.content.Context
import android.util.Log
import co.kobby.clinicalaide.data.database.dao.StgDao
import co.kobby.clinicalaide.data.database.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Service that orchestrates the PDF parsing and database storage process
 * Uses FileBasedStgPdfParser for memory-efficient processing
 */
class StgPdfProcessingService(
    private val context: Context,
    private val dao: StgDao
) {
    
    companion object {
        private const val TAG = "StgPdfProcessing"
        private const val STG_PDF_ASSET = "GHANA-STG-2017-1.pdf"
        private const val BATCH_SIZE = 10 // Batch size for database operations
        private const val CHUNK_SIZE = 3 // Pages to process at once
    }
    
    data class ProcessingProgress(
        val currentPage: Int,
        val totalPages: Int,
        val currentChapter: String? = null,
        val message: String
    )
    
    /**
     * Main processing function that parses the STG PDF and stores content in database
     * Uses FileBasedStgPdfParser for memory-efficient processing
     * Returns a Flow to track progress
     */
    fun processStgPdf(): Flow<ProcessingProgress> = flow {
        val parser = FileBasedStgPdfParser(context)
        
        try {
            Log.d(TAG, "Starting STG PDF processing with FileBasedStgPdfParser")
            
            // Clean up any old temp files first
            parser.cleanupTempFiles()
            
            emit(ProcessingProgress(0, 0, null, "Initializing PDF processing..."))
            
            // Track chapters we've seen
            val processedChapters = mutableMapOf<Int, Long>() // chapterNumber to chapterId
            var currentChapterId: Long? = null
            
            // Collections for batch processing
            val contentBlocks = mutableListOf<StgContentBlock>()
            val medications = mutableListOf<StgMedication>()
            val conditions = mutableListOf<StgCondition>()
            
            // Process the PDF in chunks
            parser.processStgPdf(STG_PDF_ASSET).collect { result ->
                // Emit progress
                emit(ProcessingProgress(
                    currentPage = result.currentPage,
                    totalPages = result.totalPages,
                    currentChapter = result.chapter?.title,
                    message = "Processing pages ${result.currentPage}-${minOf(result.currentPage + CHUNK_SIZE - 1, result.totalPages)}"
                ))
                
                // Handle chapter changes
                if (result.chapter != null && !processedChapters.containsKey(result.chapter.chapterNumber)) {
                    // Insert new chapter
                    currentChapterId = withContext(Dispatchers.IO) {
                        dao.insertChapter(
                            StgChapter(
                                chapterNumber = result.chapter.chapterNumber,
                                chapterTitle = result.chapter.title,
                                startPage = result.chapter.startPage,
                                endPage = result.chapter.endPage ?: result.totalPages,
                                description = "Chapter ${result.chapter.chapterNumber} of Ghana STG"
                            )
                        )
                    }
                    processedChapters[result.chapter.chapterNumber] = currentChapterId!!
                    
                    Log.d(TAG, "Inserted Chapter ${result.chapter.chapterNumber}: ${result.chapter.title}")
                } else if (result.chapter != null) {
                    currentChapterId = processedChapters[result.chapter.chapterNumber]
                }
                
                // Process conditions from this chunk
                result.conditions.forEach { parsedCondition ->
                    if (currentChapterId != null) {
                        conditions.add(
                            StgCondition(
                                chapterId = currentChapterId!!,
                                conditionNumber = 0, // Will be properly numbered later
                                conditionName = parsedCondition.name,
                                startPage = result.currentPage,
                                endPage = result.currentPage,
                                keywords = extractKeywords(parsedCondition.name)
                            )
                        )
                    }
                }
                
                // Store chunk text as content block if we have a chapter
                if (currentChapterId != null && result.text.isNotEmpty()) {
                    // For now, associate with chapter until we have condition IDs
                    // In production, we'd parse conditions first then associate content
                    contentBlocks.add(
                        StgContentBlock(
                            conditionId = currentChapterId!!, // Using chapter ID temporarily
                            blockType = ContentType.DEFINITION.name,
                            content = result.text.take(5000), // Limit content size
                            pageNumber = result.currentPage,
                            orderInCondition = 0,
                            keywords = extractKeywords(result.text)
                        )
                    )
                }
                
                // Process medications from this chunk
                result.medications.forEach { parsedMedication ->
                    if (currentChapterId != null) {
                        medications.add(
                            StgMedication(
                                conditionId = currentChapterId!!, // Using chapter ID temporarily
                                medicationName = parsedMedication.name,
                                dosage = parsedMedication.dosage ?: "",
                                frequency = parsedMedication.frequency ?: "",
                                duration = parsedMedication.duration ?: "",
                                route = parsedMedication.route ?: "oral",
                                ageGroup = "adult",
                                weightBased = false,
                                contraindications = parsedMedication.contraindications?.joinToString(";"),
                                sideEffects = parsedMedication.sideEffects?.joinToString(";"),
                                evidenceLevel = null,
                                pageNumber = result.currentPage
                            )
                        )
                    }
                }
                
                // Batch insert when collections reach threshold
                if (conditions.size >= BATCH_SIZE) {
                    withContext(Dispatchers.IO) {
                        conditions.forEach { condition ->
                            dao.insertCondition(condition)
                        }
                        conditions.clear()
                    }
                }
                
                if (contentBlocks.size >= BATCH_SIZE) {
                    withContext(Dispatchers.IO) {
                        dao.insertContentBlocks(contentBlocks)
                        contentBlocks.clear()
                    }
                }
                
                if (medications.size >= BATCH_SIZE) {
                    withContext(Dispatchers.IO) {
                        dao.insertMedications(medications)
                        medications.clear()
                    }
                }
            }
            
            // Insert any remaining data
            withContext(Dispatchers.IO) {
                if (conditions.isNotEmpty()) {
                    conditions.forEach { dao.insertCondition(it) }
                }
                if (contentBlocks.isNotEmpty()) {
                    dao.insertContentBlocks(contentBlocks)
                }
                if (medications.isNotEmpty()) {
                    dao.insertMedications(medications)
                }
            }
            
            // Clean up temp files
            parser.cleanupTempFiles()
            
            // Get final statistics
            val stats = getStatistics()
            
            emit(ProcessingProgress(
                currentPage = stats.totalContentBlocks, // Using content blocks as proxy for pages processed
                totalPages = stats.totalContentBlocks,
                currentChapter = null,
                message = "PDF processing complete! Processed ${stats.totalChapters} chapters, ${stats.totalConditions} conditions, ${stats.totalMedications} medications"
            ))
            
            Log.d(TAG, "STG PDF processing completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing STG PDF", e)
            
            // Clean up temp files on error
            try {
                parser.cleanupTempFiles()
            } catch (cleanupError: Exception) {
                Log.e(TAG, "Error cleaning up temp files", cleanupError)
            }
            
            emit(ProcessingProgress(
                0, 0, null,
                "Error: ${e.message}"
            ))
            throw e
        }
    }
    
    /**
     * Clear all existing STG data from database
     */
    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Clearing existing STG data")
        // The cascade delete should handle related tables
        dao.deleteAllChapters()
    }
    
    /**
     * Get processing statistics
     */
    suspend fun getStatistics(): ProcessingStatistics = withContext(Dispatchers.IO) {
        ProcessingStatistics(
            totalChapters = dao.getChapterCount(),
            totalConditions = dao.getConditionCount(),
            totalMedications = dao.getMedicationCount(),
            totalContentBlocks = dao.getContentBlockCount()
        )
    }
    
    data class ProcessingStatistics(
        val totalChapters: Int,
        val totalConditions: Int,
        val totalMedications: Int,
        val totalContentBlocks: Int
    )
    
    /**
     * Extract keywords from text for search optimization
     */
    private fun extractKeywords(text: String): String {
        val keywords = text.lowercase()
            .split(Regex("[\\s,;.!?]+"))
            .filter { it.length > 3 }
            .distinct()
            .take(20)
        
        return keywords.joinToString(",")
    }
}