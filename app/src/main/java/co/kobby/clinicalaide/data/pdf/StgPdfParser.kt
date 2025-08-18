package co.kobby.clinicalaide.data.pdf

import co.kobby.clinicalaide.data.pdf.extractors.ChapterExtractor
import co.kobby.clinicalaide.data.pdf.extractors.ConditionExtractor
import co.kobby.clinicalaide.data.pdf.extractors.MedicationExtractor
import co.kobby.clinicalaide.data.pdf.models.ParsedChapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Original PDF parser implementation
 * @deprecated Use FileBasedStgPdfParser for memory-efficient processing
 */
@Deprecated("Use FileBasedStgPdfParser for memory-efficient processing", 
    ReplaceWith("FileBasedStgPdfParser(context)"))
class StgPdfParser(
    private val chapterExtractor: ChapterExtractor = ChapterExtractor(),
    private val conditionExtractor: ConditionExtractor = ConditionExtractor(),
    private val medicationExtractor: MedicationExtractor = MedicationExtractor()
) {
    
    suspend fun parsePdf(inputStream: InputStream): List<ParsedChapter> = withContext(Dispatchers.IO) {
        PDDocument.load(inputStream).use { document ->
            parsePdfDocument(document)
        }
    }
    
    suspend fun parsePdf(file: File): List<ParsedChapter> = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { document ->
            parsePdfDocument(document)
        }
    }
    
    private suspend fun parsePdfDocument(document: PDDocument): List<ParsedChapter> {
        val chapters = chapterExtractor.extractChapters(document)
        
        return chapters.map { chapter ->
            val conditions = conditionExtractor.extractConditions(chapter)
            val conditionsWithMedications = conditions.map { condition ->
                val medications = medicationExtractor.extractMedications(condition)
                condition.copy(medications = medications)
            }
            chapter.copy(conditions = conditionsWithMedications)
        }
    }
    
    suspend fun parseChapterRange(
        inputStream: InputStream,
        startChapter: Int,
        endChapter: Int
    ): List<ParsedChapter> = withContext(Dispatchers.IO) {
        PDDocument.load(inputStream).use { document ->
            val allChapters = parsePdfDocument(document)
            allChapters.filter { it.chapterNumber in startChapter..endChapter }
        }
    }
    
    suspend fun parseSingleChapter(
        inputStream: InputStream,
        chapterNumber: Int
    ): ParsedChapter? = withContext(Dispatchers.IO) {
        PDDocument.load(inputStream).use { document ->
            val allChapters = parsePdfDocument(document)
            allChapters.firstOrNull { it.chapterNumber == chapterNumber }
        }
    }
}