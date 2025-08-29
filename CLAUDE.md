# Claude Project Instructions

This document contains comprehensive instructions for building the Ghana STG Clinical Chatbot Android application. Use this as your primary reference for understanding the project scope, architecture, and implementation details.

## 🚀 Quick Resume Guide

### Current Project Status (August 29, 2025)
- ✅ **PDF Extraction Complete**: All 664 pages (29-692) successfully extracted
- ✅ **Database Populated**: 23 chapters, 831 sections, 664 content entries, 957 metadata entries
- ✅ **New Database Schema**: Hierarchical structure with chapters→sections→content→metadata
- ✅ **Android Room Entities Updated**: Matching new Python schema from stg-ocr-parse
- ✅ **Embeddings Generated**: 664 vector embeddings using all-MiniLM-L6-v2 (384 dimensions)
- ✅ **Semantic Search Implemented**: Complete RAG pipeline with vector similarity search
- ✅ **TensorFlow Lite Ready**: Framework for real embedding models in place
- ✅ **Chat Interface Working**: Full chat with citations and error handling
- ⏳ **Next Phase**: Add real TFLite model and test with medical queries

### Quick Commands to Resume
```bash
# Check project status
git status
git log --oneline -5

# Run all database tests (verify everything works)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=co.kobby.clinicalaide.data.database

# Run PDF parser tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=co.kobby.clinicalaide.data.pdf

# Build project
./gradlew build

# Start emulator if needed
/Users/kobby/Library/Android/sdk/emulator/emulator -avd Pixel_7a_API_34-ext8
```

### Recent Updates (August 29, 2025)
- **✅ SEMANTIC SEARCH COMPLETE**: Implemented full RAG pipeline with vector similarity search
- **✅ TENSORFLOW LITE INTEGRATION**: Created framework for real-time embedding generation
- **✅ CHAT INTERFACE FIXED**: Resolved silent failures, added comprehensive error handling
- Created 5 new services: SemanticSearchService, EmbeddingService, TFLiteModelLoader, TextPreprocessor, ClinicalRAGService
- Fixed critical DAO methods and entity field mismatches
- App now performs semantic search on 664 content entries with citations

### Previous Updates (August 27-28, 2025)
- New database schema from stg-ocr-parse is source of truth
- Completed full STG document extraction (pages 29-692)
- Generated vector embeddings for all content (all-MiniLM-L6-v2 model)
- Built complete chat interface with Jetpack Compose
- Database: `stg_rag.db` (3.2MB with embeddings)

## 📚 Documentation Reference

This project includes comprehensive documentation to guide development. Reference these documents for specific aspects:

### Core Documentation Files

1. **[docs/PRD.md](docs/PRD.md)** - Product Requirements Document
   - Executive summary and project goals
   - Target users and detailed personas
   - Functional and non-functional requirements
   - Technical specifications and timeline
   - Risk assessment and success metrics

2. **[docs/project-overview.md](docs/project-overview.md)** - Project Overview
   - Quick start guide and document navigation
   - High-level architecture summary
   - Key technologies and features
   - Development workflow overview

3. **[docs/database-schema.md](docs/database-schema.md)** - Database Design (UPDATED)
   - **NEW SCHEMA**: Based on stg-ocr-parse extraction pipeline
   - Tables: chapters, sections, content, embeddings, metadata
   - Hierarchical structure with proper foreign key relationships
   - See `stg-ocr-parse/README.md` for complete schema documentation

4. **[docs/pdf-parsing-guide.md](docs/pdf-parsing-guide.md)** - PDF Processing Strategy
   - Multi-phase parsing strategy for 708-page STG document
   - Content extraction patterns and regex implementations
   - Medication extraction algorithms
   - Embedding generation pipeline

5. **[docs/pdf-parsing-implementation.md](docs/pdf-parsing-implementation.md)** - PDF Parser Implementation Details
   - FileBasedStgPdfParser technical documentation
   - Memory management strategies
   - Test infrastructure and results
   - Known issues and solutions

6. **[docs/README.md](docs/README.md)** - Technical Architecture
   - Complete project structure and implementation details
   - Example user interactions and system responses
   - Development timeline with sprint breakdowns
   - Risk mitigation strategies

