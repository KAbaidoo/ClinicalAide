# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a medical document extraction pipeline for the Ghana Standard Treatment Guidelines (STG) PDF. The document is 708 pages with embedded text (not scanned), allowing direct extraction via PyMuPDF without OCR.

## Key Document Characteristics

- **Main content**: Pages 29-692 (clinical guidelines)
- **Exclude**: Pages 1-28 (TOC, introduction), 693-708 (index)
- **Text type**: Embedded (not scanned) - use PyMuPDF direct extraction, NOT OCR
- **Processing speed**: ~200 pages/minute with PyMuPDF

## Processing Pipeline Commands

```bash
# Step 1: Analyze document structure (optional)
python3 pymupdf_analysis.py

# Step 2: Extract content (full document: pages 29-692)
python3 pymupdf_extractor.py

# Step 3: Populate database with correct schema
python3 populate_db_correct_schema.py

# Step 4: Generate embeddings for semantic search
python3 generate_embeddings.py

# Full pipeline for entire document
python3 pymupdf_extractor.py && python3 populate_db_correct_schema.py && python3 generate_embeddings.py
```

Note: The `enhanced_parser.py` is slow and has been replaced with direct database population via `populate_db_correct_schema.py`

## Architecture & Data Flow

### 1. Extraction Layer (`pymupdf_extractor.py`)
- **PyMuPDFExtractor** class extracts text using multiple methods:
  - `dict` method: Structured extraction with font/formatting info (preferred)
  - `blocks` method: Text organized by visual blocks
  - `text` method: Simple text extraction
- Integrates **TextCleaner** for automatic text cleaning
- Integrates **TableFormatter** for ASCII table formatting
- Handles cross-page table continuations
- Batch processing capability for full document
- Outputs to `pymupdf_output/extracted_content.json` and individual page files

### 2. Text Cleaning Layer (`text_cleaner.py`)
- **TextCleaner** class handles common extraction issues:
  - Removes all header variations (document title, chapter headers, page numbers)
  - Replaces "yy" bullet artifacts with proper "•" bullets
  - Removes section artifacts (e.g., "— Diarrhoea —")
  - Merges split sentences and paragraphs intelligently
  - Preserves ASCII table formatting
  - Detects and preserves section headers (A., B., C.)
- Automatically applied during extraction

### 3. Table Formatting Layer (`table_formatter.py`)
- **TableFormatter** class handles table extraction and formatting:
  - Validates tables to filter false positives (Note boxes, chapter headers)
  - Formats tables in multiple styles: ASCII (with borders), Markdown, Text block
  - Detects and filters Note boxes (e.g., "Note 1-1", "Note 1-2")
  - Handles cross-page table continuations with labels
  - Removes mangled table text from content while preserving formatted tables
- Integrated into PyMuPDFExtractor for automatic table processing

### 4. Parsing Layer (`enhanced_parser.py`)
- **EnhancedParser** class structures the cleaned extraction:
  - Identifies chapters by font size and patterns
  - Extracts sections (e.g., "8. Diarrhoea") and subsections
  - Categorizes content blocks: causes, symptoms, signs, investigations, treatment
  - Parses medication information and dosages
  - Works with cleaned text (bullet points as "•")
- Input: `pymupdf_output/extracted_content.json`
- Output: `pymupdf_output/parsed_structured_data.json`

### 5. Database Layer (`pymupdf_db_handler.py`)
- **PyMuPDFDatabaseHandler** populates SQLite database
- Hierarchical structure: chapters → sections → content
- Maintains parent-child relationships for subsections
- Stores medication and table data separately

## Database Schema (Current Implementation)

The database (`stg_rag.db`) uses the following schema:

