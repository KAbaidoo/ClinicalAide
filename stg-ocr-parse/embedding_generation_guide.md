# Embedding Generation Guide
## TensorFlow Integration for Ghana STG Medical AI Chatbot

This guide covers generating vector embeddings for the 969 content chunks in the RAG database for use with Gemma 2 in your Android application.

---

## Overview

### Current Database State
- **Database**: `stg_rag_complete.db`
- **Content Chunks**: 969 pre-processed chunks with citations
- **Chunk Types**: treatment, clinical_features, investigations, medication
- **Ready for**: TensorFlow/sentence-transformers embedding generation

### Architecture
```
969 Content Chunks → Generate Embeddings → Store in Database → Android TensorFlow Lite → Vector Search → Gemma 2
```

---

## 1. Setup and Dependencies

### Python Environment
```bash
# Option A: Using sentence-transformers (Recommended)
pip install sentence-transformers torch numpy

# Option B: Using TensorFlow
pip install tensorflow tensorflow-hub numpy

# Database access
pip install sqlite3
```

### Model Selection

#### Recommended Model (Best for Mobile)
```python
from sentence_transformers import SentenceTransformer

# 384-dimensional embeddings, optimized for mobile
model = SentenceTransformer('all-MiniLM-L6-v2')
embedding_dim = 384
```

#### Alternative Models
```python
# Higher quality, 768 dimensions
model = SentenceTransformer('all-mpnet-base-v2')
embedding_dim = 768

# Medical-specific (experimental)
model = SentenceTransformer('dmis-lab/biobert-base-cased-v1.1')
```

---

## 2. Generate Embeddings for RAG Database

### Complete Script
```python
#!/usr/bin/env python3
"""
Generate embeddings for all content chunks in the RAG database
"""

import sqlite3
import numpy as np
from sentence_transformers import SentenceTransformer
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class RAGEmbeddingGenerator:
    def __init__(self, db_path="stg_rag_complete.db"):
        self.db_path = db_path
        self.model = SentenceTransformer('all-MiniLM-L6-v2')
        self.embedding_dim = 384
        
    def generate_all_embeddings(self):
        """Generate embeddings for all 969 content chunks"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Get all chunks
        cursor.execute("""
            SELECT id, content 
            FROM content_chunks 
            ORDER BY id
        """)
        
        chunks = cursor.fetchall()
        logger.info(f"Generating embeddings for {len(chunks)} chunks...")
        
        # Process in batches
        batch_size = 32
        for i in range(0, len(chunks), batch_size):
            batch = chunks[i:i+batch_size]
            chunk_ids = [c[0] for c in batch]
            texts = [c[1] for c in batch]
            
            # Generate embeddings
            embeddings = self.model.encode(texts, show_progress_bar=False)
            
            # Store in database
            for chunk_id, embedding in zip(chunk_ids, embeddings):
                embedding_bytes = embedding.astype(np.float32).tobytes()
                
                # Update content_chunks table
                cursor.execute("""
                    UPDATE content_chunks 
                    SET embedding = ? 
                    WHERE id = ?
                """, (embedding_bytes, chunk_id))
                
                # Insert into embeddings table
                cursor.execute("""
                    INSERT OR REPLACE INTO embeddings 
                    (chunk_id, embedding, model_name, dimension)
                    VALUES (?, ?, ?, ?)
                """, (chunk_id, embedding_bytes, 'all-MiniLM-L6-v2', self.embedding_dim))
            
            conn.commit()
            logger.info(f"Processed {min(i+batch_size, len(chunks))}/{len(chunks)} chunks")
        
        conn.close()
        logger.info("Embedding generation complete!")
        
        # Print statistics
        self.print_statistics()
        
    def print_statistics(self):
        """Print embedding statistics"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("SELECT COUNT(*) FROM content_chunks WHERE embedding IS NOT NULL")
        embedded_count = cursor.fetchone()[0]
        
        cursor.execute("SELECT COUNT(*) FROM embeddings")
        embedding_table_count = cursor.fetchone()[0]
        
        print(f"\n{'='*60}")
        print("EMBEDDING GENERATION COMPLETE")
        print(f"{'='*60}")
        print(f"Content chunks with embeddings: {embedded_count}/969")
        print(f"Embeddings table entries: {embedding_table_count}")
        print(f"Model: all-MiniLM-L6-v2")
        print(f"Dimension: {self.embedding_dim}")
        print(f"Storage: ~1.5MB for all embeddings")
        
        conn.close()

if __name__ == "__main__":
    generator = RAGEmbeddingGenerator()
    generator.generate_all_embeddings()
```

---

## 3. Convert to TensorFlow Lite for Android

### TensorFlow Lite Conversion
```python
import tensorflow as tf
import tensorflow_hub as hub
import numpy as np

class TFLiteConverter:
    def __init__(self):
        # Load Universal Sentence Encoder
        self.embed = hub.load("https://tfhub.dev/google/universal-sentence-encoder-lite/2")
        
    def convert_to_tflite(self, output_path="use_lite.tflite"):
        """Convert model to TensorFlow Lite format"""
        
        # Create a concrete function
        text_input = tf.keras.layers.Input(shape=[], dtype=tf.string)
        
        # Get the embedding function
        embedding = tf.function(lambda x: self.embed(x))
        
        # Convert to TFLite
        converter = tf.lite.TFLiteConverter.from_concrete_functions([embedding.get_concrete_function(text_input)])
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        
        # Quantize to reduce size
        converter.target_spec.supported_types = [tf.float16]
        
        tflite_model = converter.convert()
        
        # Save
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
            
        print(f"Model saved to {output_path}")
        print(f"Size: {len(tflite_model) / 1024 / 1024:.2f} MB")
```

---

## 4. Similarity Search Implementation