### How to Use This Documentation

- **Starting Development**: Begin with this CLAUDE.md file for overall guidance
- **Database Implementation**: Refer to `database-schema.md` for Room entity setup
- **PDF Processing**: Use `pdf-parsing-guide.md` for content extraction implementation
- **Product Requirements**: Check `PRD.md` for feature priorities and user requirements
- **Architecture Decisions**: See `README.md` for technical implementation patterns

## Project Overview

**Project Name**: Ghana STG Clinical Chatbot  
**Platform**: Android (Kotlin)  
**Type**: Offline-first medical reference application with AI chatbot  
**Purpose**: Provide healthcare providers in Ghana with instant access to evidence-based clinical guidance from the Ghana Standard Treatment Guidelines (STG) 7th Edition

## Core Technologies

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room (SQLite)
- **Architecture**: MVVM with Repository pattern
- **AI/ML**: TensorFlow Lite (local embeddings and LLM)
- **PDF Processing**: PDFBox or similar
- **Dependency Injection**: Hilt
- **Async Operations**: Coroutines

## Key Features to Implement

### 1. Offline-First Architecture
- All functionality must work without internet connectivity
- Local storage of complete Ghana STG content (708 pages)
- Local AI processing for embeddings and response generation
- No cloud dependencies for core features

### 2. PDF Parsing System
- Parse 708-page Ghana STG PDF into structured database
- Extract chapters, conditions, and content blocks
- Identify medications, dosages, and clinical contexts
- Generate searchable keywords and medical terms

### 3. Semantic Search Engine
- Local vector embeddings using TensorFlow Lite
- Cosine similarity search for content retrieval
- Support for medical terminology and natural language queries
- Fast response times (<1 second for search)

### 4. AI Chatbot Interface
- Natural language query processing
- Evidence-based response generation using local LLM
- Citation system with page references to original STG
- Clinical context awareness (pediatric, adult, pregnancy, etc.)

### 5. User Interface
- Chat interface with message history
- Browse mode for exploring conditions by chapter
- Favorites/bookmarks system
- Search functionality across all content

## Database Schema Implementation (UPDATED August 27, 2025)

**📖 Source of Truth: stg-ocr-parse/stg_rag.db**
**📖 Reference: [stg-ocr-parse/README.md](stg-ocr-parse/README.md)**

The database schema has been updated to match the Python extraction pipeline. Key entities:

```kotlin
// Core entities (Android Room)
Chapter         // Document chapters (23 total)
Section         // Hierarchical sections with parent-child relationships
Content         // Actual medical content (paragraph, bullet, table, note)
Embedding       // Vector embeddings for semantic search
Metadata        // Key-value pairs for content classification
```

**Database Statistics:**
- 23 chapters covering all medical systems
- 831 sections with proper hierarchy
- 664 content entries with page references
- 957 metadata entries for classification
- Ready for embedding generation

## PDF Parsing Implementation (COMPLETED)

**📖 Reference: [stg-ocr-parse/CLAUDE.md](stg-ocr-parse/CLAUDE.md)**

✅ **PDF extraction is complete!** The full Ghana STG document (pages 29-692) has been processed.

**Extraction Pipeline Used:**
1. **PyMuPDF Extraction** - Direct text extraction (no OCR needed)
2. **Text Cleaning** - Headers, artifacts, and formatting cleaned
3. **Table Processing** - ASCII table formatting preserved
4. **Database Population** - Direct insertion into hierarchical schema
5. **Metadata Extraction** - Automatic classification of content

**Next Step:** Generate embeddings for semantic search

## Local AI Implementation

### Embedding Generation
```kotlin
class LocalEmbeddingService {
    // Use TensorFlow Lite Universal Sentence Encoder
    private val interpreter: Interpreter
    
    fun generateEmbedding(text: String): FloatArray {
        // Process text and generate vector embedding
        // Return 512 or 768 dimensional vector
    }
}
```

### Semantic Search
```kotlin
class OfflineSemanticSearch {
    suspend fun searchSimilarContent(query: String): List<Content> {
        // Generate query embedding
        // Calculate cosine similarity with stored embeddings
        // Return top matching content entries
    }
}
```

