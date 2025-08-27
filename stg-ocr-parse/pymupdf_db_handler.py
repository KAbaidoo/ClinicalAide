import sqlite3
import json
from pathlib import Path
from typing import Dict, List, Optional
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class PyMuPDFDatabaseHandler:
    def __init__(self, db_path: str = 'stg_pymupdf.db'):
        """
        Initialize database handler for PyMuPDF extracted data
        
        Args:
            db_path: Path to SQLite database file
        """
        self.db_path = db_path
        self.conn = None
        self.cursor = None
        
    def connect(self):
        """Connect to the database"""
        self.conn = sqlite3.connect(self.db_path)
        self.cursor = self.conn.cursor()
        logger.info(f"Connected to database: {self.db_path}")
    
    def disconnect(self):
        """Disconnect from the database"""
        if self.conn:
            self.conn.close()
            logger.info("Disconnected from database")
    
    def create_tables(self):
        """Create database tables based on provided schema"""
        schema = """
        -- Table to store document chapters
        CREATE TABLE IF NOT EXISTS chapters (
            chapter_id INTEGER PRIMARY KEY AUTOINCREMENT,
            chapter_number TEXT NOT NULL,
            chapter_title TEXT NOT NULL,
            UNIQUE(chapter_number)
        );

        -- Table to store sections within chapters
        CREATE TABLE IF NOT EXISTS sections (
            section_id INTEGER PRIMARY KEY AUTOINCREMENT,
            chapter_id INTEGER NOT NULL,
            section_number TEXT,
            section_title TEXT NOT NULL,
            parent_section_id INTEGER,
            FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id),
            FOREIGN KEY (parent_section_id) REFERENCES sections(section_id)
        );

        -- Table to store treatment content
        CREATE TABLE IF NOT EXISTS content (
            content_id INTEGER PRIMARY KEY AUTOINCREMENT,
            section_id INTEGER NOT NULL,
            page_number INTEGER NOT NULL,
            content_text TEXT NOT NULL,
            content_type TEXT NOT NULL,
            FOREIGN KEY (section_id) REFERENCES sections(section_id)
        );

        -- Table to store embeddings for semantic search
        CREATE TABLE IF NOT EXISTS embeddings (
            embedding_id INTEGER PRIMARY KEY AUTOINCREMENT,
            content_id INTEGER NOT NULL,
            embedding BLOB NOT NULL,
            FOREIGN KEY (content_id) REFERENCES content(content_id)
        );

        -- Table to store metadata
        CREATE TABLE IF NOT EXISTS metadata (
            metadata_id INTEGER PRIMARY KEY AUTOINCREMENT,
            content_id INTEGER NOT NULL,
            key TEXT NOT NULL,
            value TEXT NOT NULL,
            FOREIGN KEY (content_id) REFERENCES content(content_id)
        );
        
        -- Additional tables for medications and tables
        CREATE TABLE IF NOT EXISTS medications (
            medication_id INTEGER PRIMARY KEY AUTOINCREMENT,
            section_id INTEGER,
            page_number INTEGER NOT NULL,
            name TEXT NOT NULL,
            route TEXT,
            dose TEXT,
            frequency TEXT,
            FOREIGN KEY (section_id) REFERENCES sections(section_id)
        );
        
        CREATE TABLE IF NOT EXISTS document_tables (
            table_id INTEGER PRIMARY KEY AUTOINCREMENT,
            page_number INTEGER NOT NULL,
            table_type TEXT,
            headers TEXT,
            data TEXT
        );
        """
        
        self.cursor.executescript(schema)
        self.conn.commit()
        logger.info("Database tables created successfully")
    
    def clear_database(self):
        """Clear all data from tables"""
        tables = ['medications', 'document_tables', 'metadata', 'embeddings', 'content', 'sections', 'chapters']
        
        for table in tables:
            self.cursor.execute(f"DELETE FROM {table}")
        
        self.conn.commit()
        logger.info("Database cleared")
    
    def insert_chapter(self, chapter_number: str, chapter_title: str) -> int:
        """
        Insert or get chapter
        
        Args:
            chapter_number: Chapter number
            chapter_title: Chapter title
            
        Returns:
            Chapter ID
        """
        # Try to get existing chapter
        self.cursor.execute(
            "SELECT chapter_id FROM chapters WHERE chapter_number = ?",
            (chapter_number,)
        )
        result = self.cursor.fetchone()
        
        if result:
            return result[0]
        
        # Insert new chapter
        try:
            self.cursor.execute(
                "INSERT INTO chapters (chapter_number, chapter_title) VALUES (?, ?)",
                (chapter_number, chapter_title)
            )
            self.conn.commit()
            logger.info(f"Inserted chapter {chapter_number}: {chapter_title[:50]}")
            return self.cursor.lastrowid
        except sqlite3.IntegrityError:
            # Handle race condition
            self.cursor.execute(
                "SELECT chapter_id FROM chapters WHERE chapter_number = ?",
                (chapter_number,)
            )
            return self.cursor.fetchone()[0]
    
    def insert_section(self, chapter_id: int, section_number: Optional[str],
                      section_title: str, parent_section_id: Optional[int] = None) -> int:
        """
        Insert section
        
        Args:
            chapter_id: Parent chapter ID
            section_number: Section number
            section_title: Section title
            parent_section_id: Parent section ID for subsections
            
        Returns:
            Section ID
        """
        self.cursor.execute(
            """INSERT INTO sections (chapter_id, section_number, section_title, parent_section_id)
               VALUES (?, ?, ?, ?)""",
            (chapter_id, section_number, section_title, parent_section_id)
        )
        self.conn.commit()
        return self.cursor.lastrowid
    
    def insert_content(self, section_id: int, page_number: int,
                      content_text: str, content_type: str) -> int:
        """
        Insert content entry
        
        Args:
            section_id: Parent section ID
            page_number: Page number
            content_text: Content text
            content_type: Content type
            
        Returns:
            Content ID
        """
        self.cursor.execute(
            """INSERT INTO content (section_id, page_number, content_text, content_type)
               VALUES (?, ?, ?, ?)""",
            (section_id, page_number, content_text, content_type)
        )
        self.conn.commit()
        return self.cursor.lastrowid
    
    def insert_medication(self, section_id: Optional[int], page_number: int,
                         name: str, route: Optional[str] = None,
                         dose: Optional[str] = None, frequency: Optional[str] = None):
        """
        Insert medication entry
        
        Args:
            section_id: Related section ID
            page_number: Page number
            name: Medication name
            route: Administration route
            dose: Dosage
            frequency: Frequency
        """
        self.cursor.execute(
            """INSERT INTO medications (section_id, page_number, name, route, dose, frequency)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (section_id, page_number, name, route, dose, frequency)
        )
        self.conn.commit()
    
    def insert_table(self, page_number: int, table_type: str,
                    headers: List[str], data: List[List[str]]):
        """
        Insert table data
        
        Args:
            page_number: Page number
            table_type: Type of table
            headers: Table headers
            data: Table data
        """
        self.cursor.execute(
            """INSERT INTO document_tables (page_number, table_type, headers, data)
               VALUES (?, ?, ?, ?)""",
            (page_number, table_type, json.dumps(headers), json.dumps(data))
        )
        self.conn.commit()
    
    def process_parsed_data(self, parsed_data: Dict):
        """
        Process parsed PyMuPDF data and populate database
        
        Args:
            parsed_data: Parsed data from enhanced_parser
        """
        # Track current chapter across pages
        current_chapter_id = None
        current_chapter_number = None
        
        for page_num in sorted(parsed_data.keys(), key=lambda x: int(x) if x.isdigit() else 0):
            page_data = parsed_data[page_num]
            page_number = int(page_num) if page_num.isdigit() else page_num
            
            logger.info(f"Processing page {page_number}...")
            
            # Handle chapter
            if page_data.get('chapter'):
                chapter = page_data['chapter']
                current_chapter_number = chapter['number']
                # Get full title from first occurrence
                if not chapter.get('title'):
                    chapter['title'] = 'Disorders of the Gastrointestinal Tract'
                current_chapter_id = self.insert_chapter(
                    chapter['number'],
                    chapter['title']
                )
            
            # Ensure we have a chapter for content
            if not current_chapter_id:
                # Create default chapter for initial content
                current_chapter_id = self.insert_chapter('1', 'Disorders of the Gastrointestinal Tract')
            
            # Handle sections
            for section in page_data.get('sections', []):
                section_id = self.insert_section(
                    current_chapter_id,
                    section.get('number'),
                    section['title']
                )
                
                # Insert section content
                if section.get('content'):
                    content_id = self.insert_content(
                        section_id,
                        page_number,
                        section['content'],
                        'section_content'
                    )
                
                # Handle subsections
                for subsection in section.get('subsections', []):
                    subsection_id = self.insert_section(
                        current_chapter_id,
                        None,
                        subsection['title'],
                        section_id
                    )
                    
                    if subsection.get('content'):
                        self.insert_content(
                            subsection_id,
                            page_number,
                            subsection['content'],
                            subsection['type']
                        )
            
            # Handle content blocks
            if not page_data.get('sections') and page_data.get('content_blocks'):
                # Create a page-level section
                page_section_id = self.insert_section(
                    current_chapter_id,
                    None,
                    f"Page {page_number} Content"
                )
                
                for block in page_data['content_blocks']:
                    self.insert_content(
                        page_section_id,
                        page_number,
                        block['content'],
                        block['type']
                    )
            
            # Handle medications
            for med in page_data.get('medications', []):
                # Find appropriate section
                section_id = None
                if page_data.get('sections'):
                    # Use first section on page
                    section_id = self.cursor.lastrowid
                
                self.insert_medication(
                    section_id,
                    page_number,
                    med['name'],
                    med.get('route'),
                    med.get('dose'),
                    med.get('frequency')
                )
            
            # Handle tables
            for table in page_data.get('tables', []):
                self.insert_table(
                    page_number,
                    table.get('type', 'general'),
                    table.get('headers', []),
                    table.get('data', [])
                )
    
    def get_statistics(self) -> Dict:
        """
        Get database statistics
        
        Returns:
            Statistics dictionary
        """
        stats = {}
        
        # Basic counts
        tables = ['chapters', 'sections', 'content', 'medications', 'document_tables']
        for table in tables:
            self.cursor.execute(f"SELECT COUNT(*) FROM {table}")
            stats[f'total_{table}'] = self.cursor.fetchone()[0]
        
        # Content by type
        self.cursor.execute(
            "SELECT content_type, COUNT(*) FROM content GROUP BY content_type"
        )
        stats['content_by_type'] = dict(self.cursor.fetchall())
        
        # Sample data
        self.cursor.execute(
            "SELECT chapter_number, chapter_title FROM chapters LIMIT 5"
        )
        stats['sample_chapters'] = self.cursor.fetchall()
        
        self.cursor.execute(
            """SELECT s.section_number, s.section_title, c.chapter_number
               FROM sections s
               JOIN chapters c ON s.chapter_id = c.chapter_id
               WHERE s.section_number IS NOT NULL
               LIMIT 10"""
        )
        stats['sample_sections'] = self.cursor.fetchall()
        
        self.cursor.execute(
            "SELECT name, route, dose FROM medications LIMIT 10"
        )
        stats['sample_medications'] = self.cursor.fetchall()
        
        return stats


def main():
    """Main function to populate database with PyMuPDF parsed data"""
    
    # Load parsed data
    with open('pymupdf_output/parsed_structured_data.json', 'r', encoding='utf-8') as f:
        parsed_data = json.load(f)
    
    # Initialize database handler
    db = PyMuPDFDatabaseHandler('stg_pymupdf.db')
    
    try:
        # Connect and create tables
        db.connect()
        db.create_tables()
        
        # Clear existing data for clean import
        db.clear_database()
        
        # Process parsed data
        db.process_parsed_data(parsed_data)
        
        # Get and display statistics
        stats = db.get_statistics()
        
        # Print concise summary
        print(f"\nDatabase population complete:")
        print(f"  - Chapters: {stats['total_chapters']}")
        print(f"  - Sections: {stats['total_sections']}")
        print(f"  - Content entries: {stats['total_content']}")
        print(f"  - Medications: {stats['total_medications']}")
        print(f"  - Tables: {stats['total_document_tables']}")
        print(f"  - Database: stg_pymupdf.db")
        
    finally:
        db.disconnect()


if __name__ == "__main__":
    main()