```sql
-- Table to store document chapters
CREATE TABLE chapters (
    chapter_id INTEGER NOT NULL PRIMARY KEY,
    chapter_number TEXT NOT NULL,  -- e.g., "Chapter 1"
    chapter_title TEXT NOT NULL     -- e.g., "Disorders of the Gastrointestinal Tract"
);

-- Table to store sections within chapters
CREATE TABLE sections (
    section_id INTEGER NOT NULL PRIMARY KEY,
    chapter_id INTEGER NOT NULL,
    section_number TEXT,           -- e.g., "1.2" (nullable for unnumbered sections)
    section_title TEXT NOT NULL,   -- e.g., "Malaria"
    parent_section_id INTEGER,     -- For subsections (nullable, references section_id)
    FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_section_id) REFERENCES sections(section_id) ON DELETE CASCADE
);

-- Table to store treatment content
CREATE TABLE content (
    content_id INTEGER NOT NULL PRIMARY KEY,
    section_id INTEGER NOT NULL,
    page_number INTEGER NOT NULL,  -- Page number in the PDF
    content_text TEXT NOT NULL,    -- Actual text (e.g., treatment description)
    content_type TEXT NOT NULL,    -- e.g., "paragraph", "bullet", "table", "note"
    FOREIGN KEY (section_id) REFERENCES sections(section_id) ON DELETE CASCADE
);

-- Table to store embeddings for semantic search
CREATE TABLE embeddings (
    embedding_id INTEGER NOT NULL PRIMARY KEY,
    content_id INTEGER NOT NULL,
    embedding BLOB NOT NULL,       -- Binary data for the embedding vector
    FOREIGN KEY (content_id) REFERENCES content(content_id) ON DELETE CASCADE
);

-- Table to store metadata
CREATE TABLE metadata (
    metadata_id INTEGER NOT NULL PRIMARY KEY,
    content_id INTEGER NOT NULL,
    key TEXT NOT NULL,            -- e.g., "target_population", "severity"
    value TEXT NOT NULL,          -- e.g., "children", "severe"
    FOREIGN KEY (content_id) REFERENCES content(content_id) ON DELETE CASCADE
);
```

### Database Population Script (`populate_db_correct_schema.py`)
- Processes all 664 pages (29-692) from extracted content
- Identifies and inserts all 23 chapters
- Extracts sections and subsections with proper hierarchy
- Categorizes content by type (paragraph, bullet, table, note)
- Extracts metadata (target population, severity, treatment type)

## Important Patterns & Medical Content

### Section Detection Patterns
- Main sections: `^\d+\.\s+(.+)$` (e.g., "8. Diarrhoea")
- Subsections: `^[A-Z]\.\s+(.+)$` (e.g., "A. Bacterial gastroenteritis")
- Chapter headers: Identified by larger font size or "CHAPTER X" pattern

### Medical Content Categories
- **Causes**: Look for "causes", "etiology", "aetiology"
- **Symptoms**: "symptoms", "clinical features", "presentation"
- **Signs**: "signs", "physical examination"
- **Investigations**: "investigations", "diagnostic tests", "laboratory tests"
- **Treatment**: "treatment", "management", "therapy"
- **Medications**: Pattern includes drug name, route (oral/IV/IM), dose (mg/ml)

### Medication Extraction
Medications follow pattern: `[drug_name], [route], [dose]`
- Routes: oral, IV, IM, SC, topical, inhaled
- Doses: Include mg, ml, g, mcg, units, %
- Frequency: hourly, daily, bd, tds, qds

## Current State & Performance

### Implementation Status (2025-08-28)
- **✅ Full document extraction complete** (pages 29-692)
- **✅ All 23 chapters identified and populated**
- **✅ Database schema implemented per user specification**
- **✅ Embeddings generated** for all content
- **✅ Android Room validation** passed with 100% success
- **Processing time**: ~3-4 seconds for database population
- **Extraction speed**: ~200 pages/minute with PyMuPDF
- **Embedding generation**: ~4 seconds for 664 entries
- **Database statistics**:
  - 23 chapters (all document chapters)
  - 831 sections with proper hierarchy
  - 664 content entries
  - 957 metadata entries extracted
  - 664 embeddings (384 dimensions each)
  - Database size: 3.18MB with embeddings