### Local LLM Integration
```kotlin
class OfflineClinicalLLM {
    // Use Gemma 2B or Phi-3 Mini for response generation
    suspend fun generateClinicalGuidance(
        context: LLMContext,
        userQuery: String
    ): String {
        // Build clinical prompt with context
        // Generate evidence-based response
        // Include citations and safety information
    }
}
```

## Application Architecture

### Repository Pattern
```kotlin
class StgRepository(
    private val dao: StgDao,
    private val semanticSearch: OfflineSemanticSearch,
    private val llmService: OfflineClinicalLLM
) {
    suspend fun getClinicalGuidance(query: String): ClinicalResponse {
        // 1. Process query and extract intent
        // 2. Perform semantic search for relevant content
        // 3. Build context with related information
        // 4. Generate LLM response with citations
        // 5. Return structured clinical guidance
    }
}
```

### ViewModel Implementation
```kotlin
class ChatViewModel(
    private val repository: StgRepository
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    
    fun sendMessage(userMessage: String) {
        viewModelScope.launch {
            // Add user message
            // Get clinical guidance from repository
            // Add bot response with citations
        }
    }
}
```

## User Interface Guidelines

### Chat Interface
- Clean, medical-focused design with professional colors
- Message bubbles distinguishing user queries from bot responses
- Citation links embedded in responses
- Typing indicators and loading states
- Quick action buttons for common queries

### Navigation Structure
- Bottom navigation: Chat, Browse, Favorites, Settings
- Browse mode organized by medical system/chapter
- Search functionality across all content
- Filter options by patient type or clinical context

### Key Screens
1. **Chat Screen** - Primary conversational interface
2. **Browse Screen** - Hierarchical content exploration
3. **Condition Detail Screen** - Complete information for specific conditions
4. **Favorites Screen** - Saved content and bookmarks
5. **Settings Screen** - App preferences and information

## Content Structure Examples

### Example Query Flow
**User Query**: "What is the treatment for a child having diarrhea and vomiting?"

**System Process**:
1. Parse query → identify: pediatric, diarrhea, vomiting, treatment
2. Semantic search → find relevant content blocks
3. Context assembly → gather definition, treatment, dosage, referral criteria
4. LLM generation → create evidence-based response
5. Citation → include Ghana STG page references

**Expected Response**:
```
Treatment for Pediatric Diarrhea with Vomiting:

PRIMARY TREATMENT:
• Oral Rehydration Therapy (ORS) - first-line treatment
• Give small, frequent sips due to vomiting
• Start with 5-10ml every 5 minutes

DOSING:
• Mild dehydration: 75ml/kg over 4 hours
• Moderate dehydration: 100ml/kg over 6 hours

IMPORTANT NOTES:
• Continue normal feeding/breastfeeding
• Antibiotics NOT recommended (viral cause most common)

REFER IMMEDIATELY IF:
• Signs of severe dehydration
• Persistent vomiting preventing fluid intake
• Blood in stool

Evidence Level: Grade A
Source: Ghana STG 7th Edition, Pages 29-32
```

## Performance Requirements

- **Response Time**: <3 seconds for 90% of queries
- **Memory Usage**: <200MB active memory
- **Database Size**: ~50-100MB for complete STG content
- **App Launch**: <2 seconds cold start
- **Search Performance**: <1 second for semantic search

## Development Phases

### ✅ Phase 0: Project Setup (COMPLETE)
- Project structure and dependencies
- Android app configuration
- Git repository initialization

### ✅ Phase 1: Database Implementation (UPDATED August 27, 2025)
- New schema: chapters, sections, content, embeddings, metadata
- Room entities updated to match Python extraction schema
- Database populated with complete STG content
- Foreign key relationships properly maintained

### ✅ Phase 2: PDF Parsing (COMPLETE August 27, 2025)
- Parsed all 664 pages (29-692) of Ghana STG PDF
- Extracted 23 chapters, 831 sections, 664 content entries
- Identified 957 metadata entries for classification
- Database fully populated in `stg_rag.db`
- **See: stg-ocr-parse/README.md for details**

