# Ghana STG Clinical Chatbot - Project Documentation

This directory contains comprehensive documentation for the Ghana STG Clinical Chatbot Android application project.

## Document Overview

### Core Documentation Files

#### 1. **README.md**
- Complete project overview and technical architecture
- Database schema and implementation details
- Key features and example user interactions
- Development timeline and risk mitigation strategies

#### 2. **PRD.md** (Product Requirements Document)
- Executive summary and project goals
- Target users and personas
- Functional and non-functional requirements
- Technical specifications and implementation timeline
- Risk assessment and portfolio value

#### 3. **database-schema.md**
- Complete database structure with all entities
- Data Access Objects (DAOs) and relationships
- Type converters and complex query examples
- Performance optimization and validation rules

#### 4. **pdf-parsing-guide.md**
- Comprehensive PDF parsing implementation
- Multi-phase parsing strategy for 708-page document
- Content extraction patterns and regex implementations
- Medication extraction and embedding generation

#### 5. **UI-PRD.md** (NEW - UI Development Source of Truth)
- Comprehensive chat interface specifications
- Session management via navigation drawer
- Dual database architecture (stg_rag.db + app_database.db)
- Jetpack Compose implementation details
- Accessibility and performance standards

#### 6. **claude.md**
- Instructions for AI coding agent
- Project overview and key implementation guidelines
- Architecture patterns and development phases
- Critical technical constraints and portfolio showcase elements

## Project Quick Start

### Overview
This is an Android application that provides offline access to the Ghana Standard Treatment Guidelines (STG) 7th Edition through an AI-powered RAG (Retrieval-Augmented Generation) chatbot interface. The app uses a hierarchical database with 664 content entries, each with 384-dimensional vector embeddings, designed for healthcare providers working completely offline.

### Key Technologies
- **Platform**: Android (Kotlin)
- **UI**: Jetpack Compose
- **Database**: Room (SQLite)
- **AI/ML**: TensorFlow Lite (local embeddings and LLM)
- **Architecture**: MVVM with Repository pattern

### Core Features
1. **Offline-First Design** - Complete functionality without internet
2. **PyMuPDF Extraction** - 664 pages processed with hierarchical structure preservation
3. **RAG Pipeline** - 664 content entries with vector embeddings and metadata
4. **Semantic Search** - AI-powered content discovery with 384-dimensional embeddings
5. **Clinical Chatbot** - Natural language interface with medical terminology understanding
6. **Hierarchical Navigation** - Browse by chapters (23) → sections (831) → content (664)

### Development Phases
1. **Foundation** (✅ COMPLETE) - Hierarchical database implementation
2. **Content Extraction** (✅ COMPLETE) - PyMuPDF extraction and population
3. **Vector Embeddings** (✅ COMPLETE) - Embedding generation and Android integration
4. **Semantic Search** (✅ COMPLETE) - Vector similarity and search service
5. **User Interface** (✅ COMPLETE) - Chat interface with Jetpack Compose
6. **Testing & Optimization** (🔄 IN PROGRESS) - Performance and accuracy

## Source Document Analysis

### Ghana STG 7th Edition Details
- **Total Pages**: 708 pages
- **Publisher**: Republic of Ghana Ministry of Health
- **Content**: Evidence-based treatment guidelines with ratings (A, B, C)
- **Structure**: 22+ chapters covering major medical systems
- **Target Users**: Doctors, medical assistants, midwives, pharmacists

### Document Structure
- **Chapters**: Organized by medical system (Gastrointestinal, Liver, etc.)
- **Conditions**: Individual medical conditions within each chapter
- **Content Blocks**: Structured sections (definition, causes, treatment, dosage, referral)
- **Citations**: Page references and evidence levels throughout

## Technical Architecture

### Hierarchical Database Schema
The application uses a hierarchical Room database structure:

```
chapters (23) → sections (831) → content (664) → embeddings (664)
                                      ↓
                                  metadata (957)
```

**Key Statistics**:
- 23 medical chapters covering all major systems
- 831 hierarchical sections with parent-child relationships
- 664 content entries with page references
- 957 metadata entries for classification
- 664 vector embeddings (384 dimensions each)
- Android Room validated with 100% success

