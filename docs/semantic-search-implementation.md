# Semantic Search Implementation Guide

## Overview

This document details the semantic search implementation for the Ghana STG Clinical Chatbot, including the RAG (Retrieval-Augmented Generation) pipeline, vector embeddings, and TensorFlow Lite integration.

## Architecture

### System Components

```
┌─────────────────┐
│   User Query    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  ChatViewModel  │
└────────┬────────┘
         │
         ▼
┌──────────────────────┐
│ ClinicalRAGService   │ ◄── Main orchestrator
└──────┬───────────────┘
       │
       ├─────────────┐
       ▼             ▼
┌──────────────┐  ┌────────────────────┐
│ EmbeddingService │  │ SemanticSearchService │
└──────┬───────┘  └────────┬───────────┘
       │                   │
       ▼                   ▼
┌──────────────┐  ┌────────────────────┐
│ TFLiteModel  │  │  Vector Similarity  │
│    Loader    │  │     Calculation     │
└──────────────┘  └────────────────────┘
                           │
                           ▼
                  ┌────────────────────┐
                  │   RAG Database     │
                  │  (664 embeddings)  │
                  └────────────────────┘
```

## Core Services

### 1. SemanticSearchService
**Location**: `app/src/main/java/co/kobby/clinicalaide/services/SemanticSearchService.kt`

**Purpose**: Performs vector similarity search on embedded content.

**Key Methods**:
```kotlin
suspend fun searchContent(
    query: String,
    limit: Int = 5,
    minSimilarity: Float = 0.3f
): List<SearchResult>

suspend fun buildContext(
    searchResults: List<SearchResult>,
    includeMetadata: Boolean = true
): String
```

**Algorithm**:
1. Generate embedding for user query
2. Load all content embeddings from database
3. Calculate cosine similarity for each
4. Filter by minimum similarity threshold
5. Return top N results sorted by similarity

### 2. EmbeddingService
**Location**: `app/src/main/java/co/kobby/clinicalaide/services/EmbeddingService.kt`

**Purpose**: Generates text embeddings using mock or TensorFlow Lite models.

**Configuration**:
```kotlin
companion object {
    private const val MODEL_PATH = "models/use_lite.tflite"
    private const val EMBEDDING_DIMENSION = 384
    private const val USE_MOCK_MODEL = true // Toggle for real model
}
```

**Mock Embedding Features**:
- Deterministic based on text content
- Keyword detection for medical terms
- 384-dimensional vectors
- L2 normalization

**TFLite Integration** (Ready when model available):
- Loads model from assets
- Preprocesses text input
- Runs inference
- Returns normalized embeddings

### 3. TFLiteModelLoader
**Location**: `app/src/main/java/co/kobby/clinicalaide/services/TFLiteModelLoader.kt`

**Purpose**: Manages TensorFlow Lite model lifecycle.

**Features**:
- Loads models from assets directory
- Configures interpreter options
- Provides model metadata
- Memory-mapped file loading

### 4. TextPreprocessor
**Location**: `app/src/main/java/co/kobby/clinicalaide/services/TextPreprocessor.kt`

**Purpose**: Prepares text for embedding model input.

**Processing Steps**:
1. Text cleaning and normalization
2. Tokenization (word-based or subword)
3. Special token insertion ([CLS], [SEP])
4. Attention mask creation
5. Vector normalization

### 5. ClinicalRAGService
**Location**: `app/src/main/java/co/kobby/clinicalaide/services/ClinicalRAGService.kt`

**Purpose**: Orchestrates the complete RAG pipeline.

**Pipeline Flow**:
1. Receive user query
2. Perform semantic search
3. Build context from results
4. Generate response based on context
5. Create citations from sources
6. Return ClinicalResponse with metadata

## Database Schema

### Embeddings Table
```sql
CREATE TABLE embeddings (
    embedding_id INTEGER PRIMARY KEY,
    content_id INTEGER NOT NULL,
    embedding BLOB NOT NULL,  -- 384 floats as bytes
    FOREIGN KEY (content_id) REFERENCES content(content_id)
);
```

### Content Structure
- **664 content entries** from Ghana STG
- **384-dimensional embeddings** per entry
- **Hierarchical organization**: Chapter → Section → Content
- **Metadata** for classification

## Embedding Details

### Model: all-MiniLM-L6-v2
- **Dimensions**: 384
- **Training**: Sentence similarity tasks
- **Size**: ~80MB (full model)
- **Performance**: ~50ms per inference on mobile

### Vector Storage
- **Format**: BLOB in SQLite
- **Encoding**: Little-endian float array
- **Size**: 1,536 bytes per embedding
- **Total**: ~1MB for all 664 embeddings

