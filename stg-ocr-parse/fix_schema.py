#!/usr/bin/env python3
"""
Fix database schema to add explicit NOT NULL constraints for Room compatibility
"""

import sqlite3
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def fix_database_schema():
    """Create a new database with proper NOT NULL constraints"""
    
    # Connect to existing database
    source_conn = sqlite3.connect('/Users/kobby/AndroidStudioProjects/ClinicalAide/app/src/main/assets/databases/stg_rag.db')
    source_cursor = source_conn.cursor()
    
    # Create new database with fixed schema
    dest_conn = sqlite3.connect('stg_rag_complete_fixed.db')
    dest_cursor = dest_conn.cursor()
    
    logger.info("Creating new database with fixed schema...")
    
    # Drop existing tables if any
    dest_cursor.executescript("""
        DROP TABLE IF EXISTS chapters;
        DROP TABLE IF EXISTS sections;
        DROP TABLE IF EXISTS conditions_enhanced;
        DROP TABLE IF EXISTS medications_enhanced;
        DROP TABLE IF EXISTS content_chunks;
        DROP TABLE IF EXISTS embeddings;
    """)
    
    # Create tables with proper NOT NULL constraints
    dest_cursor.executescript("""
        CREATE TABLE chapters (
            id INTEGER PRIMARY KEY NOT NULL,
            number INTEGER NOT NULL,
            title TEXT NOT NULL,
            start_page INTEGER NOT NULL
        );
        
        CREATE TABLE sections (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            chapter_number INTEGER NOT NULL,
            section_number TEXT NOT NULL,
            title TEXT NOT NULL,
            page_number INTEGER NOT NULL,
            FOREIGN KEY (chapter_number) REFERENCES chapters(number)
        );
        
        CREATE TABLE conditions_enhanced (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            chapter_number INTEGER,
            section_number TEXT,
            page_number INTEGER NOT NULL,
            clinical_features TEXT,
            investigations TEXT,
            treatment TEXT,
            reference_citation TEXT,
            ocr_source INTEGER NOT NULL DEFAULT 1
        );
        
        CREATE TABLE medications_enhanced (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            generic_name TEXT NOT NULL,
            chapter_number INTEGER,
            section_number TEXT,
            page_number INTEGER NOT NULL,
            strength TEXT,
            route TEXT,
            dosage_info TEXT,
            reference_citation TEXT,
            ocr_source INTEGER NOT NULL DEFAULT 1
        );
        
        CREATE TABLE content_chunks (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            content TEXT NOT NULL,
            chunk_type TEXT NOT NULL,
            source_id INTEGER,
            chapter_number INTEGER,
            chapter_title TEXT,
            section_number TEXT,
            page_number INTEGER NOT NULL,
            condition_name TEXT,
            reference_citation TEXT NOT NULL,
            metadata TEXT,
            embedding BLOB,
            created_at TEXT
        );
        
        CREATE TABLE embeddings (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            chunk_id INTEGER NOT NULL,
            embedding BLOB NOT NULL,
            model_name TEXT DEFAULT 'universal-sentence-encoder',
            dimension INTEGER DEFAULT 512,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (chunk_id) REFERENCES content_chunks(id)
        );
    """)
    
    logger.info("Copying data from existing database...")
    
    # Copy chapters
    source_cursor.execute("SELECT * FROM chapters")
    chapters = source_cursor.fetchall()
    for chapter in chapters:
        dest_cursor.execute("INSERT INTO chapters VALUES (?, ?, ?, ?)", chapter)
    logger.info(f"Copied {len(chapters)} chapters")
    
    # Copy sections
    source_cursor.execute("SELECT * FROM sections")
    sections = source_cursor.fetchall()
    for section in sections:
        dest_cursor.execute("INSERT INTO sections VALUES (?, ?, ?, ?, ?)", section)
    logger.info(f"Copied {len(sections)} sections")
    
    # Copy conditions_enhanced
    source_cursor.execute("SELECT * FROM conditions_enhanced")
    conditions = source_cursor.fetchall()
    for condition in conditions:
        dest_cursor.execute("""
            INSERT INTO conditions_enhanced 
            (id, name, chapter_number, section_number, page_number, 
             clinical_features, investigations, treatment, reference_citation, ocr_source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, condition)
    logger.info(f"Copied {len(conditions)} conditions")
    
    # Copy medications_enhanced
    source_cursor.execute("SELECT * FROM medications_enhanced")
    medications = source_cursor.fetchall()
    for medication in medications:
        dest_cursor.execute("""
            INSERT INTO medications_enhanced 
            (id, generic_name, chapter_number, section_number, page_number, 
             strength, route, dosage_info, reference_citation, ocr_source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, medication)
    logger.info(f"Copied {len(medications)} medications")
    
    # Copy content_chunks
    source_cursor.execute("SELECT * FROM content_chunks")
    chunks = source_cursor.fetchall()
    for chunk in chunks:
        dest_cursor.execute("""
            INSERT INTO content_chunks 
            (id, content, chunk_type, source_id, chapter_number, chapter_title, 
             section_number, page_number, condition_name, reference_citation, 
             metadata, embedding, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, chunk)
    logger.info(f"Copied {len(chunks)} content chunks")
    
    # Check if embeddings table exists and has data
    try:
        source_cursor.execute("SELECT * FROM embeddings")
        embeddings = source_cursor.fetchall()
        for embedding in embeddings:
            dest_cursor.execute("""
                INSERT INTO embeddings 
                (id, chunk_id, embedding, model_name, dimension, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """, embedding)
        logger.info(f"Copied {len(embeddings)} embeddings")
    except sqlite3.OperationalError:
        logger.info("No embeddings table found or no data to copy")
    
    # Commit and close
    dest_conn.commit()
    
    # Verify the data
    dest_cursor.execute("SELECT COUNT(*) FROM chapters")
    chapter_count = dest_cursor.fetchone()[0]
    dest_cursor.execute("SELECT COUNT(*) FROM content_chunks")
    chunk_count = dest_cursor.fetchone()[0]
    dest_cursor.execute("SELECT COUNT(*) FROM conditions_enhanced")
    condition_count = dest_cursor.fetchone()[0]
    dest_cursor.execute("SELECT COUNT(*) FROM medications_enhanced")
    medication_count = dest_cursor.fetchone()[0]
    
    logger.info("\n=== Database Statistics ===")
    logger.info(f"Chapters: {chapter_count}")
    logger.info(f"Content Chunks: {chunk_count}")
    logger.info(f"Conditions: {condition_count}")
    logger.info(f"Medications: {medication_count}")
    
    # Verify schema
    dest_cursor.execute("PRAGMA table_info(chapters)")
    schema_info = dest_cursor.fetchall()
    logger.info("\n=== Chapters Table Schema ===")
    for col in schema_info:
        logger.info(f"Column: {col[1]}, Type: {col[2]}, NotNull: {col[3]}, PK: {col[5]}")
    
    source_conn.close()
    dest_conn.close()
    
    logger.info("\nDatabase fixed and saved as stg_rag_complete_fixed.db")
    
if __name__ == "__main__":
    fix_database_schema()