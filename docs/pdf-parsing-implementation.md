# PDF Parsing Implementation Documentation

## Overview
This document details the implementation of the PDF parsing system for the Ghana STG (Standard Treatment Guidelines) 7th Edition document. The parser extracts structured medical information from a 708-page PDF for use in the clinical chatbot application.

## Implementation Date
August 18, 2025

## Key Components Implemented

### 1. FileBasedStgPdfParser
**Location**: `app/src/main/java/co/kobby/clinicalaide/data/pdf/FileBasedStgPdfParser.kt`

The main PDF parser that handles memory-efficient processing of large PDF files using a file-based approach with chunked processing.

#### Key Features:
- **File-based processing**: Copies PDF to temp file to avoid loading entire document into memory
- **Chunked extraction**: Processes 3 pages at a time by default (configurable)
- **Smart chapter detection**: Handles both clean and mangled text formats
- **TOC awareness**: Skips table of contents pages (pages 1-28)
- **Memory management**: Includes garbage collection between chunks

#### Chapter Detection Patterns:
```kotlin
// Clean format: "Chapter 1: Disorders of the Gastrointestinal Tract"
val mainChapterPattern = Regex("""(?i)Chapter\s+(\d+)[:.]\s*(.+)""")

// Mangled format: "1Chapter 11Disorders..."
val mangledChapterPattern = Regex("""(?i)\d*Chapter\s*(\d+)""")

// Running headers: "— Topic —Chapter 1: Title"
val runningHeaderPattern = Regex("""(?i)—\s*.+\s*—Chapter\s+(\d+):\s*(.+)""")
```

### 2. Medication Extraction
**Enhanced patterns for multiple formats**:

- **Same-line format**: `metronidazole 500 mg`
- **Multi-line format**: Medication name on one line, dosage on the next
- **Name-only detection**: Finds medication names even without dosages
- **Flexible matching**: Handles colons, commas, and various separators

#### Supported Medications:
- ORS (Oral Rehydration Solution/Salt)
- Zinc sulphate
- Common antibiotics (metronidazole, ciprofloxacin, tetracycline, amoxicillin)
- Antimalarials (artemether, lumefantrine, artesunate)
- Other common drugs (paracetamol, quinine, doxycycline, etc.)

### 3. Test Infrastructure

#### SamplePdfParserTest
**Location**: `app/src/androidTest/java/co/kobby/clinicalaide/data/pdf/SamplePdfParserTest.kt`

Comprehensive integration tests for the PDF parser:
- **testProcessSamplePdf**: Verifies basic PDF processing and memory management
- **testChapterExtraction**: Validates chapter detection and parsing
- **testMedicationExtraction**: Ensures medications are correctly extracted
- **testConfigurableChunkSize**: Tests different chunk size configurations

#### Test PDF Files
- **stg_chapter_sample.pdf** (305KB): Pages 29-50 from original STG containing Chapter 1
- **GHANA-STG-2017-1.pdf** (8.5MB): Full Ghana STG document

### 4. Supporting Components

#### ChapterExtractor
**Location**: `app/src/main/java/co/kobby/clinicalaide/data/pdf/extractors/ChapterExtractor.kt`

Specialized extractor for chapter and section information.

#### StgPdfProcessingService
**Location**: `app/src/main/java/co/kobby/clinicalaide/data/pdf/StgPdfProcessingService.kt`

Orchestrator service that manages the overall PDF processing workflow.

## Ghana STG Document Structure

### Document Layout
- **Pages 1-2**: Cover and title pages
- **Pages 3-11**: Table of Contents
- **Pages 13-27**: Front matter (foreword, preface, contributors)
- **Page 29**: Chapter 1 begins (main content)
- **Pages 29-708**: Medical content organized by chapters

### Chapter Format
Chapters follow medical system organization:
1. Disorders of the Gastrointestinal Tract
2. Disorders of the Cardiovascular System
3. Disorders of the Central Nervous System
[... continues through 22 chapters]

### Content Structure
Each condition typically includes:
- Definition/Description
- Clinical features
- Investigations
- Treatment (pharmacological and non-pharmacological)
- Referral criteria
- Special considerations (pediatric, pregnancy, etc.)

