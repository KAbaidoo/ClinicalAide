#!/usr/bin/env python3
"""
Direct database population with correct schema
"""

import json
import sqlite3
import re
from pathlib import Path
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class DatabasePopulator:
    def __init__(self, db_path="stg_rag.db"):
        self.db_path = db_path
        self.conn = None
        self.cursor = None
        self.current_chapter_id = None
        self.current_section_id = None
        self.section_hierarchy = {}  # Track section hierarchy
        
        # Known chapter mappings based on document structure
        self.chapter_pages = {
            29: ('Chapter 1', 'Disorders of the Gastrointestinal Tract'),
            51: ('Chapter 2', 'Disorders of the Liver'),
            76: ('Chapter 3', 'Nutritional Disorders'),
            80: ('Chapter 4', 'Haematological Disorders'),
            98: ('Chapter 5', 'Immunisable Diseases'),
            115: ('Chapter 6', 'Problems of the Newborn'),
            131: ('Chapter 7', 'Cardiovascular System'),
            185: ('Chapter 8', 'Respiratory System'),
            211: ('Chapter 9', 'Disorders of the Central Nervous System'),
            225: ('Chapter 10', 'Psychiatric Disorders'),
            263: ('Chapter 11', 'Disorders of the Skin'),
            305: ('Chapter 12', 'Endocrine and Metabolic Disorders'),
            332: ('Chapter 13', 'Obstetric Care and Obstetric Disorders'),
            376: ('Chapter 14', 'Gynaecological Disorders'),
            406: ('Chapter 15', 'Disorders of the Kidney and Genitourinary System'),
            460: ('Chapter 16', 'Sexually Transmitted Infections'),
            479: ('Chapter 17', 'HIV Infections and AIDS'),
            486: ('Chapter 18', 'Infectious Diseases and Infestations'),
            521: ('Chapter 19', 'Eye Disorders'),
            533: ('Chapter 20', 'Ear, Nose and Throat Disorders'),
            552: ('Chapter 21', 'Oral and Dental Conditions'),
            570: ('Chapter 22', 'Disorders Of The Musculoskeletal System'),
            600: ('Chapter 23', 'Trauma And Injuries'),
        }
        
    def connect(self):
        """Connect to database"""
        self.conn = sqlite3.connect(self.db_path)
        self.cursor = self.conn.cursor()
        logger.info(f"Connected to database: {self.db_path}")
        
    def disconnect(self):
        """Disconnect from database"""
        if self.conn:
            self.conn.close()
            logger.info("Disconnected from database")
            
    def create_tables(self):
        """Create database schema according to user specification"""
        schema = """
        -- Table to store document chapters
        CREATE TABLE chapters (
            chapter_id INTEGER NOT NULL PRIMARY KEY,
            chapter_number TEXT NOT NULL,
            chapter_title TEXT NOT NULL
        );

        -- Table to store sections within chapters
        CREATE TABLE sections (
            section_id INTEGER NOT NULL PRIMARY KEY,
            chapter_id INTEGER NOT NULL,
            section_number TEXT,
            section_title TEXT NOT NULL,
            parent_section_id INTEGER,
            FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE CASCADE,
            FOREIGN KEY (parent_section_id) REFERENCES sections(section_id) ON DELETE CASCADE
        );

        -- Table to store treatment content
        CREATE TABLE content (
            content_id INTEGER NOT NULL PRIMARY KEY,
            section_id INTEGER NOT NULL,
            page_number INTEGER NOT NULL,
            content_text TEXT NOT NULL,
            content_type TEXT NOT NULL,
            FOREIGN KEY (section_id) REFERENCES sections(section_id) ON DELETE CASCADE
        );

        -- Table to store embeddings for semantic search
        CREATE TABLE embeddings (
            embedding_id INTEGER NOT NULL PRIMARY KEY,
            content_id INTEGER NOT NULL,
            embedding BLOB NOT NULL,
            FOREIGN KEY (content_id) REFERENCES content(content_id) ON DELETE CASCADE
        );

        -- Table to store metadata
        CREATE TABLE metadata (
            metadata_id INTEGER NOT NULL PRIMARY KEY,
            content_id INTEGER NOT NULL,
            key TEXT NOT NULL,
            value TEXT NOT NULL,
            FOREIGN KEY (content_id) REFERENCES content(content_id) ON DELETE CASCADE
        );
        
        -- Note: Indices will be created by Room automatically to match entity definitions
        -- Removing manual index creation to avoid conflicts with Room's auto-generated indices
        """
        
        for statement in schema.split(';'):
            if statement.strip():
                self.cursor.execute(statement)
        self.conn.commit()
        logger.info("Database tables created successfully")
        
    def clear_database(self):
        """Clear all existing data"""
        tables = ['metadata', 'embeddings', 'content', 'sections', 'chapters']
        for table in tables:
            try:
                self.cursor.execute(f"DELETE FROM {table}")
            except:
                pass
        self.conn.commit()
        logger.info("Database cleared")
        
    def detect_chapter(self, text, page_num):
        """Detect if text contains chapter header"""
        patterns = [
            re.compile(r'^(?:CHAPTER|Chapter)\s+(\d+)[:\s]*(.+)?', re.IGNORECASE | re.MULTILINE),
            re.compile(r'^Chapter\s+(\d+):\s*(.+)', re.MULTILINE),
            # Also detect chapters that might be in headers
            re.compile(r'Chapter\s+(\d+):\s*(.+?)(?:\n|$)', re.IGNORECASE)
        ]
        
        for pattern in patterns:
            match = pattern.search(text)
            if match:
                chapter_num = match.group(1)
                title = match.group(2).strip() if match.group(2) else f"Chapter {chapter_num}"
                # Clean the title
                title = title.replace('\n', ' ').strip()
                return {
                    'number': f"Chapter {chapter_num}",
                    'title': title,
                    'page': page_num
                }
        return None
        
    def detect_section(self, text):
        """Detect main section headers (e.g., '1. Section Title')"""
        pattern = re.compile(r'^(\d+)\.\s+(.+?)(?:\n|$)', re.MULTILINE)
        matches = pattern.findall(text)
        sections = []
        for match in matches:
            sections.append({
                'number': match[0],
                'title': match[1].strip()
            })
        return sections if sections else None
        
    def detect_subsection(self, text):
        """Detect subsection headers (e.g., 'A. Subsection Title')"""
        pattern = re.compile(r'^([A-Z])\.\s+(.+?)(?:\n|$)', re.MULTILINE)
        matches = pattern.findall(text)
        subsections = []
        for match in matches:
            subsections.append({
                'letter': match[0],
                'title': match[1].strip()
            })
        return subsections if subsections else None
        
    def determine_content_type(self, text):
        """Determine the type of content"""
        # Check if it's a table (ASCII formatted)
        if '+--' in text or '|' in text and '-+-' in text:
            return 'table'
        
        # Check if it's a note
        if text.strip().startswith('Note ') and re.match(r'^Note\s+\d+-\d+', text.strip()):
            return 'note'
        
        # Check for bullet points
        if text.strip().startswith('•') or text.strip().startswith('-'):
            return 'bullet'
            
        # Default to paragraph
        return 'paragraph'
        
    def extract_metadata(self, text):
        """Extract metadata from content"""
        metadata = []
        
        # Check for target population
        if 'children' in text.lower():
            metadata.append(('target_population', 'children'))
        elif 'adult' in text.lower():
            metadata.append(('target_population', 'adults'))
        elif 'pregnant' in text.lower():
            metadata.append(('target_population', 'pregnant_women'))
            
        # Check for severity
        if 'severe' in text.lower():
            metadata.append(('severity', 'severe'))
        elif 'moderate' in text.lower():
            metadata.append(('severity', 'moderate'))
        elif 'mild' in text.lower():
            metadata.append(('severity', 'mild'))
            
        # Check for treatment type
        if 'pharmacological' in text.lower():
            metadata.append(('treatment_type', 'pharmacological'))
        elif 'non-pharmacological' in text.lower():
            metadata.append(('treatment_type', 'non-pharmacological'))
            
        return metadata
        
    def process_page(self, page_num, page_data):
        """Process a single page"""
        text = page_data.get('full_text', page_data.get('text', ''))
        if not text.strip():
            return
            
        # Check for known chapter starts
        if page_num in self.chapter_pages:
            chapter_num, chapter_title = self.chapter_pages[page_num]
            # Check if chapter already exists
            self.cursor.execute(
                "SELECT chapter_id FROM chapters WHERE chapter_number = ?",
                (chapter_num,)
            )
            existing = self.cursor.fetchone()
            
            if not existing:
                self.cursor.execute("""
                    INSERT INTO chapters (chapter_number, chapter_title)
                    VALUES (?, ?)
                """, (chapter_num, chapter_title))
                self.current_chapter_id = self.cursor.lastrowid
                self.section_hierarchy = {}  # Reset hierarchy for new chapter
                logger.info(f"Inserted {chapter_num}: {chapter_title}")
            else:
                self.current_chapter_id = existing[0]
            
        # Also check for explicit chapter markers
        chapter = self.detect_chapter(text, page_num)
        if chapter:
            # Check if chapter already exists
            self.cursor.execute(
                "SELECT chapter_id FROM chapters WHERE chapter_number = ?",
                (chapter['number'],)
            )
            existing = self.cursor.fetchone()
            
            if not existing:
                self.cursor.execute("""
                    INSERT INTO chapters (chapter_number, chapter_title)
                    VALUES (?, ?)
                """, (chapter['number'], chapter['title']))
                self.current_chapter_id = self.cursor.lastrowid
                self.section_hierarchy = {}  # Reset hierarchy for new chapter
                logger.info(f"Inserted {chapter['number']}: {chapter['title']}")
            else:
                self.current_chapter_id = existing[0]
            
        # Only process sections if we have a current chapter
        if not self.current_chapter_id:
            return
            
        # Check for main sections
        sections = self.detect_section(text)
        if sections:
            for section in sections:
                section_key = f"{section['number']}"
                
                # Check if section already exists
                self.cursor.execute(
                    "SELECT section_id FROM sections WHERE chapter_id = ? AND section_number = ?",
                    (self.current_chapter_id, section['number'])
                )
                existing = self.cursor.fetchone()
                
                if not existing:
                    self.cursor.execute("""
                        INSERT INTO sections (chapter_id, section_number, section_title, parent_section_id)
                        VALUES (?, ?, ?, NULL)
                    """, (self.current_chapter_id, section['number'], section['title']))
                    section_id = self.cursor.lastrowid
                    self.section_hierarchy[section_key] = section_id
                    self.current_section_id = section_id
                else:
                    section_id = existing[0]
                    self.section_hierarchy[section_key] = section_id
                    self.current_section_id = section_id
            
        # Check for subsections
        subsections = self.detect_subsection(text)
        if subsections and self.current_section_id:
            parent_id = self.current_section_id
            for subsection in subsections:
                # Create a unique identifier for the subsection
                subsection_key = f"{self.current_section_id}_{subsection['letter']}"
                
                # Check if subsection already exists
                self.cursor.execute(
                    "SELECT section_id FROM sections WHERE parent_section_id = ? AND section_title = ?",
                    (parent_id, subsection['title'])
                )
                existing = self.cursor.fetchone()
                
                if not existing:
                    self.cursor.execute("""
                        INSERT INTO sections (chapter_id, section_number, section_title, parent_section_id)
                        VALUES (?, ?, ?, ?)
                    """, (self.current_chapter_id, subsection['letter'], subsection['title'], parent_id))
                    subsection_id = self.cursor.lastrowid
                    self.section_hierarchy[subsection_key] = subsection_id
                else:
                    subsection_id = existing[0]
                    self.section_hierarchy[subsection_key] = subsection_id
            
        # Store content if we have a section
        if self.current_section_id and text.strip():
            # Split text into logical chunks (paragraphs, bullets, etc.)
            chunks = text.split('\n\n')
            
            for chunk in chunks:
                chunk = chunk.strip()
                if not chunk:
                    continue
                    
                content_type = self.determine_content_type(chunk)
                
                # Insert content
                self.cursor.execute("""
                    INSERT INTO content (section_id, page_number, content_text, content_type)
                    VALUES (?, ?, ?, ?)
                """, (self.current_section_id, page_num, chunk, content_type))
                
                content_id = self.cursor.lastrowid
                
                # Extract and store metadata
                metadata = self.extract_metadata(chunk)
                for key, value in metadata:
                    self.cursor.execute("""
                        INSERT INTO metadata (content_id, key, value)
                        VALUES (?, ?, ?)
                    """, (content_id, key, value))
                    
        # Handle tables from page_data if available
        if 'tables' in page_data and page_data['tables']:
            for table in page_data['tables']:
                if self.current_section_id:
                    table_text = table.get('ascii', '') if isinstance(table, dict) else str(table)
                    if table_text:
                        self.cursor.execute("""
                            INSERT INTO content (section_id, page_number, content_text, content_type)
                            VALUES (?, ?, ?, 'table')
                        """, (self.current_section_id, page_num, table_text))
                
    def populate_from_extracted(self, input_file="pymupdf_output/extracted_content.json"):
        """Populate database from extracted content"""
        logger.info(f"Loading extracted content from {input_file}")
        
        with open(input_file, 'r') as f:
            data = json.load(f)
            
        # Sort pages by number
        sorted_pages = sorted(data.items(), key=lambda x: int(x[0]))
        total_pages = len(sorted_pages)
        
        logger.info(f"Processing {total_pages} pages...")
        
        # Process each page in order
        for page_str, page_data in sorted_pages:
            try:
                page_num = int(page_str)
                if page_num % 50 == 0:
                    logger.info(f"Processing page {page_num}/{sorted_pages[-1][0]}...")
                    self.conn.commit()  # Commit periodically
                    
                self.process_page(page_num, page_data)
                
            except Exception as e:
                logger.error(f"Error processing page {page_str}: {e}")
                continue
                
        self.conn.commit()
        
        # Get statistics
        stats = self.get_statistics()
        return stats
        
    def get_statistics(self):
        """Get database statistics"""
        stats = {}
        tables = ['chapters', 'sections', 'content', 'metadata', 'embeddings']
        
        for table in tables:
            self.cursor.execute(f"SELECT COUNT(*) FROM {table}")
            stats[table] = self.cursor.fetchone()[0]
            
        # Get some sample data
        self.cursor.execute("SELECT chapter_number, chapter_title FROM chapters ORDER BY chapter_id LIMIT 5")
        stats['sample_chapters'] = self.cursor.fetchall()
        
        self.cursor.execute("""
            SELECT s.section_number, s.section_title 
            FROM sections s 
            WHERE s.parent_section_id IS NULL 
            ORDER BY s.section_id LIMIT 5
        """)
        stats['sample_sections'] = self.cursor.fetchall()
        
        return stats
        
    def run(self):
        """Main execution"""
        try:
            self.connect()
            self.create_tables()
            self.clear_database()
            
            stats = self.populate_from_extracted()
            
            print("\nDatabase population complete:")
            print(f"  - Chapters: {stats['chapters']}")
            print(f"  - Sections: {stats['sections']}")
            print(f"  - Content entries: {stats['content']}")
            print(f"  - Metadata entries: {stats['metadata']}")
            print(f"  - Embeddings: {stats['embeddings']}")
            print(f"  - Database: {self.db_path}")
            
            if stats['sample_chapters']:
                print("\nSample chapters:")
                for num, title in stats['sample_chapters'][:3]:
                    print(f"    {num}: {title}")
                    
            if stats['sample_sections']:
                print("\nSample sections:")
                for num, title in stats['sample_sections'][:3]:
                    if num:
                        print(f"    {num}. {title}")
                    else:
                        print(f"    {title}")
            
            # Play completion sound
            import subprocess
            subprocess.run(['afplay', '/System/Library/Sounds/Glass.aiff'], check=False)
            
        finally:
            self.disconnect()


if __name__ == "__main__":
    populator = DatabasePopulator()
    populator.run()