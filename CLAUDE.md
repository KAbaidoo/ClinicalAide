# Claude Project Instructions

This document contains comprehensive instructions for building the Ghana STG Clinical Chatbot Android application. Use this as your primary reference for understanding the project scope, architecture, and implementation details.

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

3. **[docs/database-schema.md](docs/database-schema.md)** - Complete Database Design
   - All Room entities with detailed field descriptions
   - Data Access Objects (DAOs) and relationships
   - Type converters for complex data types
   - Query examples and performance optimization

4. **[docs/pdf-parsing-guide.md](docs/pdf-parsing-guide.md)** - PDF Processing Implementation
   - Multi-phase parsing strategy for 708-page STG document
   - Content extraction patterns and regex implementations
   - Medication extraction algorithms
   - Embedding generation pipeline

5. **[docs/README.md](docs/README.md)** - Technical Architecture
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

## Database Schema Implementation

**📖 Reference Document: [docs/database-schema.md](docs/database-schema.md)**

Use the complete schema defined in the database documentation. Key entities:

```kotlin
// Core entities
StgChapter      // Medical system categories
StgCondition    // Individual medical conditions  
StgContentBlock // Structured content (definition, treatment, dosage, etc.)
StgEmbedding    // Vector embeddings for semantic search
StgMedication   // Detailed medication information

// Supporting entities
StgCrossReference // Relationships between conditions
StgSearchCache    // Performance optimization
```

## PDF Parsing Implementation

**📖 Reference Document: [docs/pdf-parsing-guide.md](docs/pdf-parsing-guide.md)**

Follow the detailed parsing guide for implementing the PDF extraction pipeline:

1. **Document Structure Analysis** - Extract TOC and map content locations
2. **Chapter Parsing** - Identify chapter boundaries and titles
3. **Condition Parsing** - Extract individual medical conditions
4. **Content Block Parsing** - Parse structured sections (causes, treatment, dosage, etc.)
5. **Medication Extraction** - Extract specific medication information
6. **Embedding Generation** - Create vector embeddings for semantic search

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
    suspend fun searchSimilarContent(query: String): List<StgContentBlock> {
        // Generate query embedding
        // Calculate cosine similarity with stored embeddings
        // Return top matching content blocks
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

### Phase 1: Foundation (3-4 weeks)
- Set up project structure and dependencies
- Implement database schema with Room
- Build PDF parsing pipeline
- Populate database with Ghana STG content

### Phase 2: AI Integration (2-3 weeks)
- Implement local embedding generation
- Build semantic search functionality
- Integrate local LLM for response generation
- Create context assembly system

### Phase 3: User Interface (3-4 weeks)
- Build chat interface with Jetpack Compose
- Implement browse functionality
- Create condition detail screens
- Add favorites and search features

### Phase 4: Testing & Optimization (2-3 weeks)
- Performance optimization and profiling
- Clinical accuracy validation
- UI/UX refinement
- Error handling and edge cases

### Phase 5: Documentation (1-2 weeks)
- Code documentation and README
- Architecture documentation
- Demo preparation for portfolio

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

## Getting Started

1. **Setup Development Environment**
   - Android Studio with Kotlin support
   - TensorFlow Lite dependencies
   - PDF processing libraries

2. **Initialize Project Structure**
   - Create Android project with Jetpack Compose
   - Set up Room database with entities (see [docs/database-schema.md](docs/database-schema.md))
   - Configure dependency injection with Hilt

3. **Implement Core Features**
   - Start with PDF parsing and database population (see [docs/pdf-parsing-guide.md](docs/pdf-parsing-guide.md))
   - Add semantic search functionality
   - Build chat interface and user experience
   - Integrate local AI for response generation

4. **Testing and Validation**
   - Test on multiple Android devices
   - Validate clinical accuracy against STG
   - Optimize performance and user experience

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