### AI/ML Components
- **Local Embeddings**: all-MiniLM-L6-v2 (384-dimensional vectors deployed)
- **Semantic Search**: Cosine similarity across 664 content entries
- **Language Model**: Local LLM integration (planned)
- **RAG Pipeline**: Hierarchical content retrieval with metadata filtering
- **Search Service**: SemanticSearchService infrastructure complete

### Offline Implementation
- All STG content stored locally in Room database
- Local vector embeddings for semantic search
- Local LLM for response generation
- No cloud dependencies for core functionality

## User Experience Design

### Primary User Flow
1. User opens app to chat interface
2. User asks clinical question (e.g., "Treatment for pediatric diarrhea?")
3. App processes query using semantic search
4. App generates evidence-based response with STG citations
5. User can explore related content or ask follow-up questions

### Interface Components
- **Chat Screen**: Primary conversational interface
- **Browse Screen**: Hierarchical content exploration
- **Condition Details**: Complete treatment information
- **Favorites**: Bookmarked content for quick access
- **Search**: Full-text and semantic search capabilities

## Implementation Guidelines

### Critical Requirements
- **Medical Accuracy**: 95%+ alignment with Ghana STG guidelines
- **Performance**: <3 second response time for queries
- **Offline Reliability**: 99.9% functionality without internet
- **Device Compatibility**: Android 8.0+ with 2GB+ RAM

### Development Best Practices
- Use modern Android development patterns (MVVM, Jetpack Compose)
- Implement comprehensive error handling and validation
- Optimize for mobile device constraints (memory, battery, storage)
- Maintain clinical accuracy with proper citation system
- Follow medical app compliance and disclaimer requirements

### Portfolio Showcase Value
This project demonstrates:
- Full-stack Android development with AI/ML integration
- Complex data processing and database design
- Offline-first architecture with local AI processing
- Healthcare domain expertise and user-centered design
- Performance optimization for mobile constraints

## Current Status & Next Steps

### Completed (✅)
1. **Database Implementation** - Hierarchical Room database with validation
2. **PyMuPDF Extraction** - 664 pages processed with structure preservation
3. **Vector Embeddings** - 664 embeddings with all-MiniLM-L6-v2 model
4. **Android Integration** - Room validation passing with 100% success
5. **Semantic Search Infrastructure** - SemanticSearchService implemented

### Completed Implementation (August 29, 2025)
6. **Vector Similarity** - Cosine similarity calculations implemented
7. **RAG Context Assembly** - Complete pipeline with citations
8. **Chat Interface** - Fully functional with error handling
9. **Semantic Search Service** - 5 services created for RAG pipeline
10. **TensorFlow Lite Framework** - Ready for real model integration

### In Progress (🔄)
11. **Real TFLite Model** - Adding actual embedding model
12. **Testing & Optimization** - Performance and clinical accuracy

### Upcoming (⏳)
13. **Local LLM Integration** - Response generation with Gemma/Phi
14. **Browse Interface** - Hierarchical content navigation
15. **Voice Features** - Speech input/output capabilities

## Additional Resources

- **Source PDF**: `/Users/kobby/Desktop/MOH-STG/GHANA-STG-2017-1.pdf`
- **OCR Pipeline**: `/Users/kobby/Desktop/MOH-STG/stg-ocr-parse/`
- **RAG Database**: `/Users/kobby/AndroidStudioProjects/ClinicalAide/app/src/main/assets/databases/stg_rag.db` (3.33MB with embeddings)
- **Documentation**: All `.md` files in `/docs/` directory
- **Key Scripts**:
  - `pymupdf_extractor.py` - PyMuPDF extraction with structure preservation
  - `populate_db_correct_schema.py` - Hierarchical database population
  - `generate_embeddings.py` - Vector embedding generation (all-MiniLM-L6-v2)

For detailed implementation guidance, refer to the specific documentation files. Each file provides comprehensive technical details for different aspects of the project.

---

*This project represents a significant portfolio piece demonstrating advanced Android development, AI/ML integration, and healthcare technology expertise. The offline-first approach and local AI processing showcase innovative solutions for real-world constraints in developing country healthcare settings.*
