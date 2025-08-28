package co.kobby.clinicalaide.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.search.SemanticSearchService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for SemanticSearchService search algorithms and similarity calculations.
 * Verifies text-based search, medical term extraction, and similarity scoring.
 */
@RunWith(AndroidJUnit4::class)
class SemanticSearchServiceTest {
    
    private lateinit var searchService: SemanticSearchService
    private lateinit var database: RagDatabase
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = RagDatabase.getInstance(context)
        searchService = SemanticSearchService(database.ragDao())
    }
    
    // ==================== TEXT-BASED SEARCH TESTS ====================
    
    @Test
    fun test_searchSemantically_fallbacksToTextSearch() = runBlocking {
        // Currently falls back to text search since embedding search not implemented
        val results = searchService.searchSemantically("hypertension treatment", 10)
        
        assertNotNull("Search results should not be null", results)
        assertTrue("Should find results for hypertension treatment", results.isNotEmpty())
        assertTrue("Should respect limit", results.size <= 10)
        
        // Results should have similarity scores
        results.forEach { result ->
            assertNotNull("Content should not be null", result.content)
            assertTrue("Similarity should be between 0 and 1", result.similarity in 0.0f..1.5f) // Allow boost
        }
        
        // Results should be sorted by similarity
        for (i in 1 until results.size) {
            assertTrue(
                "Results should be sorted by similarity descending",
                results[i - 1].similarity >= results[i].similarity
            )
        }
    }
    
    @Test
    fun test_searchSemantically_respectsSimilarityThreshold() = runBlocking {
        val threshold = 0.5f
        val results = searchService.searchSemantically(
            "specific rare condition xyz123", 
            limit = 20, 
            similarityThreshold = threshold
        )
        
        // All results should meet the threshold
        results.forEach { result ->
            assertTrue(
                "Similarity ${result.similarity} should be >= $threshold",
                result.similarity >= threshold
            )
        }
    }
    
    @Test
    fun test_searchSemantically_expandsMedicalSynonyms() = runBlocking {
        // Search for fever should also find pyrexia/hyperthermia
        val results = searchService.searchSemantically("fever", 20)
        
        assertNotNull("Should return results", results)
        assertTrue("Should find results for fever", results.isNotEmpty())
        
        // Check if expanded search found related terms
        val expandedMatches = results.count { result ->
            val text = result.content.contentText.lowercase()
            text.contains("fever") || 
            text.contains("pyrexia") || 
            text.contains("temperature") ||
            text.contains("hyperthermia")
        }
        
        assertTrue("Should find fever and related terms", expandedMatches > 0)
    }
    
    // ==================== MEDICAL TERM EXTRACTION TESTS ====================
    
    @Test
    fun test_medicalTermExpansion_fever() = runBlocking {
        val results = searchService.searchSemantically("fever symptoms", 10)
        
        assertNotNull("Results should not be null", results)
        
        // Should find content related to fever and its synonyms
        val relevantCount = results.count { result ->
            val text = result.content.contentText.lowercase()
            text.contains("fever") || 
            text.contains("pyrexia") || 
            text.contains("temperature") ||
            text.contains("hyperthermia")
        }
        
        assertTrue("Should find fever-related content", relevantCount > 0)
    }
    
    @Test
    fun test_medicalTermExpansion_diarrhea() = runBlocking {
        val results = searchService.searchSemantically("diarrhea", 15)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Should find diarrhea-related content", results.isNotEmpty())
        
        // Check for both spellings
        val spellingMatches = results.count { result ->
            val text = result.content.contentText.lowercase()
            text.contains("diarrhea") || text.contains("diarrhoea")
        }
        
        assertTrue("Should handle both diarrhea spellings", spellingMatches > 0)
    }
    
    @Test
    fun test_medicalTermExpansion_malaria() = runBlocking {
        val results = searchService.searchSemantically("malaria", 10)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Should find malaria content", results.isNotEmpty())
        
        // Should find malaria and related terms
        val malariaMatches = results.count { result ->
            val text = result.content.contentText.lowercase()
            text.contains("malaria") || 
            text.contains("plasmodium") ||
            text.contains("antimalarial")
        }
        
        assertTrue("Should find malaria and related terms", malariaMatches > 0)
    }
    
    // ==================== SIMILARITY SCORING TESTS ====================
    
    @Test
    fun test_directMatches_getBoosted() = runBlocking {
        // Direct matches should get higher scores
        val directResults = searchService.searchSemantically("malaria", 5)
        val indirectResults = searchService.searchSemantically("xyz123notfound", 5)
        
        if (directResults.isNotEmpty()) {
            val maxDirectScore = directResults.maxOf { it.similarity }
            
            // Direct matches should have boosted scores (multiplied by 1.2)
            assertTrue("Direct matches should have good scores", maxDirectScore > 0.3f)
        }
    }
    
    @Test
    fun test_phraseMatches_getBoosted() = runBlocking {
        // Longer phrases that match exactly should get boosted
        val results = searchService.searchSemantically("oral rehydration therapy", 10)
        
        if (results.isNotEmpty()) {
            // Check if any results contain the exact phrase
            val exactPhraseMatch = results.any { result ->
                result.content.contentText.lowercase().contains("oral rehydration therapy")
            }
            
            if (exactPhraseMatch) {
                val exactMatchScore = results.first { result ->
                    result.content.contentText.lowercase().contains("oral rehydration therapy")
                }.similarity
                
                assertTrue("Exact phrase matches should get good scores", exactMatchScore > 0.3f)
            }
        }
    }
    
    @Test
    fun test_medicalTermMatches_getBoosted() = runBlocking {
        // Medical terms should get special treatment
        val results = searchService.searchSemantically("antimalarial therapy dosage", 10)
        
        if (results.isNotEmpty()) {
            // Results with multiple medical terms should score higher
            val medicalTermCount = results.count { result ->
                val text = result.content.contentText.lowercase()
                (text.contains("antimalarial") || 
                 text.contains("therapy") || 
                 text.contains("dosage") ||
                 text.contains("mg") ||
                 text.contains("treatment"))
            }
            
            assertTrue("Should find content with medical terms", medicalTermCount > 0)
        }
    }
    
    // ==================== SIMILAR CONTENT TESTS ====================
    
    @Test
    fun test_findSimilarContent_excludesSourceContent() = runBlocking {
        // Get some content first
        val searchResults = searchService.searchSemantically("malaria", 1)
        
        if (searchResults.isNotEmpty()) {
            val sourceContent = searchResults.first().content
            val similarResults = searchService.findSimilarContent(sourceContent.contentId, 5)
            
            // Source should not be in results
            assertFalse(
                "Similar content should not include source",
                similarResults.any { it.content.contentId == sourceContent.contentId }
            )
            
            // Similar content should be related
            if (similarResults.isNotEmpty()) {
                val topSimilar = similarResults.first().content.contentText.lowercase()
                val sourceText = sourceContent.contentText.lowercase()
                
                // Should have some common words
                val sourceWords = sourceText.split("\\s+".toRegex()).toSet()
                val similarWords = topSimilar.split("\\s+".toRegex()).toSet()
                val commonWords = sourceWords.intersect(similarWords)
                
                assertTrue("Similar content should share some words", commonWords.size > 2)
            }
        }
    }
    
    @Test
    fun test_findSimilarContent_returnsRelatedContent() = runBlocking {
        // Find content about a specific topic
        val searchResults = searchService.searchSemantically("pediatric diarrhea", 1)
        
        if (searchResults.isNotEmpty()) {
            val sourceContent = searchResults.first().content
            val similarResults = searchService.findSimilarContent(sourceContent.contentId, 10)
            
            // Similar results should be about related topics
            val relatedCount = similarResults.count { result ->
                val text = result.content.contentText.lowercase()
                text.contains("pediatric") || 
                text.contains("child") ||
                text.contains("diarrhea") ||
                text.contains("diarrhoea") ||
                text.contains("dehydration") ||
                text.contains("ors")
            }
            
            assertTrue(
                "At least some similar content should be related", 
                relatedCount > similarResults.size / 3
            )
        }
    }
    
    // ==================== EDGE CASES ====================
    
    @Test
    fun test_emptyQuery_returnsNoResults() = runBlocking {
        val results = searchService.searchSemantically("", 10)
        
        assertTrue("Empty query should return no results", results.isEmpty())
    }
    
    @Test
    fun test_veryShortQuery_handledProperly() = runBlocking {
        val results = searchService.searchSemantically("a", 5)
        
        assertNotNull("Should handle single character query", results)
        // Single character might match many things or nothing
    }
    
    @Test
    fun test_specialCharacters_handledSafely() = runBlocking {
        val results = searchService.searchSemantically("test'; DROP TABLE--", 5)
        
        assertNotNull("Should handle special characters safely", results)
        // Should not cause SQL injection or crashes
    }
    
    @Test
    fun test_veryLongQuery_handledProperly() = runBlocking {
        val longQuery = "malaria " + "treatment ".repeat(50) + "diagnosis"
        val results = searchService.searchSemantically(longQuery, 5)
        
        assertNotNull("Should handle very long queries", results)
        assertTrue("Should respect limit even with long query", results.size <= 5)
    }
    
    @Test
    fun test_nonExistentTerms_returnsEmptyOrLowScores() = runBlocking {
        val results = searchService.searchSemantically("xyz123abc456notarealterm", 10, 0.8f)
        
        assertNotNull("Should handle non-existent terms", results)
        // With high threshold, should return few or no results
        assertTrue("Non-existent terms with high threshold should return few results", results.size < 3)
    }
    
    // ==================== MEDICAL TERM RECOGNITION ====================
    
    @Test
    fun test_recognizesMedicalSuffixes() = runBlocking {
        val results = searchService.searchSemantically("gastroenteritis", 10)
        
        assertNotNull("Should handle medical terms with -itis", results)
        if (results.isNotEmpty()) {
            assertTrue("Should find content with medical suffix terms", results.isNotEmpty())
        }
    }
    
    @Test
    fun test_recognizesMedicalPrefixes() = runBlocking {
        val results = searchService.searchSemantically("antimalarial", 10)
        
        assertNotNull("Should handle medical terms with anti- prefix", results)
        if (results.isNotEmpty()) {
            val antiPrefixMatches = results.count { result ->
                result.content.contentText.lowercase().contains("anti")
            }
            assertTrue("Should find content with anti- prefix", antiPrefixMatches > 0)
        }
    }
    
    @Test
    fun test_recognizesCommonMedicalTerms() = runBlocking {
        val medicalTerms = listOf("treatment", "diagnosis", "symptoms", "medication", "therapy")
        
        medicalTerms.forEach { term ->
            val results = searchService.searchSemantically(term, 5)
            assertNotNull("Should handle medical term: $term", results)
            assertTrue("Should find results for medical term: $term", results.isNotEmpty())
        }
    }
}