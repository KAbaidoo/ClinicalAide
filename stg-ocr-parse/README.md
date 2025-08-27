# Ghana STG Document Extraction Pipeline

## Overview
This project extracts structured content from the Ghana Standard Treatment Guidelines (STG) 7th Edition (2017) PDF document. The pipeline uses PyMuPDF for direct text extraction (no OCR needed) and populates a SQLite database with hierarchically structured medical content.

## Current Status (2025-08-27)
- ✅ **Full extraction complete**: All 664 pages (29-692) processed
- ✅ **All 23 chapters identified and populated** in database
- ✅ **831 sections** extracted with proper hierarchy
- ✅ **957 metadata entries** automatically extracted
- ✅ **Database ready** for semantic search implementation

## Project Structure

### Core Scripts
- `pymupdf_extractor.py` - Main extraction engine using PyMuPDF (processes ~200 pages/minute)
- `populate_db_correct_schema.py` - Direct database population with user-specified schema
- `text_cleaner.py` - Text cleaning utilities for extraction artifacts
- `table_formatter.py` - Table detection and ASCII formatting

### Data Files
- `GHANA-STG-2017-1.pdf` - Source document (708 pages)
- `stg_rag.db` - SQLite database with structured content for RAG implementation
- `pymupdf_output/` - Extracted content and individual page files

## Database Schema

```sql
-- Table to store document chapters
CREATE TABLE chapters (
    chapter_id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_number TEXT NOT NULL,  -- e.g., "Chapter 1"
    chapter_title TEXT NOT NULL     -- e.g., "Disorders of the Gastrointestinal Tract"
);

-- Table to store sections within chapters
CREATE TABLE sections (
    section_id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_id INTEGER NOT NULL,
    section_number TEXT,           -- e.g., "1.2" or "A" for subsections
    section_title TEXT NOT NULL,   -- e.g., "Malaria"
    parent_section_id INTEGER,     -- For nested subsections
    FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id),
    FOREIGN KEY (parent_section_id) REFERENCES sections(section_id)
);

-- Table to store treatment content
CREATE TABLE content (
    content_id INTEGER PRIMARY KEY AUTOINCREMENT,
    section_id INTEGER NOT NULL,
    page_number INTEGER NOT NULL,
    content_text TEXT NOT NULL,
    content_type TEXT NOT NULL,    -- "paragraph", "bullet", "table", "note"
    FOREIGN KEY (section_id) REFERENCES sections(section_id)
);

-- Table to store embeddings for semantic search
CREATE TABLE embeddings (
    embedding_id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_id INTEGER NOT NULL,
    embedding BLOB NOT NULL,
    FOREIGN KEY (content_id) REFERENCES content(content_id)
);

-- Table to store metadata
CREATE TABLE metadata (
    metadata_id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_id INTEGER NOT NULL,
    key TEXT NOT NULL,            -- e.g., "target_population", "severity"
    value TEXT NOT NULL,          -- e.g., "children", "severe"
    FOREIGN KEY (content_id) REFERENCES content(content_id)
);
```

## Quick Start

### Full Pipeline (Recommended)
```bash
# Extract all pages and populate database
python3 pymupdf_extractor.py && python3 populate_db_correct_schema.py
```

### Individual Steps
```bash
# 1. Extract content from PDF (pages 29-692)
python3 pymupdf_extractor.py

# 2. Populate database with extracted content
python3 populate_db_correct_schema.py

# 3. Query the database
sqlite3 stg_rag.db "SELECT * FROM chapters;"
```

## Document Structure

The Ghana STG contains 23 chapters covering comprehensive medical treatment guidelines:

1. **Disorders of the Gastrointestinal Tract** (p. 29)
2. **Disorders of the Liver** (p. 51)
3. **Nutritional Disorders** (p. 76)
4. **Haematological Disorders** (p. 80)
5. **Immunisable Diseases** (p. 98)
6. **Problems of the Newborn** (p. 115)
7. **Cardiovascular System** (p. 131)
8. **Respiratory System** (p. 185)
9. **Disorders of the Central Nervous System** (p. 211)
10. **Psychiatric Disorders** (p. 225)
11. **Disorders of the Skin** (p. 263)
12. **Endocrine and Metabolic Disorders** (p. 305)
13. **Obstetric Care and Obstetric Disorders** (p. 332)
14. **Gynaecological Disorders** (p. 376)
15. **Disorders of the Kidney and Genitourinary System** (p. 406)
16. **Sexually Transmitted Infections** (p. 460)
17. **HIV Infections and AIDS** (p. 479)
18. **Infectious Diseases and Infestations** (p. 486)
19. **Eye Disorders** (p. 521)
20. **Ear, Nose and Throat Disorders** (p. 533)
21. **Oral and Dental Conditions** (p. 552)
22. **Disorders Of The Musculoskeletal System** (p. 570)
23. **Trauma And Injuries** (p. 600)

## Features

### Text Extraction
- Direct text extraction from embedded PDF text (no OCR required)
- Preserves formatting and structure
- Handles tables with ASCII formatting
- Cleans common extraction artifacts

### Content Processing
- Automatic chapter and section detection
- Hierarchical section organization
- Content type classification (paragraph, bullet, table, note)
- Metadata extraction (target population, severity, treatment type)

### Database Features
- Hierarchical content structure
- Full-text searchable content
- Ready for embedding generation and semantic search
- Indexed for fast queries

## Performance

- **Extraction Speed**: ~200 pages/minute
- **Database Population**: ~3-4 seconds for entire document
- **Total Processing Time**: Under 5 minutes for complete pipeline
- **Storage**: ~25MB extracted JSON, ~1MB database

## Requirements

- Python 3.x
- PyMuPDF (`pip install pymupdf`)
- SQLite3 (included with Python)

## Next Steps

1. **Generate embeddings** for semantic search capabilities
2. **Build search API** for querying medical guidelines
3. **Create web interface** for browsing and searching content
4. **Add medication extraction** with dosage parsing

## License

This extraction pipeline is for educational and healthcare purposes. The Ghana STG document remains the property of the Ghana Ministry of Health.