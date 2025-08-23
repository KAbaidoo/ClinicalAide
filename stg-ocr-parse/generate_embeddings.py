#!/usr/bin/env python3
"""
Generate TensorFlow embeddings for content chunks
Uses sentence-transformers for medical text embeddings
"""

import sqlite3
import numpy as np
import logging
from typing import List, Tuple
import pickle
import json

# For embedding generation
from sentence_transformers import SentenceTransformer

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class EmbeddingGenerator:
    def __init__(self, db_path: str = "stg_rag_complete.db"):
        self.db_path = db_path
        # Use a model that works well for medical text
        # This model creates 384-dimensional embeddings
        self.model = SentenceTransformer('all-MiniLM-L6-v2')
        self.embedding_dim = 384
        
    def generate_all_embeddings(self):
        """Generate embeddings for all content chunks"""
        logger.info("Starting embedding generation...")
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Get all chunks without embeddings
        cursor.execute("""
            SELECT id, content 
            FROM content_chunks 
            WHERE embedding IS NULL
        """)
        
        chunks = cursor.fetchall()
        logger.info(f"Generating embeddings for {len(chunks)} chunks...")
        
        # Process in batches for efficiency
        batch_size = 32
        for i in range(0, len(chunks), batch_size):
            batch = chunks[i:i+batch_size]
            chunk_ids = [c[0] for c in batch]
            texts = [c[1] for c in batch]
            
            # Generate embeddings
            embeddings = self.model.encode(texts, show_progress_bar=False)
            
            # Store embeddings
            for chunk_id, embedding in zip(chunk_ids, embeddings):
                # Convert to bytes for storage
                embedding_bytes = embedding.astype(np.float32).tobytes()
                
                cursor.execute("""
                    UPDATE content_chunks 
                    SET embedding = ? 
                    WHERE id = ?
                """, (embedding_bytes, chunk_id))
                
                # Also insert into embeddings table
                cursor.execute("""
                    INSERT INTO embeddings (chunk_id, embedding, model_name, dimension)
                    VALUES (?, ?, ?, ?)
                """, (chunk_id, embedding_bytes, 'all-MiniLM-L6-v2', self.embedding_dim))
            
            conn.commit()
            
            if (i + batch_size) % 100 == 0:
                logger.info(f"Processed {min(i + batch_size, len(chunks))}/{len(chunks)} chunks")
        
        conn.close()
        logger.info("Embedding generation complete!")
        
    def search_similar(self, query: str, top_k: int = 5) -> List[Tuple]:
        """Search for similar content using embeddings"""
        
        # Generate query embedding
        query_embedding = self.model.encode([query])[0]
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Get all embeddings
        cursor.execute("""
            SELECT id, content, reference_citation, embedding, condition_name, chunk_type
            FROM content_chunks
            WHERE embedding IS NOT NULL
        """)
        
        results = []
        for row in cursor.fetchall():
            chunk_id, content, reference, embedding_bytes, condition, chunk_type = row
            
            # Convert bytes back to numpy array
            embedding = np.frombuffer(embedding_bytes, dtype=np.float32)
            
            # Calculate cosine similarity
            similarity = np.dot(query_embedding, embedding) / (
                np.linalg.norm(query_embedding) * np.linalg.norm(embedding)
            )
            
            results.append((similarity, content, reference, condition, chunk_type))
        
        # Sort by similarity
        results.sort(reverse=True, key=lambda x: x[0])
        
        conn.close()
        
        return results[:top_k]
        
    def test_search(self):
        """Test the embedding search with sample queries"""
        
        test_queries = [
            "What is the treatment for malaria?",
            "How to manage hypertension?",
            "Antibiotics for pneumonia in children",
            "Diabetes management guidelines",
            "First aid for burns"
        ]
        
        print("\n" + "="*60)
        print("EMBEDDING SEARCH TEST")
        print("="*60)
        
        for query in test_queries:
            print(f"\nQuery: {query}")
            print("-"*50)
            
            results = self.search_similar(query, top_k=3)
            
            for i, (score, content, reference, condition, chunk_type) in enumerate(results, 1):
                print(f"\n{i}. Similarity: {score:.3f}")
                print(f"   Type: {chunk_type}")
                print(f"   Condition: {condition}")
                print(f"   Content: {content[:150]}...")
                print(f"   Reference: Ghana STG 2017 - {reference}")
                
def create_android_export():
    """Create a lightweight export for Android"""
    logger.info("Creating Android export...")
    
    conn = sqlite3.connect("stg_rag_complete.db")
    cursor = conn.cursor()
    
    # Create Android-optimized database
    android_conn = sqlite3.connect("stg_android_rag.db")
    android_cursor = android_conn.cursor()
    
    # Create simplified schema for Android
    android_cursor.executescript("""
    DROP TABLE IF EXISTS content_chunks;
    DROP TABLE IF EXISTS embeddings_metadata;
    
    CREATE TABLE content_chunks (
        id INTEGER PRIMARY KEY,
        content TEXT NOT NULL,
        chunk_type TEXT NOT NULL,
        condition_name TEXT,
        chapter_number INTEGER,
        section_number TEXT,
        page_number INTEGER,
        reference_citation TEXT NOT NULL,
        embedding BLOB
    );
    
    CREATE TABLE embeddings_metadata (
        model_name TEXT,
        dimension INTEGER,
        total_chunks INTEGER
    );
    
    CREATE INDEX idx_chunks_embedding ON content_chunks(embedding);
    CREATE INDEX idx_chunks_condition ON content_chunks(condition_name);
    """)
    
    # Copy data
    cursor.execute("""
        SELECT id, content, chunk_type, condition_name, chapter_number, 
               section_number, page_number, reference_citation, embedding
        FROM content_chunks
    """)
    
    for row in cursor.fetchall():
        android_cursor.execute("""
            INSERT INTO content_chunks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, row)
    
    # Add metadata
    android_cursor.execute("""
        INSERT INTO embeddings_metadata VALUES (?, ?, ?)
    """, ('all-MiniLM-L6-v2', 384, android_cursor.lastrowid))
    
    android_conn.commit()
    android_conn.close()
    conn.close()
    
    # Copy to Android project
    import shutil
    shutil.copy("stg_android_rag.db",
                "/Users/kobby/AndroidStudioProjects/ClinicalAide/app/src/main/assets/databases/stg_android_rag.db")
    
    logger.info("Android export complete!")
    
def main():
    # Generate embeddings
    generator = EmbeddingGenerator()
    generator.generate_all_embeddings()
    
    # Test search
    generator.test_search()
    
    # Create Android export
    create_android_export()
    
    # Print statistics
    conn = sqlite3.connect("stg_rag_complete.db")
    cursor = conn.cursor()
    
    cursor.execute("SELECT COUNT(*) FROM content_chunks WHERE embedding IS NOT NULL")
    embedded_count = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM content_chunks")
    total_count = cursor.fetchone()[0]
    
    print("\n" + "="*60)
    print("EMBEDDING GENERATION COMPLETE")
    print("="*60)
    print(f"Total chunks: {total_count}")
    print(f"Chunks with embeddings: {embedded_count}")
    print(f"Embedding dimension: 384")
    print(f"Model: all-MiniLM-L6-v2")
    print(f"Database: stg_android_rag.db")
    
    conn.close()

if __name__ == "__main__":
    main()