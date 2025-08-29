#!/usr/bin/env python3
"""
Generate embeddings for common medical queries using the same model as the content embeddings.
This creates a lookup table for the Android app to use for semantic search.
"""

import sqlite3
import numpy as np
from sentence_transformers import SentenceTransformer
import json
import struct
from tqdm import tqdm

class QueryEmbeddingGenerator:
    def __init__(self, db_path="stg_rag.db", model_name="all-MiniLM-L6-v2"):
        """
        Initialize the query embedding generator.
        
        Args:
            db_path: Path to the SQLite database
            model_name: Same model used for content embeddings
        """
        self.db_path = db_path
        self.model_name = model_name
        self.conn = None
        self.model = None
        
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
        self.model = SentenceTransformer(self.model_name)
        print(f"Model loaded. Embedding dimension: {self.model.get_sentence_embedding_dimension()}")
        return self.model.get_sentence_embedding_dimension()
        
    def create_query_table(self):
        """Create table for storing query embeddings."""
        cursor = self.conn.cursor()
        
        # Create query embeddings table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS query_embeddings (
                query_id INTEGER PRIMARY KEY AUTOINCREMENT,
                query_text TEXT NOT NULL UNIQUE,
                query_category TEXT,
                embedding BLOB NOT NULL,
                usage_count INTEGER DEFAULT 0
            )
        """)
        
        self.conn.commit()
        print("Query embeddings table created/verified")
        
    def get_common_queries(self):
        """Define common medical queries that users might search for."""
        queries = {
            # Disease/condition queries
            "General Conditions": [
                "malaria",
                "malaria treatment",
                "treatment for malaria",
                "malaria symptoms",
                "malaria diagnosis",
                "severe malaria",
                "uncomplicated malaria",
                "malaria in children",
                "malaria in pregnancy",
                "fever",
                "high fever",
                "fever treatment",
                "fever in children",
                "headache",
                "severe headache",
                "migraine",
                "diarrhea",
                "diarrhoea",
                "diarrhea treatment",
                "pediatric diarrhea",
                "diarrhea with dehydration",
                "acute diarrhea",
                "chronic diarrhea",
                "vomiting",
                "nausea and vomiting",
                "pneumonia",
                "pneumonia treatment",
                "community acquired pneumonia",
                "pneumonia in children",
                "cough",
                "persistent cough",
                "productive cough",
                "dry cough",
                "tuberculosis",
                "TB treatment",
                "HIV",
                "AIDS",
                "HIV treatment",
                "antiretroviral therapy",
                "ART",
                "diabetes",
                "diabetes management",
                "diabetes type 2",
                "hypertension",
                "high blood pressure",
                "blood pressure management",
            ],
            
            # Pediatric queries
            "Pediatric": [
                "pediatric",
                "children",
                "infant",
                "newborn",
                "pediatric dosing",
                "dosage for children",
                "child fever",
                "infant diarrhea",
                "newborn care",
                "vaccination",
                "immunization",
                "growth monitoring",
                "malnutrition in children",
                "dehydration in children",
            ],
            
            # Emergency queries
            "Emergency": [
                "emergency",
                "emergency care",
                "first aid",
                "poisoning",
                "snake bite",
                "trauma",
                "injury",
                "bleeding",
                "shock",
                "cardiac arrest",
                "respiratory distress",
                "acute abdomen",
                "convulsions",
                "seizures",
                "unconscious patient",
            ],
            
            # Medication queries
            "Medications": [
                "antibiotics",
                "first line antibiotics",
                "antibiotic for pneumonia",
                "paracetamol",
                "paracetamol dosage",
                "ibuprofen",
                "amoxicillin",
                "amoxicillin dosage",
                "artemether lumefantrine",
                "antimalarial drugs",
                "ORS",
                "oral rehydration solution",
                "zinc supplementation",
                "vitamin A",
                "iron supplementation",
            ],
            
            # Pregnancy related
            "Pregnancy": [
                "pregnancy",
                "pregnant women",
                "antenatal care",
                "prenatal care",
                "pregnancy complications",
                "preeclampsia",
                "eclampsia",
                "postpartum hemorrhage",
                "labor",
                "delivery",
                "safe in pregnancy",
                "contraindicated in pregnancy",
            ],
            
            # Dosage queries
            "Dosage": [
                "dosage",
                "dose",
                "how much",
                "dosing",
                "adult dose",
                "pediatric dose",
                "weight based dosing",
                "maximum dose",
                "frequency",
                "duration of treatment",
            ],
            
            # Diagnostic queries
            "Diagnostic": [
                "diagnosis",
                "diagnostic criteria",
                "laboratory tests",
                "investigations",
                "differential diagnosis",
                "signs and symptoms",
                "clinical features",
                "physical examination",
            ],
            
            # Treatment protocols
            "Protocols": [
                "treatment protocol",
                "management",
                "clinical guidelines",
                "referral criteria",
                "when to refer",
                "follow up",
                "monitoring",
                "complications",
                "prevention",
            ]
        }
        
        # Flatten queries list
        all_queries = []
        for category, query_list in queries.items():
            for query in query_list:
                all_queries.append((query, category))
                
        return all_queries
        
    def generate_embeddings(self):
        """Generate embeddings for all common queries."""
        queries = self.get_common_queries()
        print(f"Generating embeddings for {len(queries)} queries...")
        
        cursor = self.conn.cursor()
        
        for query_text, category in tqdm(queries):
            # Check if query already exists
            cursor.execute("SELECT query_id FROM query_embeddings WHERE query_text = ?", (query_text,))
            if cursor.fetchone():
                print(f"Skipping existing query: {query_text}")
                continue
                
            # Generate embedding
            embedding = self.model.encode(query_text, convert_to_numpy=True)
            embedding_blob = embedding.astype(np.float32).tobytes()
            
            # Insert into database
            cursor.execute("""
                INSERT INTO query_embeddings (query_text, query_category, embedding)
                VALUES (?, ?, ?)
            """, (query_text, category, embedding_blob))
            
        self.conn.commit()
        print("Query embeddings generated and stored")
        
    def export_embeddings_for_android(self):
        """Export embeddings in a format suitable for Android app."""
        cursor = self.conn.cursor()
        cursor.execute("""
            SELECT query_text, query_category, embedding
            FROM query_embeddings
            ORDER BY query_category, query_text
        """)
        
        results = cursor.fetchall()
        
        # Create JSON file with embeddings
        export_data = []
        for query_text, category, embedding_blob in results:
            # Convert blob to list of floats
            embedding_array = np.frombuffer(embedding_blob, dtype=np.float32)
            
            export_data.append({
                "query": query_text,
                "category": category,
                "embedding": embedding_array.tolist()  # Convert to list for JSON
            })
        
        # Save to JSON file
        with open("query_embeddings.json", "w") as f:
            json.dump(export_data, f, indent=2)
            
        print(f"Exported {len(export_data)} query embeddings to query_embeddings.json")
        
        # Also create a compact binary format for Android
        with open("query_embeddings.bin", "wb") as f:
            # Write header: number of queries
            f.write(struct.pack('I', len(export_data)))
            
            for item in export_data:
                query_bytes = item["query"].encode('utf-8')
                category_bytes = item["category"].encode('utf-8')
                
                # Write query length and text
                f.write(struct.pack('I', len(query_bytes)))
                f.write(query_bytes)
                
                # Write category length and text
                f.write(struct.pack('I', len(category_bytes)))
                f.write(category_bytes)
                
                # Write embedding (384 floats)
                for value in item["embedding"]:
                    f.write(struct.pack('f', value))
                    
        print(f"Exported binary format to query_embeddings.bin")
        
    def test_similarity(self):
        """Test similarity between some queries and content."""
        cursor = self.conn.cursor()
        
        # Get a sample query embedding
        cursor.execute("""
            SELECT query_text, embedding 
            FROM query_embeddings 
            WHERE query_text = 'malaria treatment'
        """)
        result = cursor.fetchone()
        
        if result:
            query_text, query_embedding_blob = result
            query_embedding = np.frombuffer(query_embedding_blob, dtype=np.float32)
            
            # Get some content embeddings
            cursor.execute("""
                SELECT c.content_text, e.embedding
                FROM embeddings e
                JOIN content c ON e.content_id = c.content_id
                WHERE c.content_text LIKE '%malaria%'
                LIMIT 5
            """)
            
            content_results = cursor.fetchall()
            
            print(f"\nSimilarity test for query: '{query_text}'")
            print("-" * 60)
            
            for content_text, content_embedding_blob in content_results:
                content_embedding = np.frombuffer(content_embedding_blob, dtype=np.float32)
                
                # Calculate cosine similarity
                similarity = np.dot(query_embedding, content_embedding) / (
                    np.linalg.norm(query_embedding) * np.linalg.norm(content_embedding)
                )
                
                print(f"Similarity: {similarity:.3f}")
                print(f"Content: {content_text[:100]}...")
                print()

def main():
    generator = QueryEmbeddingGenerator()
    
    try:
        # Connect to database
        generator.connect()
        
        # Load model
        embedding_dim = generator.load_model()
        print(f"Using embedding dimension: {embedding_dim}")
        
        # Create table
        generator.create_query_table()
        
        # Generate embeddings
        generator.generate_embeddings()
        
        # Export for Android
        generator.export_embeddings_for_android()
        
        # Test similarity
        generator.test_similarity()
        
        # Get statistics
        cursor = generator.conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM query_embeddings")
        count = cursor.fetchone()[0]
        print(f"\nTotal query embeddings in database: {count}")
        
    finally:
        generator.disconnect()

if __name__ == "__main__":
    main()