### Python Reference Implementation
```python
def search_similar_chunks(query_text, top_k=5):
    """Search for similar chunks using embeddings"""
    
    # Generate query embedding
    model = SentenceTransformer('all-MiniLM-L6-v2')
    query_embedding = model.encode([query_text])[0]
    
    conn = sqlite3.connect("stg_rag_complete.db")
    cursor = conn.cursor()
    
    # Get all chunks with embeddings
    cursor.execute("""
        SELECT id, content, reference_citation, embedding, condition_name
        FROM content_chunks
        WHERE embedding IS NOT NULL
    """)
    
    results = []
    for row in cursor.fetchall():
        chunk_id, content, reference, embedding_bytes, condition = row
        
        # Convert bytes to numpy array
        chunk_embedding = np.frombuffer(embedding_bytes, dtype=np.float32)
        
        # Calculate cosine similarity
        similarity = np.dot(query_embedding, chunk_embedding) / (
            np.linalg.norm(query_embedding) * np.linalg.norm(chunk_embedding)
        )
        
        results.append({
            'id': chunk_id,
            'content': content,
            'reference': reference,
            'condition': condition,
            'similarity': similarity
        })
    
    # Sort by similarity
    results.sort(key=lambda x: x['similarity'], reverse=True)
    
    conn.close()
    
    return results[:top_k]

# Example usage
results = search_similar_chunks("treatment for malaria")
for r in results:
    print(f"Similarity: {r['similarity']:.3f}")
    print(f"Condition: {r['condition']}")
    print(f"Reference: {r['reference']}")
    print(f"Content: {r['content'][:200]}...")
    print("-" * 50)
```

---

## 5. Android Integration

### Load Embeddings in Android
```kotlin
// In your Repository
suspend fun loadEmbeddings(): Map<Int, FloatArray> {
    val embeddings = mutableMapOf<Int, FloatArray>()
    
    val cursor = database.rawQuery(
        "SELECT chunk_id, embedding FROM embeddings",
        null
    )
    
    while (cursor.moveToNext()) {
        val chunkId = cursor.getInt(0)
        val embeddingBlob = cursor.getBlob(1)
        
        // Convert BLOB to FloatArray
        val buffer = ByteBuffer.wrap(embeddingBlob)
        val floatBuffer = buffer.asFloatBuffer()
        val embedding = FloatArray(384)
        floatBuffer.get(embedding)
        
        embeddings[chunkId] = embedding
    }
    cursor.close()
    
    return embeddings
}

// Calculate similarity
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dotProduct = 0f
    var normA = 0f
    var normB = 0f
    
    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    
    return dotProduct / (sqrt(normA) * sqrt(normB))
}
```

---

## 6. Optimization Tips

### Storage Optimization
- **Float16 quantization**: Reduces embedding size by 50%
- **PCA reduction**: Reduce dimensions from 384 to 256
- **Batch processing**: Process embeddings in batches of 32-64

### Performance Optimization
```python
# Optimize for mobile with dimension reduction
from sklearn.decomposition import PCA

def reduce_dimensions(embeddings, target_dim=256):
    """Reduce embedding dimensions for mobile efficiency"""
    pca = PCA(n_components=target_dim)
    reduced = pca.fit_transform(embeddings)
    
    # Save PCA model for inference
    import pickle
    with open('pca_model.pkl', 'wb') as f:
        pickle.dump(pca, f)
    
    return reduced
```

---

## 7. Testing Embeddings

### Quality Test
```python
def test_embedding_quality():
    """Test if embeddings capture medical semantics"""
    
    test_queries = [
        ("malaria treatment", ["Artemether", "fever", "antimalarial"]),
        ("hypertension management", ["blood pressure", "ACE inhibitors", "cardiovascular"]),
        ("diabetes medication", ["insulin", "metformin", "glucose"])
    ]
    
    for query, expected_terms in test_queries:
        results = search_similar_chunks(query, top_k=3)
        
        print(f"\nQuery: {query}")
        found_terms = []
        for r in results:
            for term in expected_terms:
                if term.lower() in r['content'].lower():
                    found_terms.append(term)
        
        print(f"Found relevant terms: {set(found_terms)}")
        print(f"Top similarity: {results[0]['similarity']:.3f}")
```

---

## 8. Complete Workflow

### Step-by-step Implementation
```bash
# 1. Generate embeddings for all chunks
python3 generate_embeddings.py

# 2. Verify embeddings
sqlite3 stg_rag_complete.db "SELECT COUNT(*) FROM content_chunks WHERE embedding IS NOT NULL;"
# Should return: 969

# 3. Test similarity search
python3 -c "
from generate_embeddings import search_similar_chunks
results = search_similar_chunks('treatment for malaria')
print(f'Found {len(results)} similar chunks')
"

# 4. Copy to Android project
cp stg_rag_complete.db /Users/kobby/AndroidStudioProjects/ClinicalAide/app/src/main/assets/databases/stg_rag.db

# 5. Verify in Android
# Use Room database to query embeddings
```

---

## Summary

### What You Now Have:
- ✅ 969 content chunks ready for embedding
- ✅ Script to generate 384-dimensional embeddings
- ✅ Embeddings stored in BLOB format for Android
- ✅ Similarity search implementation
- ✅ TensorFlow Lite conversion guide
- ✅ Android integration code

### Next Steps:
1. Run the embedding generation script
2. Test similarity search
3. Copy database to Android project
4. Implement TensorFlow Lite model in Android
5. Connect to Gemma 2 for response generation

### Performance Expectations:
- Embedding generation: ~5-10 minutes for all 969 chunks
- Storage overhead: ~1.5MB for all embeddings
- Search time: <100ms for similarity search on mobile
- Memory usage: ~10MB during search