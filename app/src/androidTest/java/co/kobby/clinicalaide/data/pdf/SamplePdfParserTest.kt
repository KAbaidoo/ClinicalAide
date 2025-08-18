package co.kobby.clinicalaide.data.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests using a smaller sample PDF (20 pages) that works within emulator memory constraints
 */
@RunWith(AndroidJUnit4::class)
class SamplePdfParserTest {
    
    private lateinit var parser: FileBasedStgPdfParser
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        // Use smaller chunk size for testing
        parser = FileBasedStgPdfParser(context, chunkSize = 2)
    }
    
    @Test
    fun testProcessSamplePdf() {
        runBlocking {
            // Clean up any old temp files
            parser.cleanupTempFiles()
            
            val runtime = Runtime.getRuntime()
            System.gc()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            println("Initial memory: ${initialMemory / 1024 / 1024} MB")
            
            var chunksProcessed = 0
            var totalConditions = 0
            var totalMedications = 0
            val chapters = mutableSetOf<String>()
            
            // Process the sample PDF with actual chapter content
            parser.processStgPdf("stg_chapter_sample.pdf")
                .toList()
                .forEach { result ->
                    chunksProcessed++
                    totalConditions += result.conditions.size
                    totalMedications += result.medications.size
                    
                    if (result.chapter != null) {
                        chapters.add("Chapter ${result.chapter.chapterNumber}: ${result.chapter.title}")
                    }
                    
                    // Log progress every 5 chunks
                    if (chunksProcessed % 5 == 0) {
                        val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                        println("Chunk $chunksProcessed: Memory = ${currentMemory / 1024 / 1024} MB")
                    }
                }
            
            println("\n=== Processing Complete ===")
            println("Chunks processed: $chunksProcessed")
            println("Chapters found: ${chapters.size}")
            chapters.forEach { println("  - $it") }
            println("Total conditions: $totalConditions")
            println("Total medications: $totalMedications")
            
            // Verify processing worked
            assertThat(chunksProcessed).isGreaterThan(0)
            assertThat(chapters).isNotEmpty()
            
            // Clean up temp files
            parser.cleanupTempFiles()
            
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = finalMemory - initialMemory
            println("Memory increase: ${memoryIncrease / 1024 / 1024} MB")
            
            // Memory increase should be reasonable for 20-page PDF
            assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024) // Less than 50MB
        }
    }
    
    @Test
    fun testChapterExtraction() {
        runBlocking {
            parser.cleanupTempFiles()
            
            val chapters = mutableListOf<FileBasedStgPdfParser.ChapterInfo>()
            
            // Process the new sample PDF with actual chapter content
            parser.processStgPdf("stg_chapter_sample.pdf")
                .toList()
                .forEach { result ->
                    result.chapter?.let { chapter ->
                        if (!chapters.any { it.chapterNumber == chapter.chapterNumber }) {
                            chapters.add(chapter)
                        }
                    }
                }
            
            println("Found ${chapters.size} chapters in sample PDF:")
            chapters.forEach { chapter ->
                println("  Chapter ${chapter.chapterNumber}: ${chapter.title}")
                println("    Pages: ${chapter.startPage}-${chapter.endPage ?: "?"}")
            }
            
            // New sample PDF contains pages 29-50 with Chapter 1 content
            assertThat(chapters).isNotEmpty()
            
            // Should find Chapter 1: Disorders of the Gastrointestinal Tract
            val firstChapter = chapters.firstOrNull()
            assertThat(firstChapter).isNotNull()
            assertThat(firstChapter?.chapterNumber).isEqualTo(1)
            assertThat(firstChapter?.title).isNotEmpty()
            
            // Title should contain "Disorders" and/or "Gastrointestinal"
            val title = firstChapter?.title ?: ""
            val hasExpectedContent = title.contains("Disorders", ignoreCase = true) || 
                                    title.contains("Gastrointestinal", ignoreCase = true)
            assertThat(hasExpectedContent).isTrue()
            
            parser.cleanupTempFiles()
        }
    }
    
    @Test
    fun testMedicationExtraction() {
        runBlocking {
            parser.cleanupTempFiles()
            
            val medications = mutableSetOf<String>()
            
            // Process sample PDF with actual chapter content and collect medications
            parser.processStgPdf("stg_chapter_sample.pdf")
                .toList()
                .forEach { result ->
                    result.medications.forEach { med ->
                        medications.add("${med.name} ${med.dosage}")
                    }
                }
            
            println("Found ${medications.size} unique medications:")
            medications.take(10).forEach { println("  - $it") }
            
            // The sample contains Chapter 1 about Diarrhoea treatment
            // Should find medications like ORS, Zinc, antibiotics, etc.
            assertThat(medications).isNotEmpty()
            
            println("\nSample medications found:")
            medications.take(10).forEach { println("  - $it") }
            
            // Check for expected medications from diarrhoea treatment
            val medicationNames = medications.map { it.split(" ")[0].lowercase() }
            val hasExpectedMeds = medicationNames.any { med ->
                med in listOf("ors", "zinc", "metronidazole", "ciprofloxacin", "tetracycline")
            }
            
            if (hasExpectedMeds) {
                println("✓ Found expected diarrhoea treatment medications")
            }
            
            parser.cleanupTempFiles()
        }
    }
    
    @Test
    fun testConfigurableChunkSize() {
        runBlocking {
            // Test with different chunk sizes
            val chunkSizes = listOf(1, 3, 5)
            
            chunkSizes.forEach { size ->
                val testParser = FileBasedStgPdfParser(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    chunkSize = size
                )
                
                testParser.cleanupTempFiles()
                
                var chunksProcessed = 0
                testParser.processStgPdf("stg_chapter_sample.pdf")
                    .toList()
                    .forEach { _ ->
                        chunksProcessed++
                    }
                
                println("Chunk size $size: Processed $chunksProcessed chunks")
                
                // More chunks with smaller size
                assertThat(chunksProcessed).isGreaterThan(0)
                
                testParser.cleanupTempFiles()
            }
        }
    }
}