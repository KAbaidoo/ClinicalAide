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
This is an Android application that provides offline access to the Ghana Standard Treatment Guidelines (STG) 7th Edition through an AI-powered chatbot interface. The app is designed for healthcare providers and works completely offline using local AI processing.

### Key Technologies
- **Platform**: Android (Kotlin)
- **UI**: Jetpack Compose
- **Database**: Room (SQLite)
- **AI/ML**: TensorFlow Lite (local embeddings and LLM)
- **Architecture**: MVVM with Repository pattern

### Core Features
1. **Offline-First Design** - Complete functionality without internet
2. **PDF Parsing System** - Convert 708-page STG document to structured data
3. **Semantic Search** - AI-powered content discovery
4. **Clinical Chatbot** - Natural language interface for medical queries
5. **Evidence-Based Responses** - All answers cited to original STG content

### Development Phases
1. **Foundation** (3-4 weeks) - Database and PDF parsing
2. **AI Integration** (2-3 weeks) - Semantic search and LLM
3. **User Interface** (3-4 weeks) - Chat and browse functionality
4. **Testing & Optimization** (2-3 weeks) - Performance and accuracy
5. **Documentation** (1-2 weeks) - Portfolio preparation

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

### Database Schema
The application uses a hierarchical Room database structure:

```
StgChapter (1) ──────── (Many) StgCondition
                                    │
                                    │ (1)
                                    │
                                    │ (Many)
                              StgContentBlock ──── (1) StgEmbedding
                                    │
                                    │ (1)
                                    │
                                    │ (Many)
                              StgMedication
```

### AI/ML Components
- **Local Embeddings**: TensorFlow Lite Universal Sentence Encoder
- **Semantic Search**: Cosine similarity with vector embeddings
- **Language Model**: Quantized Gemma 2B or Phi-3 Mini
- **Context Assembly**: Clinical context-aware response generation

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

## Next Steps

1. **Review all documentation files** to understand complete project scope
2. **Set up development environment** with required tools and dependencies
3. **Implement database schema** using Room with all entities defined
4. **Build PDF parsing pipeline** following the detailed parsing guide
5. **Integrate AI components** for semantic search and response generation
6. **Develop user interface** with Jetpack Compose following UX guidelines
7. **Test and optimize** for performance, accuracy, and user experience

## Additional Resources

- **Source PDF**: `/Users/kobby/Desktop/MOH-STG/GHANA-STG-2017-1.pdf`
- **Project Directory**: `/Users/kobby/Desktop/MOH-STG/`
- **Documentation**: All `.md` files in `/docs/` directory

For detailed implementation guidance, refer to the specific documentation files. Each file provides comprehensive technical details for different aspects of the project.

---

*This project represents a significant portfolio piece demonstrating advanced Android development, AI/ML integration, and healthcare technology expertise. The offline-first approach and local AI processing showcase innovative solutions for real-world constraints in developing country healthcare settings.*
