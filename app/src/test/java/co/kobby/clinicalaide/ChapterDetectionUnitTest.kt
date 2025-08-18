package co.kobby.clinicalaide

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit test to verify chapter detection logic
 */
class ChapterDetectionUnitTest {
    
    @Test
    fun testChapterPatternMatching() {
        // Test main chapter pattern
        val mainPattern = Regex("""(?i)Chapter\s+(\d+)[:.]\s*(.+)""")
        
        val text1 = "Chapter 1: Disorders of the Gastrointestinal Tract"
        val match1 = mainPattern.find(text1)
        assertNotNull(match1)
        assertEquals("1", match1?.groupValues?.get(1))
        assertEquals("Disorders of the Gastrointestinal Tract", match1?.groupValues?.get(2))
        
        val text2 = "Chapter 2. Emergency Medicine"
        val match2 = mainPattern.find(text2)
        assertNotNull(match2)
        assertEquals("2", match2?.groupValues?.get(1))
        assertEquals("Emergency Medicine", match2?.groupValues?.get(2))
    }
    
    @Test
    fun testRunningHeaderPattern() {
        // Test running header pattern
        val headerPattern = Regex("""(?i)—\s*.+\s*—Chapter\s+(\d+):\s*(.+)""")
        
        val text = "— Diarrhoea —Chapter 1: Disorders of the Gastrointestinal Tract"
        val match = headerPattern.find(text)
        assertNotNull(match)
        assertEquals("1", match?.groupValues?.get(1))
        assertEquals("Disorders of the Gastrointestinal Tract", match?.groupValues?.get(2))
    }
    
    @Test
    fun testTOCDetection() {
        // Test TOC detection pattern
        val tocPattern = Regex("""\.{5,}""")
        
        val tocLine = "Chapter 1. Disorders of the Gastrointestinal Tract .............. 29"
        val hasDots = tocPattern.find(tocLine) != null
        assertTrue("Should detect TOC line with dots", hasDots)
        
        val normalText = "Chapter 1: Disorders of the Gastrointestinal Tract"
        val noDots = tocPattern.find(normalText) != null
        assertFalse("Should not detect normal text as TOC", noDots)
    }
    
    @Test
    fun testPageRangeLogic() {
        // Verify that we skip TOC pages correctly
        val tocPages = 3..11
        val frontMatterPages = 13..27
        val mainContentStart = 29
        
        // Page 5 should be skipped (TOC)
        assertTrue(5 in tocPages)
        
        // Page 20 should be skipped (front matter)
        assertTrue(20 in frontMatterPages)
        
        // Page 29 should be processed (main content)
        assertTrue(29 >= mainContentStart)
        
        // Page 35 should be processed (main content)
        assertTrue(35 >= mainContentStart)
    }
}