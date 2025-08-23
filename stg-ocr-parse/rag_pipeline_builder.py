#!/usr/bin/env python3
"""
RAG Pipeline Builder for Ghana STG Medical Chatbot
Combines OCR data with chapter structure and prepares for embeddings
"""

import sqlite3
import json
import re
import logging
from typing import List, Dict, Tuple, Optional
from dataclasses import dataclass, asdict
import fitz  # PyMuPDF

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class ContentChunk:
    """Represents a chunk of content for embedding"""
    id: int
    content: str
    chapter_number: int
    chapter_title: str
    section_number: str
    page_number: int
    condition_name: str
    content_type: str  # 'treatment', 'clinical_features', 'diagnosis', 'medication'
    reference: str
    metadata: Dict

class RAGPipelineBuilder:
    def __init__(self, pdf_path: str = "GHANA-STG-2017-1.pdf"):
        self.pdf_path = pdf_path
        self.chapters = []
        self.chunks = []
        self.chunk_size = 500  # Target chunk size in words
        
    def build_complete_database(self):
        """Build the complete RAG-ready database"""
        logger.info("Building complete RAG database...")
        
        # Step 1: Extract chapter structure
        self._extract_chapters()
        
        # Step 2: Merge OCR data with references
        self._merge_ocr_with_chapters()
        
        # Step 3: Create content chunks
        self._create_content_chunks()
        
        # Step 4: Create new database
        self._create_rag_database()
        
        # Step 5: Generate statistics
        self._print_statistics()
        
    def _extract_chapters(self):
        """Extract complete chapter and section information"""
        logger.info("Extracting chapter structure from PDF...")
        
        doc = fitz.open(self.pdf_path)
        
        # Extract TOC from pages 3-9
        for page_num in range(2, 9):
            page = doc[page_num]
            text = page.get_text()
            lines = text.split('\n')
            
            for line in lines:
                # Extract chapters
                chapter_match = re.match(r'Chapter\s+(\d+)\.\s+(.+?)\.+\s*(\d+)', line)
                if chapter_match:
                    chapter = {
                        'number': int(chapter_match.group(1)),
                        'title': chapter_match.group(2).strip(),
                        'start_page': int(chapter_match.group(3)),
                        'sections': []
                    }
                    self.chapters.append(chapter)
                
                # Extract numbered sections (e.g., "186. Malaria")
                section_match = re.match(r'(\d{1,3})\.\s+([^.]+?)\.+\s*(\d+)', line)
                if section_match and int(section_match.group(1)) > 100:
                    section = {
                        'number': section_match.group(1),
                        'title': section_match.group(2).strip(),
                        'page': int(section_match.group(3))
                    }
                    # Find which chapter this belongs to
                    for chapter in self.chapters:
                        if chapter['start_page'] <= section['page']:
                            chapter['sections'].append(section)
                            break
        
        doc.close()
        logger.info(f"Extracted {len(self.chapters)} chapters")
        
    def _merge_ocr_with_chapters(self):
        """Merge OCR-extracted conditions with chapter references"""
        logger.info("Merging OCR data with chapter structure...")
        
        # Connect to OCR database
        ocr_conn = sqlite3.connect("stg_medical_complete.db")
        ocr_cursor = ocr_conn.cursor()
        
        # Create reference database
        ref_conn = sqlite3.connect("stg_rag_complete.db")
        ref_cursor = ref_conn.cursor()
        
        # Create enhanced schema
        ref_cursor.executescript("""
        DROP TABLE IF EXISTS chapters;
        DROP TABLE IF EXISTS sections;
        DROP TABLE IF EXISTS conditions_enhanced;
        DROP TABLE IF EXISTS medications_enhanced;
        DROP TABLE IF EXISTS content_chunks;
        DROP TABLE IF EXISTS embeddings;
        
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
        
        CREATE INDEX idx_chunks_type ON content_chunks(chunk_type);
        CREATE INDEX idx_chunks_condition ON content_chunks(condition_name);
        CREATE INDEX idx_chunks_page ON content_chunks(page_number);
        """)
        
        # Insert chapters
        for chapter in self.chapters:
            ref_cursor.execute("""
                INSERT INTO chapters (number, title, start_page)
                VALUES (?, ?, ?)
            """, (chapter['number'], chapter['title'], chapter['start_page']))
            
            # Insert sections
            for section in chapter.get('sections', []):
                ref_cursor.execute("""
                    INSERT INTO sections (chapter_number, section_number, title, page_number)
                    VALUES (?, ?, ?, ?)
                """, (chapter['number'], section['number'], section['title'], section['page']))
        
        # Merge conditions from OCR database
        ocr_cursor.execute("SELECT id, name, icd10_code, clinical_features, investigations, treatment, page_number FROM conditions")
        for row in ocr_cursor.fetchall():
            # row[6] is page_number
            page_num = int(row[6]) if row[6] else 0
            chapter_num, section_num = self._find_chapter_section(page_num)
            
            reference = f"Chapter {chapter_num}, Page {page_num}"
            if section_num:
                reference = f"Chapter {chapter_num}, Section {section_num}, Page {page_num}"
            
            ref_cursor.execute("""
                INSERT INTO conditions_enhanced 
                (name, chapter_number, section_number, page_number, clinical_features, 
                 investigations, treatment, reference_citation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (row[1], chapter_num, section_num, page_num, 
                  row[3],  # clinical_features
                  row[4],  # investigations 
                  row[5],  # treatment
                  reference))
        
        # Merge medications from OCR database
        ocr_cursor.execute("SELECT id, generic_name, strength, route, page_number FROM medications")
        for row in ocr_cursor.fetchall():
            page_num = int(row[4]) if row[4] else 0
            chapter_num, section_num = self._find_chapter_section(page_num)
            
            reference = f"Chapter {chapter_num}, Page {page_num}"
            
            ref_cursor.execute("""
                INSERT INTO medications_enhanced
                (generic_name, chapter_number, section_number, page_number, 
                 strength, route, reference_citation)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (row[1], chapter_num, section_num, page_num,
                  row[2],  # strength
                  row[3],  # route
                  reference))
        
        ref_conn.commit()
        ocr_conn.close()
        ref_conn.close()
        
        logger.info("Data merge complete")
        
    def _find_chapter_section(self, page_number: int) -> Tuple[Optional[int], Optional[str]]:
        """Find chapter and section for a given page number"""
        chapter_num = None
        section_num = None
        
        for chapter in self.chapters:
            if page_number >= chapter['start_page']:
                chapter_num = chapter['number']
                # Find section
                for section in chapter.get('sections', []):
                    if page_number >= section['page']:
                        section_num = section['number']
        
        return chapter_num, section_num
        
    def _create_content_chunks(self):
        """Create content chunks for embedding"""
        logger.info("Creating content chunks for RAG...")
        
        conn = sqlite3.connect("stg_rag_complete.db")
        cursor = conn.cursor()
        
        # Create chunks from conditions
        cursor.execute("""
            SELECT id, name, chapter_number, section_number, page_number,
                   clinical_features, investigations, treatment, reference_citation
            FROM conditions_enhanced
        """)
        
        for row in cursor.fetchall():
            condition_id, name, chapter_num, section_num, page_num, clinical, investigations, treatment, ref = row
            
            # Get chapter title
            chapter_title = ""
            for chapter in self.chapters:
                if chapter['number'] == chapter_num:
                    chapter_title = chapter['title']
                    break
            
            # Create treatment chunk
            if treatment:
                chunk_content = f"Treatment for {name}: {treatment}"
                cursor.execute("""
                    INSERT INTO content_chunks 
                    (content, chunk_type, source_id, chapter_number, chapter_title,
                     section_number, page_number, condition_name, reference_citation, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (chunk_content[:2000], 'treatment', condition_id, chapter_num, chapter_title,
                      section_num, page_num, name, ref,
                      json.dumps({"condition_id": condition_id, "type": "treatment"})))
            
            # Create clinical features chunk
            if clinical:
                chunk_content = f"Clinical features of {name}: {clinical}"
                cursor.execute("""
                    INSERT INTO content_chunks
                    (content, chunk_type, source_id, chapter_number, chapter_title,
                     section_number, page_number, condition_name, reference_citation, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (chunk_content[:2000], 'clinical_features', condition_id, chapter_num, chapter_title,
                      section_num, page_num, name, ref,
                      json.dumps({"condition_id": condition_id, "type": "clinical_features"})))
            
            # Create investigations chunk
            if investigations:
                chunk_content = f"Investigations for {name}: {investigations}"
                cursor.execute("""
                    INSERT INTO content_chunks
                    (content, chunk_type, source_id, chapter_number, chapter_title,
                     section_number, page_number, condition_name, reference_citation, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (chunk_content[:2000], 'investigations', condition_id, chapter_num, chapter_title,
                      section_num, page_num, name, ref,
                      json.dumps({"condition_id": condition_id, "type": "investigations"})))
        
        # Create chunks from medications
        cursor.execute("""
            SELECT id, generic_name, chapter_number, section_number, page_number,
                   strength, route, dosage_info, reference_citation
            FROM medications_enhanced
        """)
        
        for row in cursor.fetchall():
            med_id, name, chapter_num, section_num, page_num, strength, route, dosage, ref = row
            
            # Get chapter title
            chapter_title = ""
            for chapter in self.chapters:
                if chapter['number'] == chapter_num:
                    chapter_title = chapter['title']
                    break
            
            # Create medication chunk
            chunk_content = f"Medication: {name}"
            if strength:
                chunk_content += f", Strength: {strength}"
            if route:
                chunk_content += f", Route: {route}"
            if dosage:
                chunk_content += f", Dosage: {dosage}"
            
            cursor.execute("""
                INSERT INTO content_chunks
                (content, chunk_type, source_id, chapter_number, chapter_title,
                 section_number, page_number, condition_name, reference_citation, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (chunk_content, 'medication', med_id, chapter_num, chapter_title,
                  section_num, page_num, name, ref,
                  json.dumps({"medication_id": med_id, "type": "medication"})))
        
        conn.commit()
        conn.close()
        
        logger.info("Content chunks created")
        
    def _create_rag_database(self):
        """Finalize the RAG database"""
        logger.info("Finalizing RAG database...")
        
        # Copy to Android project
        import shutil
        shutil.copy("stg_rag_complete.db", 
                   "/Users/kobby/AndroidStudioProjects/ClinicalAide/app/src/main/assets/databases/stg_rag.db")
        
        logger.info("Database copied to Android project")
        
    def _print_statistics(self):
        """Print database statistics"""
        conn = sqlite3.connect("stg_rag_complete.db")
        cursor = conn.cursor()
        
        stats = {}
        tables = ['chapters', 'sections', 'conditions_enhanced', 'medications_enhanced', 'content_chunks']
        
        for table in tables:
            cursor.execute(f"SELECT COUNT(*) FROM {table}")
            stats[table] = cursor.fetchone()[0]
        
        print("\n" + "="*60)
        print("RAG DATABASE STATISTICS")
        print("="*60)
        for table, count in stats.items():
            print(f"{table}: {count:,}")
        
        # Sample query demonstration
        print("\n" + "="*60)
        print("SAMPLE QUERY: Hypertension Treatment")
        print("="*60)
        
        cursor.execute("""
            SELECT content, reference_citation, page_number
            FROM content_chunks
            WHERE condition_name LIKE '%hypertension%' 
               OR content LIKE '%hypertension%'
            LIMIT 3
        """)
        
        for row in cursor.fetchall():
            print(f"\nContent: {row[0][:200]}...")
            print(f"Reference: Ghana STG 2017 - {row[1]}")
            print(f"Page: {row[2]}")
        
        conn.close()

def main():
    builder = RAGPipelineBuilder()
    builder.build_complete_database()

if __name__ == "__main__":
    main()