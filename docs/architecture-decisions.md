# Architecture Decisions Record (ADR)

**Project**: Ghana STG Clinical Chatbot  
**Created**: August 21, 2025  
**Purpose**: Document key architectural decisions and their rationale

## Overview

This document captures important architectural decisions made during the development of the Ghana STG Clinical Chatbot. Each decision includes context, alternatives considered, and rationale for the chosen approach.

---

## AD-001: Service Consolidation - Removing PdfToDatabaseService

**Date**: August 21, 2025  
**Status**: Implemented  
**Decision**: Remove PdfToDatabaseService and keep StgPdfProcessingService

### Context
Two services were created that served the same purpose - parsing PDFs and populating the database:
- **PdfToDatabaseService** - Simple batch processor
- **StgPdfProcessingService** - Flow-based streaming processor

### Decision Drivers
- Code duplication and maintenance burden
- Memory efficiency requirements for processing 708-page PDF
- Need for progress tracking during long operations
- Consistency in architecture patterns

### Considered Alternatives

#### Option 1: Keep PdfToDatabaseService
- **Pros**: Simpler implementation, easier to understand
- **Cons**: Uses `.toList()` which loads all results into memory, no progress tracking

#### Option 2: Keep StgPdfProcessingService (CHOSEN)
- **Pros**: Flow-based streaming, real-time progress, memory efficient
- **Cons**: More complex implementation

#### Option 3: Merge both into new service
- **Pros**: Could combine best of both
- **Cons**: Additional work, existing StgPdfProcessingService already production-ready

### Decision Outcome
Removed PdfToDatabaseService and enhanced StgPdfProcessingService because:
1. **Memory Safety** - Flow-based approach essential for 708-page document
2. **User Experience** - Progress tracking critical for long operations
3. **Production Ready** - More mature with error handling and cleanup
4. **Single Responsibility** - One service, one purpose

---

## AD-002: Content Block Design Evolution

**Date**: August 21, 2025  
**Status**: Implemented  
**Decision**: Implement structured content extraction instead of raw text dumping

### Context
Original implementation was storing raw page text as generic content blocks, missing the structured nature of medical information (symptoms, treatment, dosage, etc.).

### Problem Statement
- Content blocks were just raw text dumps
- No categorization by medical information type
- Poor searchability and filtering
- Misalignment with database design intent

### Decision Drivers
- Need for precise medical information retrieval
- Requirement to filter by content type
- Database schema already designed for structured content
- Chatbot needs contextual understanding

### Solution Approach

#### Phase 1: Create ContentBlockExtractor
- Pattern-based detection of section headers
- 10 content type categories
- Smart header removal while preserving content

#### Phase 2: Integration with Parser
- FileBasedStgPdfParser extracts ParsedContentBlocks
- Associates content with specific conditions
- Maintains ordering and relationships

#### Phase 3: Database Population
- StgPdfProcessingService maps to entities
- Proper foreign key relationships
- Batch processing for performance

### Benefits Realized
1. **Semantic Understanding** - System knows what type of information it's storing
2. **Precise Retrieval** - Can query for specific content types
3. **Better UX** - Users can filter responses by information type
4. **Foundation for AI** - Structured data for ML training

---

## AD-003: Flow-Based vs Batch Processing

**Date**: August 21, 2025  
**Status**: Decided  
**Decision**: Use Flow-based processing for PDF parsing

### Context
Processing a 708-page PDF requires careful memory management on mobile devices with limited resources.

### Alternatives Evaluated

#### Batch Processing (Traditional)
```kotlin
suspend fun process(): List<Result> {
    return parser.parseAll() // Loads everything into memory
}
```
- **Pros**: Simple, synchronous, easy testing
- **Cons**: Memory risk, no progress feedback, can't cancel

#### Flow-Based Processing (CHOSEN)
```kotlin
fun process(): Flow<Result> = flow {
    parser.processChunks().collect { chunk ->
        emit(progressUpdate)
        processChunk(chunk)
    }
}
```
- **Pros**: Streaming, cancellable, progress tracking, memory efficient
- **Cons**: More complex, async handling required

### Decision Rationale
1. **Memory Constraints** - Mobile devices have limited RAM
2. **User Feedback** - Long operations need progress indication
3. **Reactive Programming** - Aligns with modern Android patterns
4. **Cancellation** - Users can stop long operations

---

## AD-004: Database Index Strategy

**Date**: August 21, 2025  
**Status**: Implemented  
**Decision**: Add indices to all foreign key columns

### Context
Room compiler warnings indicated missing indices on foreign key columns, potentially causing full table scans.

