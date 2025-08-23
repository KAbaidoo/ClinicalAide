# Ghana STG Clinical Chatbot - Android Application

## Project Overview

This project is an Android application that provides a RAG-powered clinical chatbot for healthcare providers in Ghana. The chatbot references the Ghana Standard Treatment Guidelines (STG) 7th Edition (2017) through 969 OCR-extracted content chunks, each with verifiable citations. The application works completely offline using a local RAG (Retrieval-Augmented Generation) pipeline, ensuring reliable access to medical guidelines with exact page references.

## Background & Document Analysis

### Source Document: Ghana STG 7th Edition (2017)
- **Total Pages**: 708 pages
- **Publisher**: Republic of Ghana Ministry of Health - Ghana National Drugs Programme (GNDP)
- **Structure**: Hierarchical organization with 22+ chapters covering major medical conditions
- **Content Type**: Evidence-based treatment guidelines with ratings (Level A, B, C)
- **Target Users**: Doctors, medical assistants, midwives, pharmacists, and healthcare staff

### Document Structure Analysis
The Ghana STG follows a systematic structure:
1. **Chapters**: Organized by medical system (Gastrointestinal, Liver, Hematological, etc.)
2. **Conditions**: Each chapter contains numbered medical conditions
3. **Content Blocks**: Each condition has structured sections:
   - Definition/Description
   - Causes (acute vs chronic)
   - Clinical presentation/Symptoms
   - Treatment protocols
   - Specific dosages
   - Referral criteria
   - Evidence ratings

### Key Medical Areas Covered
- Gastrointestinal Tract Disorders
- Liver Disorders
- Nutritional Disorders
- Hematological Disorders
- Immunizable Diseases
- Newborn Problems
- Mental Health Disorders
- Skin Disorders
- Obstetric Disorders
- Gynecological Disorders
- Sexually Transmitted Infections
- HIV Infections and AIDS
- Musculoskeletal Disorders
- And more...

## Technical Architecture

### Core Technology Stack
- **Platform**: Android (Kotlin)
- **Database**: Room (SQLite) with RAG-optimized schema
- **Content Extraction**: OCR-based extraction (679 pages processed)
- **RAG Pipeline**: 969 content chunks with citations
- **Vector Search**: TensorFlow Lite (384-dimensional embeddings)
- **LLM**: Gemma 2B for response generation
- **UI**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern

### Offline-First RAG Architecture
The application uses a complete offline RAG pipeline:
- **598KB RAG database** with 969 content chunks
- **Full citation support** - Every chunk includes "Ghana STG 2017 - Chapter X, Page Y"
- **304 medical conditions** extracted with references
- **555 medications** with dosage information
- **31 chapters** with page ranges
- **Local embeddings** for semantic similarity search
- **Gemma 2B** for response generation with citations

## RAG Database Schema

### Core RAG Tables

```kotlin
@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "number")
    val number: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "start_page")
    val startPage: Int
)

@Entity(tableName = "content_chunks")
data class ContentChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "chunk_type")
    val chunkType: String, // "treatment", "clinical_features", "investigation", "medication", "general"
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "condition_name")
    val conditionName: String?,
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String, // "Ghana STG 2017 - Chapter 18, Section 187, Page 483"
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,
    val clinicalContext: String = "general", // "pediatric", "adult", "pregnancy", "elderly"
    val severityLevel: String? = null, // "mild", "moderate", "severe"
    val evidenceLevel: String? = null, // "A", "B", "C"
    val keywords: String,
    val relatedBlockIds: String = "[]", // JSON array
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stg_embeddings")
data class StgEmbedding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentBlockId: Long, // Foreign key to StgContentBlock
    val embedding: String, // JSON string of vector
    val embeddingModel: String,
    val embeddingDimensions: Int = 768,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stg_medications")
data class StgMedication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conditionId: Long,
    val medicationName: String,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val route: String,
    val ageGroup: String, // "adult", "pediatric", "neonatal"
    val weightBased: Boolean = false,
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val evidenceLevel: String? = null,
    val pageNumber: Int
)
```

## Implementation Plan

### Phase 1: PDF Parsing & Database Setup
1. **Document Structure Analysis**
   - Extract table of contents (pages 3-10)
   - Map chapter and condition locations
   - Identify content block patterns

2. **Content Extraction**
   - Parse chapters and conditions
   - Extract structured content blocks using regex patterns
   - Clean and normalize text content
   - Extract medications and dosages

3. **Database Population**
   - Insert parsed content into Room database
   - Generate local embeddings for all content blocks
   - Create search indices and optimizations

### Phase 2: Semantic Search Implementation
1. **Local Vector Search**
   - Implement cosine similarity calculation
   - Create efficient embedding storage
   - Build query processing pipeline

2. **Context Assembly**
   - Develop context building algorithm
   - Implement related content retrieval
   - Add citation management

### Phase 3: LLM Integration
1. **Local Model Setup**
   - Integrate TensorFlow Lite or ONNX runtime
   - Load and optimize local LLM model
   - Implement response generation

2. **Prompt Engineering**
   - Design clinical-specific prompts
   - Implement context injection
   - Add safety and accuracy measures

### Phase 4: User Interface
1. **Chat Interface**
   - Build conversational UI with Jetpack Compose
   - Implement message history
   - Add typing indicators and loading states