### Chapter Structure
All 23 chapters have been identified and populated:
1. Disorders of the Gastrointestinal Tract (p. 29)
2. Disorders of the Liver (p. 51)
3. Nutritional Disorders (p. 76)
4. Haematological Disorders (p. 80)
5. Immunisable Diseases (p. 98)
6. Problems of the Newborn (p. 115)
7. Cardiovascular System (p. 131)
8. Respiratory System (p. 185)
9. Disorders of the Central Nervous System (p. 211)
10. Psychiatric Disorders (p. 225)
11. Disorders of the Skin (p. 263)
12. Endocrine and Metabolic Disorders (p. 305)
13. Obstetric Care and Obstetric Disorders (p. 332)
14. Gynaecological Disorders (p. 376)
15. Disorders of the Kidney and Genitourinary System (p. 406)
16. Sexually Transmitted Infections (p. 460)
17. HIV Infections and AIDS (p. 479)
18. Infectious Diseases and Infestations (p. 486)
19. Eye Disorders (p. 521)
20. Ear, Nose and Throat Disorders (p. 533)
21. Oral and Dental Conditions (p. 552)
22. Disorders Of The Musculoskeletal System (p. 570)
23. Trauma And Injuries (p. 600)

### Batch Processing Implementation
The extractor now includes:
- Automatic batch processing (100 pages at a time)
- Progress tracking with percentage completion
- Memory-efficient processing for large documents
- Individual page text file generation

## Improvements Made During Development

### Text Cleaning Enhancements
- **✅ Bullet point conversion**: "yy" artifacts replaced with proper "•" bullets
- **✅ Header removal**: All variations (document title, chapter headers, page numbers)
- **✅ Section artifacts**: "— Section Name —" patterns cleaned
- **✅ Sentence merging**: Intelligent paragraph reconstruction
- **✅ Table preservation**: ASCII table formatting maintained during text merging

### Table Processing Improvements
- **✅ False positive filtering**: Note boxes and chapter headers no longer detected as tables
- **✅ ASCII formatting**: Clean table borders using `+`, `-`, and `|` characters
- **✅ Cross-page handling**: Tables spanning pages properly labeled and continued
- **✅ Text region replacement**: Mangled table text removed while preserving formatted tables
- **✅ Note box detection**: Specific filtering for "Note X-X" boxes

### Processing Enhancements
- **✅ Batch processing**: Memory-efficient processing in 100-page batches
- **✅ Progress tracking**: Real-time percentage completion during extraction
- **✅ Error resilience**: Individual page errors don't stop full document processing
- **✅ Output organization**: Clean file structure in `pymupdf_output/` directory

## Known Limitations

1. **Medication parser**: Complex multi-line medication formats may not parse completely
2. **Table boundaries**: Some edge cases in cross-page table detection
3. **Section hierarchy**: Deeply nested subsections (beyond A., B., C.) need enhancement
4. **Index pages**: Pages 693-708 (index) not processed as they're outside main content

## Recent Fixes (2025-08-24)

### Fixed Alternating Header Pattern Issue
- **Problem**: Document has alternating headers on odd/even pages
  - Even pages: "Standard Treatment Guidelines, 7th Edition, 2017"
  - Odd pages: Chapter titles (e.g., "Chapter 1: Disorders of the Gastrointestinal Tract")
- **Solution**: 
  - Updated `text_cleaner.py` to detect and remove both header types
  - Modified `pymupdf_extractor.py` to apply cleaning before text processing
  - Headers now properly removed from all 664 pages

## Usage Instructions

### Quick Start
```bash
# Process entire document (pages 29-692) - RECOMMENDED PIPELINE
python3 pymupdf_extractor.py && python3 populate_db_correct_schema.py && python3 generate_embeddings.py
```

### Custom Page Range
To process a specific page range, modify `pymupdf_extractor.py`:
```python
# In main() function, change:
results = extractor.process_pages(29, 692)  # Full document
# To:
results = extractor.process_pages(100, 200)  # Custom range
```

### Configuration Options
- **Batch size**: Default 100 pages, adjustable in `process_pages()` method
- **Table formatting**: Choose between ASCII, Markdown, or text block in `TableFormatter`
- **Text cleaning**: Enable/disable via `use_cleaner` parameter in `PyMuPDFExtractor`

## File Outputs

- `pymupdf_output/extracted_content.json`: Complete extracted content with structure
- `pymupdf_output/parsed_structured_data.json`: Parsed medical content with categories
- `pymupdf_output/page_*.txt`: Individual page text files for review
- `stg_rag.db`: SQLite database with hierarchical content structure for RAG