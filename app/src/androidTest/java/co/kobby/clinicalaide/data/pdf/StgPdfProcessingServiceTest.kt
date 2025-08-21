package co.kobby.clinicalaide.data.pdf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.database.StgDatabase
import co.kobby.clinicalaide.data.database.dao.StgDao
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StgPdfProcessingServiceTest {
    
    private lateinit var database: StgDatabase
    private lateinit var dao: StgDao
    private lateinit var service: StgPdfProcessingService
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        database = Room.inMemoryDatabaseBuilder(
            context,
            StgDatabase::class.java
        ).build()
        
        dao = database.stgDao()
        service = StgPdfProcessingService(context, dao)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun testProcessSamplePdf() = runBlocking {
        // Process the sample PDF using the Flow-based approach
        val progressUpdates = mutableListOf<StgPdfProcessingService.ProcessingProgress>()
        
        val results = service.processStgPdf("stg_chapter_sample.pdf").toList()
        progressUpdates.addAll(results)
        
        // Verify we got progress updates
        Assert.assertTrue("Should receive progress updates", progressUpdates.isNotEmpty())
        
        // Check the final progress message
        val finalProgress = progressUpdates.last()
        Assert.assertTrue(
            "Final message should indicate completion",
            finalProgress.message.contains("complete", ignoreCase = true) ||
            finalProgress.message.contains("processed", ignoreCase = true)
        )
        
        // Verify database was populated
        val stats = service.getStatistics()
        Assert.assertTrue("Should have chapters", stats.totalChapters > 0)
        Assert.assertTrue("Should have medications", stats.totalMedications > 0)
        
        println("Processing statistics:")
        println("Chapters: ${stats.totalChapters}")
        println("Conditions: ${stats.totalConditions}")
        println("Medications: ${stats.totalMedications}")
        println("Content blocks: ${stats.totalContentBlocks}")
    }
    
    @Test
    fun testProgressTracking() = runBlocking {
        val progressUpdates = mutableListOf<StgPdfProcessingService.ProcessingProgress>()
        
        // Collect first few progress updates
        service.processStgPdf("stg_chapter_sample.pdf").collect { progress ->
            progressUpdates.add(progress)
            if (progressUpdates.size >= 3) {
                // Stop after collecting a few updates for testing
                return@collect
            }
        }
        
        // Verify progress tracking
        Assert.assertTrue("Should have multiple progress updates", progressUpdates.size >= 2)
        
        progressUpdates.forEach { progress ->
            Assert.assertNotNull("Progress should have message", progress.message)
            println("Progress: ${progress.currentPage}/${progress.totalPages} - ${progress.message}")
        }
    }
    
    @Test
    fun testDatabaseStatistics() = runBlocking {
        // Clear database first
        service.clearDatabase()
        
        val initialStats = service.getStatistics()
        Assert.assertEquals("Should start with no chapters", 0, initialStats.totalChapters)
        
        // Process PDF
        service.processStgPdf("stg_chapter_sample.pdf").toList()
        
        val finalStats = service.getStatistics()
        Assert.assertTrue("Should have chapters after processing", finalStats.totalChapters > 0)
        Assert.assertTrue("Should have content blocks", finalStats.totalContentBlocks > 0)
    }
    
    @Test
    fun testDataIntegrity() = runBlocking {
        // Process the PDF
        service.processStgPdf("stg_chapter_sample.pdf").toList()
        
        // Check data integrity
        val chapters = dao.getAllChapters()
        Assert.assertTrue("Should have chapters", chapters.isNotEmpty())
        
        val firstChapter = chapters.first()
        Assert.assertEquals("Should be Chapter 1", 1, firstChapter.chapterNumber)
        
        // Check for conditions associated with the chapter
        val conditions = dao.getConditionsByChapter(firstChapter.id)
        
        // Check for medications
        val medications = dao.getMedicationCount()
        Assert.assertTrue("Should have medications", medications > 0)
    }
    
    @Test
    fun testClearDatabase() = runBlocking {
        // First populate some data
        service.processStgPdf("stg_chapter_sample.pdf").toList()
        
        val statsBeforeClear = service.getStatistics()
        Assert.assertTrue("Should have data before clear", statsBeforeClear.totalChapters > 0)
        
        // Clear the database
        service.clearDatabase()
        
        val statsAfterClear = service.getStatistics()
        Assert.assertEquals("Should have no chapters after clear", 0, statsAfterClear.totalChapters)
        Assert.assertEquals("Should have no conditions after clear", 0, statsAfterClear.totalConditions)
        Assert.assertEquals("Should have no medications after clear", 0, statsAfterClear.totalMedications)
    }
    
    @Test
    fun testErrorHandling() = runBlocking {
        val progressUpdates = mutableListOf<StgPdfProcessingService.ProcessingProgress>()
        
        try {
            service.processStgPdf("non_existent_file.pdf").collect { progress ->
                progressUpdates.add(progress)
            }
            Assert.fail("Should throw exception for non-existent file")
        } catch (e: Exception) {
            // Expected behavior - any exception is fine for a non-existent file
            Assert.assertNotNull("Should get an exception", e)
            println("Got expected error: ${e.message}")
        }
    }
}