2. **Clinical Features**
   - Add condition browsing
   - Implement bookmark/favorites
   - Include citation viewing

### Phase 5: Testing & Optimization
1. **Clinical Accuracy Testing**
   - Validate responses against STG guidelines
   - Test edge cases and complex queries
   - Performance optimization

2. **User Experience**
   - Conduct usability testing with healthcare providers
   - Optimize response times
   - Refine interface based on feedback

## Key Features

### Core Functionality
- **RAG-Powered Interface**: Natural language queries search 969 content chunks
- **Verifiable Citations**: Every response includes "Ghana STG 2017 - Chapter X, Page Y" references
- **Offline RAG Pipeline**: Complete functionality with local embeddings and LLM
- **OCR-Extracted Content**: 304 conditions and 555 medications with accurate information
- **Medication Database**: Complete dosing, routes, and strength information

### Advanced Features
- **Semantic Search**: Understanding of medical terminology and context
- **Related Conditions**: Suggests related medical conditions and treatments
- **Quick Reference**: Browse conditions by chapter/category
- **Citation System**: Direct page references to Ghana STG document
- **Clinical Alerts**: Highlight referral criteria and serious conditions

### Example User Interactions

**Query**: "What is the treatment for a child having diarrhea and vomiting?"

**Expected Response**: 
- Primary treatment with ORS (Oral Rehydration Therapy)
- Pediatric-specific dosing (75ml/kg for mild dehydration)
- Management of vomiting with small frequent sips
- Clear referral criteria (severe dehydration signs)
- Evidence level citations (Level A recommendations)
- Page references (Ghana STG Pages 29-32)

## Technical Considerations

### Performance Requirements
- **Response Time**: < 3 seconds for RAG pipeline queries
- **Database Size**: 598KB for complete RAG database
- **Content Coverage**: 969 chunks from 679 processed pages
- **Memory Usage**: Optimized for 2GB+ RAM devices
- **Search Performance**: Sub-second text search, < 2 seconds for semantic search

### Security & Privacy
- **Data Privacy**: All processing on-device, no data transmission
- **Medical Compliance**: Designed for healthcare data handling standards
- **Audit Trail**: Optional logging for clinical decision support

### Scalability
- **Model Updates**: Capability to update STG content and models
- **Multi-language**: Architecture supports future localization
- **Integration**: API-ready for integration with other medical systems

## Success Metrics

### Clinical Effectiveness
- **Accuracy**: 100% citation coverage for verifiable responses
- **Coverage**: 31 chapters, 304 conditions, 555 medications extracted
- **Quality**: OCR extraction achieved 584 real conditions vs 382 abbreviations from text
- **Relevance**: RAG pipeline ensures contextually appropriate responses

### User Experience
- **Response Time**: Average query resolution < 3 seconds
- **User Satisfaction**: Healthcare provider feedback and adoption rates
- **Reliability**: 99.9% uptime in offline mode

### Technical Performance
- **Database Query Speed**: < 500ms for content retrieval
- **Embedding Search**: < 1 second for semantic matching
- **Memory Footprint**: < 200MB active memory usage

## Development Resources

### Required Skills
- Android development (Kotlin, Jetpack Compose)
- Machine Learning (TensorFlow Lite, embeddings)
- Database design (Room, SQLite)
- PDF processing and text extraction
- Medical domain knowledge (beneficial)

### External Dependencies
- TensorFlow Lite (for embeddings and local LLM)
- Room Database (for local storage)
- PDFBox or similar (for PDF parsing)
- Retrofit (for future sync capabilities)
- Jetpack Compose (for UI)

### Development Progress
- **Phase 1**: ✅ COMPLETE (Database implementation)
- **Phase 2**: ✅ COMPLETE (OCR extraction and RAG pipeline)
- **Phase 3**: 🔄 IN PROGRESS (Embedding generation and semantic search)
- **Phase 4**: ⏳ PENDING (Gemma 2 LLM integration)
- **Phase 5**: ⏳ PENDING (UI development with Jetpack Compose)
- **Phase 6**: ⏳ PENDING (Testing and optimization)
- **Progress**: 70% complete

## Risk Mitigation

### Technical Risks
- **Model Size**: Use quantized models and dynamic loading
- **Parsing Accuracy**: Implement validation and manual review processes
- **Performance**: Progressive optimization and profiling

### Clinical Risks
- **Accuracy**: Extensive validation against original STG document
- **Liability**: Clear disclaimers about clinical decision support tool
- **Updates**: Version control for STG content and model updates

## Future Enhancements

### Planned Features
- **Multi-language Support**: Local language translations
- **Voice Interface**: Speech-to-text for hands-free operation
- **Integration**: Connect with Electronic Health Records (EHR)
- **Analytics**: Usage patterns and improvement insights
- **Continuing Education**: Link to relevant medical education resources

### Expansion Possibilities
- **Other Guidelines**: Support for additional medical guidelines
- **Regional Adaptation**: Customization for other countries' medical standards
- **Specialized Modules**: Emergency medicine, surgery-specific modules
- **Community Features**: Healthcare provider collaboration tools

This comprehensive project plan provides the foundation for building a robust, offline-capable clinical decision support tool that will significantly benefit healthcare providers in Ghana by providing instant access to evidence-based treatment guidelines.
