#!/usr/bin/env python3
"""
Generate embeddings for all content in the STG database using sentence-transformers.
This enables semantic search capabilities for the RAG system.
"""

import sqlite3
import numpy as np
from sentence_transformers import SentenceTransformer
import time
from tqdm import tqdm
import struct

class EmbeddingGenerator:
    def __init__(self, db_path="stg_rag.db", model_name="all-MiniLM-L6-v2"):
        """
        Initialize the embedding generator.
        
        Args:
            db_path: Path to the SQLite database
            model_name: Name of the sentence-transformer model to use
                       Options: 
                       - "all-MiniLM-L6-v2" (384 dims, fast, 80MB)
                       - "all-mpnet-base-v2" (768 dims, high quality, 420MB)
        """
        self.db_path = db_path
        self.model_name = model_name
        self.conn = None
        self.model = None
        self.batch_size = 32
        self.max_length = 512  # Maximum token length for the model
        
    def connect(self):
        """Connect to the database."""
        self.conn = sqlite3.connect(self.db_path)
        print(f"Connected to database: {self.db_path}")
        
    def disconnect(self):
        """Disconnect from the database."""
        if self.conn:
            self.conn.close()
            print("Disconnected from database")
            
    def load_model(self):
        """Load the sentence transformer model."""
        print(f"Loading model: {self.model_name}")
        print("This may take a minute on first run to download the model...")
        self.model = SentenceTransformer(self.model_name)
        print(f"Model loaded. Embedding dimension: {self.model.get_sentence_embedding_dimension()}")
        return self.model.get_sentence_embedding_dimension()
        
    def numpy_to_blob(self, array):
        """Convert numpy array to blob for SQLite storage."""
        return array.astype(np.float32).tobytes()
        
    def blob_to_numpy(self, blob):
        """Convert blob back to numpy array."""
        return np.frombuffer(blob, dtype=np.float32)
        
    def get_content(self):
        """Fetch all content that needs embeddings."""
        cursor = self.conn.cursor()
        cursor.execute("""
            SELECT c.content_id, c.content_text, c.content_type,
                   s.section_title, ch.chapter_title
            FROM content c
            JOIN sections s ON c.section_id = s.section_id
            JOIN chapters ch ON s.chapter_id = ch.chapter_id
            ORDER BY c.content_id
        """)
        return cursor.fetchall()
        
    def prepare_text(self, content_text, content_type, section_title, chapter_title):
        """
        Prepare text for embedding by adding context.
        This helps the model understand the medical context better.
        """
        # Add contextual information for better semantic understanding
        context_parts = []
        
        # Add chapter and section context
        context_parts.append(f"Chapter: {chapter_title}")
        context_parts.append(f"Section: {section_title}")
        
        # Add content type hint
        if content_type == "table":
            context_parts.append("Table content:")
        elif content_type == "bullet":
            context_parts.append("Key points:")
        
        # Add the actual content
        context_parts.append(content_text)
        
        # Combine with newlines
        prepared_text = "\n".join(context_parts)
        
        # Truncate if too long (models have token limits)
        if len(prepared_text) > 2000:  # Rough character limit
            prepared_text = prepared_text[:2000] + "..."
            
        return prepared_text
        
    def generate_embeddings(self):
        """Generate embeddings for all content."""
        content_data = self.get_content()
        total_content = len(content_data)
        
        print(f"\nFound {total_content} content entries to process")
        print(f"Processing in batches of {self.batch_size}...")
        
        # Clear existing embeddings
        cursor = self.conn.cursor()
        cursor.execute("DELETE FROM embeddings")
        self.conn.commit()
        print("Cleared existing embeddings")
        
        # Process in batches
        for i in tqdm(range(0, total_content, self.batch_size), desc="Generating embeddings"):
            batch = content_data[i:i + self.batch_size]
            
            # Prepare texts for this batch
            batch_texts = []
            batch_ids = []
            
            for content_id, content_text, content_type, section_title, chapter_title in batch:
                prepared_text = self.prepare_text(content_text, content_type, section_title, chapter_title)
                batch_texts.append(prepared_text)
                batch_ids.append(content_id)
            
            # Generate embeddings for the batch
            embeddings = self.model.encode(batch_texts, show_progress_bar=False)
            
            # Store embeddings in database
            for content_id, embedding in zip(batch_ids, embeddings):
                embedding_blob = self.numpy_to_blob(embedding)
                cursor.execute("""
                    INSERT INTO embeddings (content_id, embedding)
                    VALUES (?, ?)
                """, (content_id, embedding_blob))
            
            # Commit after each batch
            self.conn.commit()
            
        print(f"\nSuccessfully generated {total_content} embeddings")
        
    def verify_embeddings(self):
        """Verify that embeddings were generated correctly."""
        cursor = self.conn.cursor()
        
        # Check counts
        cursor.execute("SELECT COUNT(*) FROM content")
        content_count = cursor.fetchone()[0]
        
        cursor.execute("SELECT COUNT(*) FROM embeddings")
        embedding_count = cursor.fetchone()[0]
        
        print(f"\nVerification:")
        print(f"  Content entries: {content_count}")
        print(f"  Embeddings generated: {embedding_count}")
        
        if content_count == embedding_count:
            print("  ✅ All content has embeddings")
        else:
            print(f"  ⚠️ Missing {content_count - embedding_count} embeddings")
            
        # Check embedding dimensions
        cursor.execute("SELECT embedding FROM embeddings LIMIT 1")
        sample_blob = cursor.fetchone()[0]
        sample_embedding = self.blob_to_numpy(sample_blob)
        print(f"  Embedding dimensions: {len(sample_embedding)}")
        
        # Test semantic similarity with a sample query
        self.test_similarity()
        
    def test_similarity(self):
        """Test semantic similarity with a medical query."""
        test_queries = [
            "treatment for malaria in children",
            "diarrhea and dehydration management",
            "hypertension medication dosage"
        ]
        
        print("\n🧪 Testing semantic search...")
        
        for query in test_queries:
            print(f"\nQuery: '{query}'")
            
            # Generate query embedding
            query_embedding = self.model.encode(query)
            
            # Find most similar content
            cursor = self.conn.cursor()
            cursor.execute("""
                SELECT c.content_id, c.content_text, e.embedding
                FROM content c
                JOIN embeddings e ON c.content_id = e.content_id
                LIMIT 100
            """)
            
            results = []
            for content_id, content_text, embedding_blob in cursor.fetchall():
                content_embedding = self.blob_to_numpy(embedding_blob)
                
                # Calculate cosine similarity
                similarity = np.dot(query_embedding, content_embedding) / (
                    np.linalg.norm(query_embedding) * np.linalg.norm(content_embedding)
                )
                
                results.append((content_id, content_text[:100], similarity))
            
            # Sort by similarity
            results.sort(key=lambda x: x[2], reverse=True)
            
            # Show top 3 results
            print("  Top 3 matches:")
            for i, (content_id, snippet, similarity) in enumerate(results[:3], 1):
                print(f"    {i}. (ID: {content_id}, Similarity: {similarity:.3f})")
                print(f"       {snippet}...")
                
    def get_database_stats(self):
        """Get statistics about the database after embedding generation."""
        cursor = self.conn.cursor()
        
        # Get database file size
        import os
        db_size_mb = os.path.getsize(self.db_path) / (1024 * 1024)
        
        print(f"\n📊 Database Statistics:")
        print(f"  Database size: {db_size_mb:.2f} MB")
        
        # Get table sizes
        cursor.execute("""
            SELECT name, COUNT(*) as rows
            FROM sqlite_master 
            LEFT JOIN pragma_table_info(sqlite_master.name) ON 1=1
            WHERE type='table' AND name NOT LIKE 'sqlite_%'
            GROUP BY name
        """)
        
        cursor.execute("""
            SELECT 'chapters' as table_name, COUNT(*) as row_count FROM chapters
            UNION SELECT 'sections', COUNT(*) FROM sections
            UNION SELECT 'content', COUNT(*) FROM content
            UNION SELECT 'embeddings', COUNT(*) FROM embeddings
            UNION SELECT 'metadata', COUNT(*) FROM metadata
        """)
        
        for table_name, row_count in cursor.fetchall():
            print(f"  {table_name}: {row_count} rows")
            
    def run(self):
        """Main execution function."""
        start_time = time.time()
        
        try:
            # Connect to database
            self.connect()
            
            # Load model
            embedding_dim = self.load_model()
            
            # Generate embeddings
            self.generate_embeddings()
            
            # Verify results
            self.verify_embeddings()
            
            # Show statistics
            self.get_database_stats()
            
            elapsed_time = time.time() - start_time
            print(f"\n✅ Embedding generation complete in {elapsed_time:.1f} seconds")
            
            # Play completion sound
            import subprocess
            subprocess.run(['afplay', '/System/Library/Sounds/Glass.aiff'], check=False)
            
        finally:
            self.disconnect()


def main():
    """Main entry point."""
    print("=" * 60)
    print("STG Medical Content Embedding Generator")
    print("=" * 60)
    
    # You can change the model here if needed
    # Options: "all-MiniLM-L6-v2" (fast, 384 dims) or "all-mpnet-base-v2" (quality, 768 dims)
    generator = EmbeddingGenerator(db_path="stg_rag.db", model_name="all-MiniLM-L6-v2")
    generator.run()
    
    print("\n📝 Next steps:")
    print("1. Copy the updated database to Android assets:")
    print("   cp stg_rag.db ../app/src/main/assets/databases/")
    print("2. Build and test the Android app with semantic search")


if __name__ == "__main__":
    main()