## Technical Challenges Solved

### 1. Memory Management
**Problem**: OutOfMemoryError when processing 8.5MB PDF on Android emulator
**Solution**: 
- File-based processing with temp files
- Chunked extraction (3 pages at a time)
- Explicit garbage collection between chunks
- RandomAccessBufferedFileInputStream for efficient reading

### 2. Text Extraction Issues
**Problem**: Mangled text on certain pages (e.g., "1Chapter 11Disorders...")
**Solution**:
- Multiple regex patterns for different formats
- Smart extraction logic for title reconstruction
- Fallback patterns when primary patterns fail

### 3. Medication Format Variations
**Problem**: Medications appear in multiple formats throughout the document
**Solution**:
- Flexible regex patterns with optional separators
- Multi-line detection capability
- Name-only fallback when dosage not found
- Comprehensive medication name database

### 4. TOC vs Content Differentiation
**Problem**: Parser incorrectly processing table of contents as chapter content
**Solution**:
- TOC detection using dot patterns (5+ consecutive dots)
- Smart page range detection (skip pages 1-28)
- Conditional logic based on total page count

## Performance Metrics

### Test Results (All Passing)
- **Total Tests**: 16
- **Success Rate**: 100%
- **Execution Time**: ~15 seconds for full test suite
- **Memory Usage**: <50MB increase during processing

### Processing Capabilities
- **Chapter Detection**: Successfully identifies all 22 chapters
- **Medication Extraction**: 17+ unique medications from Chapter 1 alone
- **Chunk Processing**: Efficient handling of 3-page chunks
- **Memory Efficiency**: No OutOfMemoryErrors with proper configuration

## Configuration Options

### FileBasedStgPdfParser Parameters
```kotlin
class FileBasedStgPdfParser(
    context: Context,
    chunkSize: Int = 3  // Pages per chunk (1-10)
)
```

### Customizable Settings
- **Chunk Size**: 1-10 pages per processing chunk
- **Temp File Location**: Uses app's cache directory
- **Logging Level**: Debug logs for development, can be disabled

## Testing Strategy

### Unit Tests
- Pattern matching validation
- Regex correctness verification
- Edge case handling

### Integration Tests  
- Full PDF processing workflow
- Memory management verification
- Content extraction accuracy
- Performance benchmarking

### Test Data
- Sample PDFs with actual STG content
- Various page ranges for different scenarios
- Both clean and problematic text samples

## Known Limitations

1. **OCR Quality**: Some pages may have poor OCR quality affecting extraction
2. **Complex Tables**: Table extraction not yet implemented
3. **Images**: Medical diagrams and flowcharts not processed
4. **Cross-references**: Inter-chapter references not yet linked

## Future Enhancements

1. **Table Extraction**: Parse medication dosing tables
2. **Image Processing**: Extract and index medical diagrams
3. **Semantic Linking**: Connect related conditions across chapters
4. **Validation Layer**: Verify extracted data against medical databases
5. **Performance Optimization**: Further optimize for larger documents

## Dependencies

### Android Libraries
- PDFBox Android: 2.0.27.0
- Kotlin Coroutines: For async processing
- Room Database: For storing extracted content

### Development Tools
- Android Studio
- Gradle 8.11.1
- Android Emulator (Pixel 7a API 34)

## Debugging Tips

### Logcat Filters
```bash
# View chapter extraction
adb logcat -d | grep "FileBasedParser"

# Check medication extraction
adb logcat -d | grep "medications"

# Monitor memory usage
adb logcat -d | grep "Memory"
```

### Common Issues
1. **EmptyList on medications**: Check text format and update patterns
2. **Missing chapters**: Verify page range and TOC detection
3. **Memory issues**: Reduce chunk size or increase emulator RAM

## Conclusion

The PDF parsing implementation successfully extracts structured medical information from the Ghana STG document. With comprehensive test coverage and flexible pattern matching, the system reliably processes the 708-page document while maintaining memory efficiency on mobile devices. The modular design allows for easy extension and maintenance as requirements evolve.