### ⏳ Phase 3: AI Integration (IN PROGRESS)
- ✅ Implement local embedding generation (COMPLETE)
- Build semantic search functionality in Android
- Integrate local LLM for response generation
- Create context assembly system

### ⏳ Phase 4: User Interface (3-4 weeks)
- Build chat interface with Jetpack Compose
- Implement browse functionality
- Create condition detail screens
- Add favorites and search features

### ⏳ Phase 5: Testing & Optimization (2-3 weeks)
- Performance optimization and profiling
- Clinical accuracy validation
- UI/UX refinement
- Error handling and edge cases

## Critical Implementation Notes

### Medical Accuracy
- All responses must be traceable to Ghana STG source
- Include evidence levels when available (A, B, C)
- Implement clear disclaimers about clinical judgment
- Validate extracted content against original document

### Offline Reliability
- No network dependencies for core functionality
- Graceful handling of missing data
- Robust error recovery mechanisms
- Efficient local storage and retrieval

### Performance Optimization
- Lazy loading of large data sets
- Database query optimization with proper indices
- Efficient vector similarity calculations
- Memory management for AI model loading

### User Experience
- Intuitive navigation for healthcare providers
- Fast response times for clinical workflows
- Clear visual hierarchy and medical terminology
- Accessibility considerations for various devices

## Technical Constraints

### Device Compatibility
- Minimum Android API 26 (Android 8.0)
- Support for ARM64 and ARMv7 architectures
- Optimize for devices with 2GB+ RAM
- Handle varying screen sizes and orientations

### Model Size Limitations
- Use quantized models for mobile deployment
- Implement dynamic model loading if needed
- Balance accuracy with storage requirements
- Consider model compression techniques

### Battery Optimization
- Minimize background processing
- Efficient CPU usage for AI operations
- Optimize database queries
- Implement proper lifecycle management

## Portfolio Showcase Elements

This project demonstrates:
- **Full-stack Android development** with modern Kotlin and Jetpack Compose
- **AI/ML integration** with local model deployment and vector search
- **Complex data processing** from large document to structured database
- **Offline-first architecture** with no cloud dependencies
- **Domain expertise** in healthcare technology and clinical workflows
- **Performance optimization** for mobile constraints
- **User-centered design** for professional medical use cases

## Getting Started (For Resuming Work)

### Current State (August 27, 2025)
- ✅ Development environment setup complete
- ✅ Project structure initialized
- ✅ PDF extraction and database population COMPLETE
- ✅ Room entities updated to match new schema
- ✅ Database `stg_correct.db` ready with full STG content
- ⏳ Ready for embedding generation and Android integration

### Next Steps
1. **Generate Embeddings**
   - Create embedding generation script in `stg-ocr-parse/`
   - Use sentence transformers or similar for vector generation
   - Populate embeddings table in database

2. **Android Integration**
   - Copy `stg_correct.db` to `app/src/main/assets/databases/`
   - Update RagDao with queries for new schema
   - Test database access from Android app

3. **Testing Workflow**
   ```bash
   # Copy database to Android assets
   cp stg-ocr-parse/stg_rag.db app/src/main/assets/databases/
   
   # Build and test Android app
   ./gradlew build
   ./gradlew connectedAndroidTest
   ```

4. **Resources**
   - Python extraction pipeline: `stg-ocr-parse/README.md`
   - Database schema: `stg-ocr-parse/CLAUDE.md`
   - Android Room entities: `app/src/.../data/rag/entities/`

## Additional Resources

- **Product Requirements**: See [docs/PRD.md](docs/PRD.md) for detailed requirements and user personas
- **Technical Architecture**: See [docs/README.md](docs/README.md) for system architecture and implementation patterns
- **Project Overview**: See [docs/project-overview.md](docs/project-overview.md) for quick navigation

## Task Completion Notification

After completing each task, play the system glass sound to notify completion:
```bash
# macOS
afplay /System/Library/Sounds/Glass.aiff

# Alternative for other systems - use system notification sound
```

Follow the detailed implementation guides in the accompanying documentation files for specific technical implementation details.