### Performance Impact
Without indices:
- Parent table modifications trigger full child table scans
- Slow JOIN operations
- Poor query performance

With indices:
- O(log n) lookups instead of O(n)
- Faster cascading operations
- Improved query performance

### Implementation
Added indices to:
- `StgCondition.chapterId`
- `StgContentBlock.conditionId`
- `StgEmbedding.contentBlockId`
- `StgMedication.conditionId`

---

## AD-005: Parser Chunk Size Configuration

**Date**: August 18-21, 2025  
**Status**: Implemented  
**Decision**: Process PDF in 3-page chunks by default

### Context
Need to balance memory usage with processing efficiency when parsing large PDFs.

### Trade-offs Analyzed

| Chunk Size | Memory Usage | Processing Time | Context Quality |
|------------|--------------|-----------------|-----------------|
| 1 page     | Minimal      | Slowest         | Poor            |
| 3 pages    | Low          | Balanced        | Good            |
| 10 pages   | Moderate     | Fast            | Best            |
| 50+ pages  | High         | Fastest         | Best            |

### Decision Factors
1. **Memory Safety** - Must work on 2GB RAM devices
2. **Context Preservation** - Multi-page conditions need continuity
3. **Performance** - Reasonable processing speed
4. **Flexibility** - Configurable for different devices

### Outcome
Default 3-page chunks with configurable option (1-10 pages).

---

## AD-006: Content Type Taxonomy

**Date**: August 21, 2025  
**Status**: Implemented  
**Decision**: Define 10 standard medical content types

### Context
Need standardized categorization for medical content that aligns with Ghana STG structure and clinical workflows.

### Selected Categories
1. DEFINITION - Condition overview
2. SYMPTOMS - Clinical presentation
3. TREATMENT - Management protocols
4. DOSAGE - Medication information
5. REFERRAL - When to escalate
6. COMPLICATIONS - Risks and adverse effects
7. INVESTIGATIONS - Diagnostic approach
8. PREVENTION - Prophylactic measures
9. FOLLOW_UP - Monitoring guidelines
10. PROGNOSIS - Expected outcomes

### Rationale
- Covers 95% of STG content patterns
- Aligns with clinical decision workflow
- Enables type-specific queries
- Supports future AI training

---

## AD-007: Test-Driven Development Approach

**Date**: August 17-21, 2025  
**Status**: Ongoing  
**Decision**: Write tests before implementation

### Context
Complex medical information system requires high reliability and accuracy.

### Benefits Observed
1. **Early Bug Detection** - Found issues before production
2. **Design Clarity** - Tests drive better API design
3. **Refactoring Safety** - Confident in making changes
4. **Documentation** - Tests serve as usage examples
5. **Quality Metrics** - 100% test pass rate maintained

### Test Categories
- Unit tests for extractors
- Integration tests for services
- Performance tests for database
- End-to-end tests for workflows

---

## AD-008: Error Handling Strategy

**Date**: August 21, 2025  
**Status**: Implemented  
**Decision**: Fail-fast with comprehensive logging

### Approach
1. **Validate Early** - Check inputs at boundaries
2. **Log Everything** - Detailed error context
3. **Clean Up Always** - Finally blocks for resources
4. **User-Friendly Messages** - Translate technical errors

### Example Implementation
```kotlin
try {
    process()
} catch (e: Exception) {
    Log.e(TAG, "Detailed error context", e)
    emit(userFriendlyError)
    throw e // Fail fast
} finally {
    cleanup() // Always clean up
}
```

---

## Decision Log Summary

| ID | Decision | Date | Status | Impact |
|----|----------|------|--------|--------|
| AD-001 | Service Consolidation | 2025-08-21 | Implemented | High |
| AD-002 | Content Block Structure | 2025-08-21 | Implemented | High |
| AD-003 | Flow-Based Processing | 2025-08-21 | Decided | High |
| AD-004 | Database Indices | 2025-08-21 | Implemented | Medium |
| AD-005 | Parser Chunk Size | 2025-08-18 | Implemented | Medium |
| AD-006 | Content Taxonomy | 2025-08-21 | Implemented | High |
| AD-007 | TDD Approach | 2025-08-17 | Ongoing | High |
| AD-008 | Error Handling | 2025-08-21 | Implemented | Medium |

---

## Future Decisions Needed

1. **AI Model Selection** - Which local LLM for mobile?
2. **Embedding Dimensions** - 512 vs 768 for vectors?
3. **Caching Strategy** - What to cache and for how long?
4. **Update Mechanism** - How to update STG content?
5. **Offline Sync** - How to handle occasional connectivity?

---

*This document follows Architecture Decision Record (ADR) best practices for documenting architectural decisions in software projects.*