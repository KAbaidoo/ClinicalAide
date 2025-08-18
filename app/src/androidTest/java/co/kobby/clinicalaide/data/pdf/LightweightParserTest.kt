package co.kobby.clinicalaide.data.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the lightweight parser using a sample text file
 * This avoids memory issues while testing the parsing logic
 */
@RunWith(AndroidJUnit4::class)
class LightweightParserTest {
    
    @Test
    fun testParseTextFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = LightweightStgParser()
        
        val chapters = mutableListOf<LightweightStgParser.ChapterInfo>()
        val pages = mutableListOf<LightweightStgParser.ParsedPage>()
        
        // Parse sample text file
        context.assets.open("sample_stg.txt").use { inputStream ->
            parser.parseTextFile(
                inputStream = inputStream,
                onChapterFound = { chapter ->
                    chapters.add(chapter)
                    println("Found chapter: ${chapter.chapterNumber}. ${chapter.title}")
                },
                onPageProcessed = { page ->
                    pages.add(page)
                    if (page.conditions.isNotEmpty() || page.medications.isNotEmpty()) {
                        println("Page ${page.pageNumber}:")
                        println("  Conditions: ${page.conditions.map { it.name }}")
                        println("  Medications: ${page.medications.map { "${it.name} ${it.dosage}" }}")
                    }
                }
            )
        }
        
        // Verify chapters were found
        assertThat(chapters).isNotEmpty()
        assertThat(chapters.any { it.title.contains("Gastrointestinal") }).isTrue()
        assertThat(chapters.any { it.title.contains("Cardiovascular") }).isTrue()
        
        // Verify pages were processed
        assertThat(pages).isNotEmpty()
        
        // Verify conditions were extracted
        val allConditions = pages.flatMap { it.conditions }
        assertThat(allConditions.any { it.name.contains("Diarrhoea") }).isTrue()
        assertThat(allConditions.any { it.name.contains("Hypertension") }).isTrue()
        
        // Verify medications were extracted
        val allMedications = pages.flatMap { it.medications }
        assertThat(allMedications).isNotEmpty()
        
        // Check for specific medications
        val hasORS = allMedications.any { it.name.contains("ORS", ignoreCase = true) }
        val hasParacetamol = allMedications.any { it.name.contains("Paracetamol", ignoreCase = true) }
        val hasMetronidazole = allMedications.any { it.name.contains("Metronidazole", ignoreCase = true) }
        val hasAmlodipine = allMedications.any { it.name.contains("Amlodipine", ignoreCase = true) }
        
        println("\nExtraction Summary:")
        println("Chapters found: ${chapters.size}")
        println("Pages processed: ${pages.size}")
        println("Total conditions: ${allConditions.size}")
        println("Total medications: ${allMedications.size}")
        println("Sample medications: ${allMedications.take(5).map { "${it.name} ${it.dosage}" }}")
        
        // At least some medications should be found
        assertThat(hasParacetamol || hasMetronidazole || hasAmlodipine).isTrue()
    }
    
    @Test
    fun testMemoryEfficientProcessing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = LightweightStgParser()
        val runtime = Runtime.getRuntime()
        
        // Record initial memory
        System.gc()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        println("Initial memory: ${initialMemory / 1024 / 1024} MB")
        
        var maxMemoryUsed = initialMemory
        var pagesProcessed = 0
        
        // Process sample file
        context.assets.open("sample_stg.txt").use { inputStream ->
            parser.parseTextFile(
                inputStream = inputStream,
                onChapterFound = { /* ignore */ },
                onPageProcessed = { page ->
                    pagesProcessed++
                    
                    // Check memory usage
                    val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                    maxMemoryUsed = maxOf(maxMemoryUsed, currentMemory)
                    
                    if (pagesProcessed % 2 == 0) {
                        println("After page $pagesProcessed: ${currentMemory / 1024 / 1024} MB")
                    }
                }
            )
        }
        
        val memoryIncrease = maxMemoryUsed - initialMemory
        println("Memory increase: ${memoryIncrease / 1024 / 1024} MB")
        println("Pages processed: $pagesProcessed")
        
        // Memory increase should be minimal for text file
        assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024) // Less than 10MB
        assertThat(pagesProcessed).isGreaterThan(0)
    }
}