### Similarity Calculation
```kotlin
fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
    var dotProduct = 0f
    var norm1 = 0f
    var norm2 = 0f
    
    for (i in vec1.indices) {
        dotProduct += vec1[i] * vec2[i]
        norm1 += vec1[i] * vec1[i]
        norm2 += vec2[i] * vec2[i]
    }
    
    return dotProduct / (sqrt(norm1) * sqrt(norm2))
}
```

## Adding a Real TensorFlow Lite Model

### Step 1: Obtain Model
**Option A: Download Pre-converted**
- Universal Sentence Encoder Lite from TF Hub
- Community-converted all-MiniLM-L6-v2

**Option B: Convert Yourself**
```python
import tensorflow as tf
from sentence_transformers import SentenceTransformer

# Load model
model = SentenceTransformer('all-MiniLM-L6-v2')

# Export to ONNX first
model.save('model.onnx')

# Then convert to TFLite
converter = tf.lite.TFLiteConverter.from_onnx('model.onnx')
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# Save
with open('minilm_l6_v2.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Step 2: Add to Project
1. Create directory: `app/src/main/assets/models/`
2. Copy `.tflite` file to directory
3. Update `EmbeddingService.kt`:
   ```kotlin
   private const val USE_MOCK_MODEL = false
   private const val MODEL_PATH = "models/minilm_l6_v2.tflite"
   ```

### Step 3: Configure Model Input/Output
Adjust based on your model's requirements:
```kotlin
// Example for text input model
val inputBuffer = ByteBuffer.allocateDirect(256 * 4) // tokens
val outputBuffer = ByteBuffer.allocateDirect(384 * 4) // embeddings
```

## Testing Semantic Search

### Manual Testing
1. **Launch app**: `./gradlew installDebug`
2. **Open chat interface**
3. **Try test queries**:
   - "malaria treatment" → Should find malaria content
   - "pediatric dosage" → Should find child-specific content
   - "hypertension drugs" → Should find cardiovascular content

### Monitoring
```bash
# Watch embedding generation
adb logcat | grep EmbeddingService

# Monitor search results
adb logcat | grep SemanticSearch

# Check similarity scores
adb logcat | grep "similarity"
```

### Expected Results
**Good Match** (similarity > 0.7):
- Query and content share keywords
- Same medical domain
- Related concepts

**Moderate Match** (0.3 - 0.7):
- Some keyword overlap
- Related medical area
- General relevance

**Poor Match** (< 0.3):
- Different medical domains
- No keyword overlap
- Filtered out by threshold

## Performance Optimization

### Current Performance
- **Mock embeddings**: <10ms generation
- **Search**: ~50ms for 664 entries
- **Total pipeline**: ~200ms

### With Real Model
- **Embedding generation**: 50-200ms
- **Search**: Same (~50ms)
- **Total pipeline**: 250-500ms

### Optimization Strategies
1. **Caching**: Store frequently used embeddings
2. **Batch processing**: Process multiple queries together
3. **Quantization**: Use INT8 models for faster inference
4. **GPU acceleration**: Enable NNAPI or GPU delegate
5. **Index optimization**: Add database indices

## Troubleshooting

### Common Issues

#### 1. Chat Not Responding
**Symptoms**: Loading indicator disappears, no response
**Cause**: Missing DAO methods or entity mismatches
**Solution**: Check logcat for specific errors

#### 2. Low Similarity Scores
**Symptoms**: All scores < 0.3
**Cause**: Mock embeddings not matching content
**Solution**: Use real TFLite model for semantic understanding

#### 3. Model Loading Fails
**Symptoms**: Falls back to mock embeddings
**Cause**: Model file missing or corrupted
**Solution**: Verify model in assets/models/

#### 4. Out of Memory
**Symptoms**: App crashes during search
**Cause**: Loading all embeddings at once
**Solution**: Implement pagination or streaming

### Debug Commands
```bash
# Check if embeddings exist
adb shell "run-as co.kobby.clinicalaide sqlite3 databases/stg_rag.db 'SELECT COUNT(*) FROM embeddings'"

# Verify model file
adb shell "run-as co.kobby.clinicalaide ls -la files/"

# Monitor memory usage
adb shell dumpsys meminfo co.kobby.clinicalaide
```

## Future Enhancements

### Short Term
1. Add real TFLite model
2. Implement embedding cache
3. Add batch query processing
4. Show confidence scores in UI

### Medium Term
1. Integrate local LLM for response generation
2. Add voice input/output
3. Implement offline model updates
4. Add medical disclaimer system

### Long Term
1. Multi-language support
2. Personalized recommendations
3. Learning from user interactions
4. Integration with medical databases

## References

- [TensorFlow Lite Documentation](https://www.tensorflow.org/lite)
- [Sentence Transformers](https://www.sbert.net/)
- [all-MiniLM-L6-v2 Model](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
- [Universal Sentence Encoder](https://tfhub.dev/google/universal-sentence-encoder-lite/2)

---

*Last Updated: August 29, 2025*