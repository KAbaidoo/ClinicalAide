# Ghana STG Document Extraction Pipeline

## Overview
This project extracts structured content from the Ghana Standard Treatment Guidelines (STG) 7th Edition (2017) PDF document. The pipeline uses PyMuPDF for direct text extraction (no OCR needed) and populates a SQLite database with hierarchically structured medical content.

## Current Status (2025-08-29)
- ✅ **Full extraction complete**: All 664 pages (29-692) processed
- ✅ **All 23 chapters identified and populated** in database
- ✅ **831 sections** extracted with proper hierarchy
- ✅ **957 metadata entries** automatically extracted
- ✅ **664 content embeddings generated** using all-MiniLM-L6-v2 model
- ✅ **129 query embeddings added** for common medical queries
- ✅ **Database ready** with semantic search capabilities
- ✅ **Android Room compatible** - schema validation passed with 100% success
- ✅ **Semantic search validated** - 30-50% similarity scores for relevant queries

## Project Structure

### Core Scripts
- `pymupdf_extractor.py` - Main extraction engine using PyMuPDF (processes ~200 pages/minute)
- `populate_db_correct_schema.py` - Direct database population with user-specified schema
- `generate_embeddings.py` - Generate vector embeddings for semantic search
- `generate_query_embeddings.py` - Generate embeddings for common medical queries (NEW)
- `text_cleaner.py` - Text cleaning utilities for extraction artifacts
- `table_formatter.py` - Table detection and ASCII formatting

### Data Files
- `GHANA-STG-2017-1.pdf` - Source document (708 pages)
- `stg_rag.db` - SQLite database with structured content and embeddings (3.2MB)
- `query_embeddings.json` - Pre-computed embeddings for 129 common queries (1.4MB)
- `query_embeddings.bin` - Binary format for Android integration
- `pymupdf_output/` - Extracted content and individual page files

## Database Schema

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
    section_number TEXT,           -- e.g., "1.2" or "A" for subsections
    section_title TEXT NOT NULL,   -- e.g., "Malaria"
    parent_section_id INTEGER,     -- For nested subsections
    FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_section_id) REFERENCES sections(section_id) ON DELETE CASCADE
);

-- Table to store treatment content
CREATE TABLE content (
    content_id INTEGER NOT NULL PRIMARY KEY,
    section_id INTEGER NOT NULL,
    page_number INTEGER NOT NULL,
    content_text TEXT NOT NULL,
    content_type TEXT NOT NULL,    -- "paragraph", "bullet", "table", "note"
    FOREIGN KEY (section_id) REFERENCES sections(section_id) ON DELETE CASCADE
);

-- Table to store embeddings for semantic search
CREATE TABLE embeddings (
    embedding_id INTEGER NOT NULL PRIMARY KEY,
    content_id INTEGER NOT NULL,
    embedding BLOB NOT NULL,
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

-- Table for pre-computed query embeddings (NEW)
CREATE TABLE query_embeddings (
    query_id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_text TEXT NOT NULL UNIQUE,
    query_category TEXT,          -- e.g., "General Conditions", "Pediatric", "Emergency"
    embedding BLOB NOT NULL,
    usage_count INTEGER DEFAULT 0
);
```

## Quick Start

### Full Pipeline (Recommended)
```bash
# Extract all pages and populate database with embeddings
python3 pymupdf_extractor.py && python3 populate_db_correct_schema.py && python3 generate_embeddings.py

# Generate query embeddings for common medical queries
python3 generate_query_embeddings.py
```

### Individual Steps
```bash
# 1. Extract content from PDF (pages 29-692)
python3 pymupdf_extractor.py

# 2. Populate database with extracted content
python3 populate_db_correct_schema.py

# 3. Generate embeddings for semantic search
python3 generate_embeddings.py

# 4. Query the database
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
- **Embedding Generation**: ~4 seconds for 664 entries (after model download)
- **Total Processing Time**: Under 5 minutes for complete pipeline
- **Storage**: ~25MB extracted JSON, 3.2MB database with embeddings

## Requirements

- Python 3.x
- PyMuPDF (`pip install pymupdf`)
- sentence-transformers (`pip install sentence-transformers`)
- SQLite3 (included with Python)

## Embeddings

The database includes vector embeddings for semantic search:
- **Model**: all-MiniLM-L6-v2 (384 dimensions)
- **Total Embeddings**: 664 (one per content entry)
- **Storage**: 1.5KB per embedding (384 floats × 4 bytes)
- **Purpose**: Enable semantic similarity search for medical queries

## Android Integration

The database is fully compatible with Android Room ORM:
- ✅ **Schema validation** passed with 100% success
- ✅ **Foreign key constraints** properly defined with CASCADE deletes
- ✅ **Database ready** for Android app integration at `stg_rag.db` (3.18MB)
- ✅ **Embeddings functional** - semantic search tested and working

## Next Steps

1. ✅ **Database integration** - Complete (Android Room validated)
2. **Build chat interface** with Jetpack Compose
3. **Implement semantic search** in Android app using embeddings  
4. **Build RAG pipeline** for context-aware responses
5. **Add offline LLM** for response generation

## License

This extraction pipeline is for educational and healthcare purposes. The Ghana STG document remains the property of the Ghana Ministry of Health.