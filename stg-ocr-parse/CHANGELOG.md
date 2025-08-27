# Changelog

## [2.0.0] - 2025-08-27

### Major Changes
- **Complete Database Schema Redesign**
  - Implemented user-specified schema with proper foreign keys
  - New tables: chapters, sections, content, embeddings, metadata
  - Hierarchical section structure with parent_section_id
  - Content type classification (paragraph, bullet, table, note)

- **All 23 Chapters Identified and Populated**
  - Comprehensive chapter mapping from page 29 to 600
  - Each chapter properly linked to its sections
  - Accurate chapter titles extracted from document

### Added
- **New Database Population Script** (`populate_db_correct_schema.py`)
  - Direct database population bypassing slow parser
  - Automatic chapter detection with predefined mappings
  - Section and subsection hierarchy extraction
  - Metadata extraction (target population, severity, treatment type)
  - Content type classification

### Enhanced
- **Documentation Updates**
  - README.md completely rewritten with current status
  - CLAUDE.md updated with new schema and pipeline
  - Added comprehensive chapter listing
  - Performance metrics updated

### Database Statistics
- **23 chapters** (all document chapters)
- **831 sections** with proper hierarchy
- **664 content entries** with page references
- **957 metadata entries** extracted
- Database file: `stg_rag.db` (renamed for RAG implementation)

### Performance
- **Database Population**: ~3-4 seconds (massive improvement from parser)
- **Total Pipeline**: Under 5 minutes for complete document

## [1.0.0] - 2025-08-24

### Added
- **Table Formatter Module** (`table_formatter.py`)
  - ASCII table formatting with proper borders
  - Markdown and text block formatting options
  - False positive table filtering (Note boxes, chapter headers)
  - Cross-page table continuation detection
  
- **Batch Processing**
  - Memory-efficient processing in 100-page batches
  - Progress tracking with percentage completion
  - Full document processing capability (664 pages)

### Enhanced
- **Text Cleaning** (`text_cleaner.py`)
  - Improved header detection for all variations
  - Multi-line chapter header removal
  - Intelligent sentence merging with table preservation
  - Section header detection (A., B., C.)
  
- **Table Processing**
  - Note box filtering (e.g., "Note 1-1", "Note 1-2")
  - Cross-page table continuation labels
  - Mangled table text removal from content
  - Table region replacement in extracted text

### Fixed
- Headers contaminating content chunks
- Tables being incorrectly detected in graphics
- Table formatting lost during text merging
- Cross-page tables missing or malformed
- "Gastrointestinal Tract" header remnants
- Table introduction text concatenation issues

### Performance
- **Processing Speed**: ~200 pages/minute
- **Full Document**: 664 pages in ~3-4 minutes
- **Memory Usage**: Optimized with batch processing
- **Accuracy**: Near-perfect text extraction with formatting preserved

### Statistics (Full Document Run)
- Pages processed: 664 (pages 29-692)
- Headers detected: 5,485
- Tables extracted: 302
- All content structured in SQLite database

## Previous Versions

### [0.9.0] - Initial Implementation
- Basic PyMuPDF extraction
- Simple text cleaning
- Database population
- Sample pages (29-40) processing only