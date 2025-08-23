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

#### 5. **claude.md**
- Instructions for AI coding agent
- Project overview and key implementation guidelines
- Architecture patterns and development phases
- Critical technical constraints and portfolio showcase elements

## Project Quick Start

### Overview
This is an Android application that provides offline access to the Ghana Standard Treatment Guidelines (STG) 7th Edition through an AI-powered RAG (Retrieval-Augmented Generation) chatbot interface. The app uses OCR-extracted content with 969 searchable chunks, each with verifiable citations, designed for healthcare providers working completely offline.

### Key Technologies
- **Platform**: Android (Kotlin)
- **UI**: Jetpack Compose
- **Database**: Room (SQLite)
- **AI/ML**: TensorFlow Lite (local embeddings and LLM)
- **Architecture**: MVVM with Repository pattern

### Core Features
1. **Offline-First Design** - Complete functionality without internet
2. **OCR-Based Extraction** - 679 pages processed, 304 conditions, 555 medications extracted
3. **RAG Pipeline** - 969 content chunks with full chapter/section/page citations
4. **Semantic Search** - AI-powered content discovery with TensorFlow embeddings
5. **Clinical Chatbot** - Natural language interface with verifiable STG references
6. **Citation System** - Every response includes "Ghana STG 2017 - Chapter X, Page Y" references

### Development Phases
1. **Foundation** (✅ COMPLETE) - Database implementation
2. **OCR & RAG Pipeline** (✅ COMPLETE) - 969 chunks with citations extracted
3. **AI Integration** (🔄 IN PROGRESS) - Embedding generation and Gemma 2 integration
4. **User Interface** (⏳ PENDING) - Chat and browse functionality
5. **Testing & Optimization** (⏳ PENDING) - Performance and accuracy

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

### RAG Database Schema
The application uses a RAG-optimized Room database structure:

```
chapters (31) ────── conditions_enhanced (304)
                           │
                           │
                    content_chunks (969) ─── embeddings (future)
                           │
                           │
                    medications_enhanced (555)
```

**Key Statistics**:
- 31 chapters with page ranges
- 969 RAG-ready content chunks
- 304 medical conditions with references
- 555 medications with dosages
- 100% citation coverage

### AI/ML Components
- **Local Embeddings**: TensorFlow Lite (384-dimensional vectors ready)
- **Semantic Search**: Cosine similarity across 969 content chunks
- **Language Model**: Gemma 2B for response generation
- **RAG Pipeline**: Content retrieval with citation tracking
- **Citation Format**: "Ghana STG 2017 - Chapter 18, Section 187, Page 483"

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
1. **Database Implementation** - RAG-optimized Room database
2. **OCR Extraction** - 679 pages processed with medical patterns
3. **RAG Pipeline** - 969 content chunks with citations
4. **Android Integration** - stg_rag.db deployed and working

### In Progress (🔄)
5. **Embedding Generation** - TensorFlow Lite integration
6. **Semantic Search** - Similarity search implementation

### Upcoming (⏳)
7. **Gemma 2 Integration** - Local LLM for response generation
8. **User Interface** - Chat interface with Jetpack Compose
9. **Testing & Optimization** - Performance and clinical accuracy

## Additional Resources

- **Source PDF**: `/Users/kobby/Desktop/MOH-STG/GHANA-STG-2017-1.pdf`
- **OCR Pipeline**: `/Users/kobby/Desktop/MOH-STG/stg-ocr-parse/`
- **RAG Database**: `/Users/kobby/AndroidStudioProjects/ClinicalAide/app/src/main/assets/databases/stg_rag.db`
- **Documentation**: All `.md` files in `/docs/` directory
- **Key Scripts**:
  - `medical_ocr_extractor.py` - OCR extraction with medical patterns
  - `rag_pipeline_builder.py` - RAG database generation
  - `generate_embeddings.py` - TensorFlow embedding generation

For detailed implementation guidance, refer to the specific documentation files. Each file provides comprehensive technical details for different aspects of the project.

---

*This project represents a significant portfolio piece demonstrating advanced Android development, AI/ML integration, and healthcare technology expertise. The offline-first approach and local AI processing showcase innovative solutions for real-world constraints in developing country healthcare settings.*
