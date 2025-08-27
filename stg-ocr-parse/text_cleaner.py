import re
from typing import List, Dict, Optional
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class TextCleaner:
    def __init__(self):
        """
        Initialize text cleaner with patterns for common issues in PyMuPDF extraction
        """
        
        # Common headers and footers to remove
        self.headers_footers = [
            r'^Standard Treatment Guidelines, 7th Edition, 2017$',
            r'^\s*Chapter \d+:\s*.+$',  # Generic chapter header pattern for all chapters
            r'^Chapter \d+: Disorders of.*$',  # Full chapter headers like "Chapter 1: Disorders of the Gastrointestinal Tract"
            r'^.*Chapter \d+:.*Gastrointestinal Tract.*$',  # Any line containing chapter and gastrointestinal
            r'^Chapter \d+: [^\n]+$',
            r'^Chapter$',
            r'^\s*Chapter\s*$',
            r'^Disorders of the$',
            r'^\s*Disorders of the\s*$',
            r'^Gastrointestinal Tract$',
            r'^\s*Gastrointestinal Tract\s+\d+\s*$',  # "Gastrointestinal Tract    1"
            r'^\d{1,3}$',  # Page numbers (1-3 digits standalone)
        ]
        
        # Section artifacts pattern (e.g., "— Diarrhoea —")
        self.section_artifacts = re.compile(r'\s*—\s*[^—]+\s*—\s*')
        
        # Bullet point replacement (including cases like "FBCyy")
        self.bullet_pattern = re.compile(r'yy\s')
        
        # Page number at end of line
        self.page_number_pattern = re.compile(r'\s+\d{1,3}\s*$')
        
        # Chapter header components (for multi-line headers)
        self.chapter_components = [
            'Chapter', 'Disorders of the', 'Gastrointestinal Tract',
            'Respiratory Infections', 'Cardiovascular', 'Nervous System',
            'Respiratory System', 'Immunisable Diseases', 'Skin',
            'Gynaecological Disorders', 'Infectious Diseases and Infestations'
        ]
        
    def clean_text(self, text: str, remove_headers: bool = True, 
                   fix_bullets: bool = True, remove_artifacts: bool = True) -> str:
        """
        Clean extracted text from PyMuPDF
        
        Args:
            text: Raw text from PyMuPDF extraction
            remove_headers: Remove headers and footers
            fix_bullets: Replace yy with bullet points
            remove_artifacts: Remove section artifacts
            
        Returns:
            Cleaned text
        """
        lines = text.split('\n')
        cleaned_lines = []
        
        skip_next = 0
        in_chapter_header = False
        
        for i, line in enumerate(lines):
            # Skip if we're in a multi-line skip
            if skip_next > 0:
                skip_next -= 1
                continue
            
            # Special handling for the start of chapter headers
            if i < 10 and line.strip() in ['1', '11']:  # Chapter numbers at start
                # Look ahead to see if this is part of a chapter header
                if i + 1 < len(lines) and 'Chapter' in lines[i + 1]:
                    in_chapter_header = True
                    continue
            
            # If we're in a chapter header sequence, skip chapter components
            if in_chapter_header:
                if self._is_chapter_component(line):
                    continue
                else:
                    # End of chapter header - but don't process this line if it's also a component
                    in_chapter_header = False
                    if self._is_chapter_component(line):
                        continue
            
            # Check if this is a header/footer to remove
            if remove_headers and self._is_header_footer(line):
                # Check for multi-line chapter headers
                if self._is_chapter_component(line) and i < len(lines) - 2:
                    # Check if next lines are also chapter components
                    if i + 1 < len(lines) and self._is_chapter_component(lines[i+1]):
                        skip_next = 1
                        if i + 2 < len(lines) and self._is_chapter_component(lines[i+2]):
                            skip_next = 2
                continue
            
            # Clean the line
            cleaned_line = line
            
            # Fix bullet points
            if fix_bullets:
                cleaned_line = self.bullet_pattern.sub('•', cleaned_line)
            
            # Remove section artifacts
            if remove_artifacts:
                cleaned_line = self.section_artifacts.sub('', cleaned_line)
            
            # Remove trailing page numbers
            if remove_headers:
                # Only remove if it's likely a page number (end of page)
                if i == len(lines) - 1 or (i < len(lines) - 1 and not lines[i+1].strip()):
                    cleaned_line = self.page_number_pattern.sub('', cleaned_line)
            
            # Add cleaned line if not empty
            if cleaned_line.strip():
                cleaned_lines.append(cleaned_line)
        
        return '\n'.join(cleaned_lines)
    
    def _is_header_footer(self, line: str) -> bool:
        """
        Check if a line is a header or footer
        
        Args:
            line: Text line to check
            
        Returns:
            True if header/footer
        """
        line = line.strip()
        
        # Check against known patterns
        for pattern in self.headers_footers:
            if re.match(pattern, line):
                return True
        
        # Check if it's just a page number
        if re.match(r'^\d{1,3}$', line):
            return True
        
        # Check for chapter header components with flexible matching
        if line in ['Gastrointestinal Tract', 'Disorders of the', 'Disorders of the Gastrointestinal Tract']:
            return True
            
        return False
    
    def _is_chapter_component(self, line: str) -> bool:
        """
        Check if line is part of a chapter header
        
        Args:
            line: Text line to check
            
        Returns:
            True if part of chapter header
        """
        line = line.strip()
        
        # Check for exact matches (including with trailing spaces)
        if line in self.chapter_components:
            return True
        
        # Check for chapter header parts
        if line in ['Gastrointestinal Tract', 'Disorders of the', 'Disorders of the Gastrointestinal Tract']:
            return True
        
        # Check for chapter number patterns
        if re.match(r'^Chapter\s*\d*$', line):
            return True
        
        # Check for numbered chapter title (e.g., "1" as part of "Chapter 1")
        if re.match(r'^\d{1,2}$', line) and len(line) <= 2:
            return True
        
        return False
    
    def clean_table_text(self, table_data: List[List[str]]) -> List[List[str]]:
        """
        Clean table data
        
        Args:
            table_data: Table data from PyMuPDF
            
        Returns:
            Cleaned table data
        """
        cleaned_table = []
        
        for row in table_data:
            cleaned_row = []
            for cell in row:
                if cell:
                    # Clean cell text
                    cell = self.bullet_pattern.sub('•', str(cell))
                    cell = self.section_artifacts.sub('', cell)
                    cell = cell.strip()
                cleaned_row.append(cell)
            cleaned_table.append(cleaned_row)
        
        return cleaned_table
    
    def merge_split_sentences(self, text: str) -> str:
        """
        Merge sentences that were split across lines
        
        Args:
            text: Text with potential split sentences
            
        Returns:
            Text with merged sentences
        """
        lines = text.split('\n')
        merged_lines = []
        current_paragraph = []
        
        for line in lines:
            line = line.strip()
            
            if not line:
                # Empty line - end of paragraph
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                    current_paragraph = []
                merged_lines.append('')
                continue
            
            # Check if this is a table introduction line
            if 'following table' in line.lower() or 'table below' in line.lower() or 'table shows' in line.lower():
                # Save current paragraph and add table intro as separate line
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                    current_paragraph = []
                merged_lines.append(line)
                continue
            
            # Check if this continues a table introduction
            if current_paragraph and 'following table' in ' '.join(current_paragraph).lower():
                # This is likely the continuation of table intro, keep it separate
                merged_lines.append(line)
                continue
            
            # Check for [Table X] markers - keep them separate
            if line.startswith('[Table'):
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                    current_paragraph = []
                merged_lines.append(line)
                continue
            
            # Check for ASCII table lines - preserve table formatting
            if line.startswith('+') or line.startswith('|'):
                # This is a table row or border - preserve as-is
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                    current_paragraph = []
                merged_lines.append(line)
                continue
            
            # Check for section headers (A., B., C., etc.) or treatment headers
            if re.match(r'^[A-Z]\.\s+', line) or 'treatment' in line.lower():
                # Section header or treatment header - keep on separate line
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                    current_paragraph = []
                merged_lines.append(line)
                continue
            
            # Check if line starts with bullet or number
            if line.startswith('•') or re.match(r'^\d+\.', line) or line.isupper():
                # New item or heading - save current paragraph
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                    current_paragraph = []
                merged_lines.append(line)
            elif line[0].isupper() and current_paragraph and current_paragraph[-1].endswith('.'):
                # New sentence - save current paragraph
                if current_paragraph:
                    merged_lines.append(' '.join(current_paragraph))
                current_paragraph = [line]
            else:
                # Continue current paragraph
                current_paragraph.append(line)
        
        # Add remaining paragraph
        if current_paragraph:
            merged_lines.append(' '.join(current_paragraph))
        
        return '\n'.join(merged_lines)
    
    def clean_page(self, page_data: Dict) -> Dict:
        """
        Clean a complete page data structure from PyMuPDF
        
        Args:
            page_data: Page data from PyMuPDF extraction
            
        Returns:
            Cleaned page data
        """
        cleaned = page_data.copy()
        
        # Clean full text if present
        if 'full_text' in cleaned:
            cleaned['full_text'] = self.clean_text(cleaned['full_text'])
            cleaned['full_text'] = self.merge_split_sentences(cleaned['full_text'])
        
        # Clean text in blocks
        if 'blocks' in cleaned:
            for block in cleaned['blocks']:
                for line in block.get('lines', []):
                    for span in line.get('spans', []):
                        if 'text' in span:
                            span['text'] = self.clean_text(span['text'], 
                                                          remove_headers=False)
        
        # Clean tables
        if 'tables' in cleaned:
            for table in cleaned['tables']:
                if 'data' in table:
                    table['data'] = self.clean_table_text(table['data'])
        
        # Clean headers
        if 'headers' in cleaned:
            cleaned_headers = []
            for header in cleaned['headers']:
                # Skip if it's a page header/footer
                if not self._is_header_footer(header.get('text', '')):
                    header['text'] = self.clean_text(header['text'], 
                                                    remove_headers=False)
                    cleaned_headers.append(header)
            cleaned['headers'] = cleaned_headers
        
        return cleaned


def test_cleaner():
    """Test the text cleaner with sample text"""
    
    sample_text = """                         Chapter

  Disorders of the
  Gastrointestinal Tract            1


8.   Diarrhoea
    Diarrhoea is defined as the passage of frequent, loose, watery stools
3 or more times a day. Diarrhoea may be accompanied by vomiting.
Causes
Acute diarrhoea (< 2 weeks)
yy   Infections
     yy    Viral: e.g. rotavirus, norovirus
     yy   Bacterial: e.g. Salmonella spp., Shigella, Campylobacter, E. coli,
          Vibrio cholerae
yy   Drug-induced: e.g. penicillins
Symptoms— Diarrhoea —
yy   Frequent watery stools
yy   Blood or mucus in the stool
                            11"""
    
    cleaner = TextCleaner()
    cleaned = cleaner.clean_text(sample_text)
    cleaned = cleaner.merge_split_sentences(cleaned)
    
    # Test completed successfully
    return cleaned


if __name__ == "__main__":
    # Module can be run standalone for testing
    test_cleaner()