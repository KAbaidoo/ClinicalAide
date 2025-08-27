import json
import re
from typing import Dict, List, Optional, Tuple
import logging
from pathlib import Path

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class EnhancedParser:
    def __init__(self):
        """Initialize the enhanced parser for PyMuPDF output"""
        
        # Patterns for identifying structure
        self.patterns = {
            'chapter': re.compile(r'^(?:CHAPTER|Chapter)\s+(\d+)[:\s]*(.+)?', re.IGNORECASE),
            'section': re.compile(r'^(\d+)\.\s+(.+)$'),
            'subsection': re.compile(r'^([A-Z])\.\s+(.+)$'),
            'medication': re.compile(r'([\w\s,]+),?\s+(oral|IV|IM|SC|topical|inhaled),?\s*(\d+\s*mg|\d+\s*ml|\d+%)', re.IGNORECASE),
            'dosage': re.compile(r'(\d+(?:\.\d+)?)\s*(mg|ml|g|mcg|units?|%)\s*(?:(\d+)\s*(?:times?|hourly|daily|bd|tds|qds))?', re.IGNORECASE),
            'note': re.compile(r'^Note\s+\d+-\d+', re.IGNORECASE),
            'evidence': re.compile(r'Evidence\s+Rating:\s*\[([A-D])\]', re.IGNORECASE),
            'table_ref': re.compile(r'Table\s+\d+-\d+:', re.IGNORECASE)
        }
        
        # Medical section keywords
        self.section_keywords = {
            'causes': ['causes', 'etiology', 'aetiology'],
            'symptoms': ['symptoms', 'clinical features', 'presentation'],
            'signs': ['signs', 'physical examination', 'examination findings'],
            'investigations': ['investigations', 'diagnostic tests', 'laboratory tests'],
            'treatment': ['treatment', 'management', 'therapy'],
            'pharmacological': ['pharmacological treatment', 'drug treatment', 'medications'],
            'non_pharmacological': ['non-pharmacological treatment', 'conservative management'],
            'referral': ['referral', 'referral criteria', 'when to refer'],
            'complications': ['complications', 'adverse effects', 'side effects']
        }
        
    def parse_structured_content(self, extracted_data: Dict) -> Dict:
        """
        Parse PyMuPDF extracted data into structured format
        
        Args:
            extracted_data: Data from PyMuPDF extractor
            
        Returns:
            Parsed structured content
        """
        page_num = extracted_data.get('page_number', 0)
        
        parsed = {
            'page_number': page_num,
            'chapter': None,
            'sections': [],
            'content_blocks': [],
            'medications': [],
            'tables': []
        }
        
        # Extract chapter information from headers
        for header in extracted_data.get('headers', []):
            if header['type'] == 'chapter':
                match = self.patterns['chapter'].match(header['text'])
                if match:
                    parsed['chapter'] = {
                        'number': match.group(1),
                        'title': (match.group(2) or '').strip()
                    }
                    break
        
        # Process full text for sections and content
        full_text = extracted_data.get('full_text', '')
        parsed['sections'] = self._extract_sections(full_text)
        parsed['content_blocks'] = self._extract_content_blocks(full_text)
        
        # Extract medications from text
        parsed['medications'] = self._extract_medications(full_text)
        
        # Process tables
        for table in extracted_data.get('tables', []):
            parsed_table = self._parse_table(table)
            if parsed_table:
                parsed['tables'].append(parsed_table)
        
        return parsed
    
    def _extract_sections(self, text: str) -> List[Dict]:
        """
        Extract sections and subsections from text
        
        Args:
            text: Full page text
            
        Returns:
            List of sections with their content
        """
        sections = []
        lines = text.split('\n')
        
        current_section = None
        current_content = []
        
        for i, line in enumerate(lines):
            line = line.strip()
            
            # Check for main section (e.g., "8. Diarrhoea")
            section_match = self.patterns['section'].match(line)
            if section_match:
                # Save previous section if exists
                if current_section:
                    current_section['content'] = '\n'.join(current_content).strip()
                    sections.append(current_section)
                
                # Start new section
                current_section = {
                    'number': section_match.group(1),
                    'title': section_match.group(2).strip(),
                    'type': 'main_section',
                    'subsections': []
                }
                current_content = []
                continue
            
            # Check for medical content sections (Causes, Symptoms, etc.)
            for section_type, keywords in self.section_keywords.items():
                if any(keyword.lower() in line.lower() for keyword in keywords):
                    if current_section:
                        # Add as subsection
                        subsection = {
                            'title': line,
                            'type': section_type,
                            'content': self._extract_section_content(lines[i+1:])
                        }
                        current_section['subsections'].append(subsection)
                    break
            
            # Accumulate content
            if current_section and line:
                current_content.append(line)
        
        # Save last section
        if current_section:
            current_section['content'] = '\n'.join(current_content).strip()
            sections.append(current_section)
        
        return sections
    
    def _extract_section_content(self, lines: List[str]) -> str:
        """
        Extract content for a specific section until next section marker
        
        Args:
            lines: Lines following section header
            
        Returns:
            Section content
        """
        content = []
        
        for line in lines:
            # Stop at next section marker
            if self._is_section_header(line):
                break
            content.append(line)
        
        return '\n'.join(content).strip()
    
    def _is_section_header(self, line: str) -> bool:
        """
        Check if line is a section header
        
        Args:
            line: Text line
            
        Returns:
            True if section header
        """
        line = line.strip()
        
        # Check for numbered sections
        if self.patterns['section'].match(line):
            return True
        
        # Check for medical sections
        for keywords in self.section_keywords.values():
            if any(keyword.lower() == line.lower() for keyword in keywords):
                return True
        
        return False
    
    def _extract_content_blocks(self, text: str) -> List[Dict]:
        """
        Extract and categorize content blocks
        
        Args:
            text: Full text
            
        Returns:
            List of categorized content blocks
        """
        blocks = []
        
        # Split into paragraphs
        paragraphs = re.split(r'\n\s*\n', text)
        
        for para in paragraphs:
            para = para.strip()
            if not para:
                continue
            
            block = {
                'content': para,
                'type': self._identify_block_type(para)
            }
            
            # Extract additional metadata
            if block['type'] == 'treatment':
                evidence_match = self.patterns['evidence'].search(para)
                if evidence_match:
                    block['evidence_rating'] = evidence_match.group(1)
            
            elif block['type'] == 'note':
                note_match = self.patterns['note'].match(para)
                if note_match:
                    block['note_id'] = note_match.group(0)
            
            blocks.append(block)
        
        return blocks
    
    def _identify_block_type(self, text: str) -> str:
        """
        Identify the type of content block
        
        Args:
            text: Block text
            
        Returns:
            Block type
        """
        text_lower = text.lower()
        
        # Check for specific patterns (updated for cleaned text with • bullets)
        if text.startswith('•') or text.startswith('yy'):
            return 'bullet_list'
        elif self.patterns['note'].match(text):
            return 'note'
        elif self.patterns['table_ref'].match(text):
            return 'table_reference'
        elif 'evidence rating' in text_lower:
            return 'treatment'
        elif any(keyword in text_lower for keyword in ['mg', 'ml', 'dose', 'hourly', 'daily']):
            return 'medication'
        elif text.isupper() and len(text) < 50:
            return 'heading'
        else:
            return 'paragraph'
    
    def _extract_medications(self, text: str) -> List[Dict]:
        """
        Extract medication information from text
        
        Args:
            text: Full text
            
        Returns:
            List of medications with details
        """
        medications = []
        
        # Find medication lines
        lines = text.split('\n')
        for line in lines:
            # Look for medication patterns
            med_match = self.patterns['medication'].search(line)
            if med_match:
                medication = {
                    'name': med_match.group(1).strip(),
                    'route': med_match.group(2),
                    'dose': med_match.group(3)
                }
                
                # Extract additional dosage info
                dosage_matches = self.patterns['dosage'].findall(line)
                if dosage_matches:
                    medication['dosage_details'] = [
                        {
                            'amount': match[0],
                            'unit': match[1],
                            'frequency': match[2] if len(match) > 2 else None
                        }
                        for match in dosage_matches
                    ]
                
                medications.append(medication)
        
        return medications
    
    def _parse_table(self, table: Dict) -> Optional[Dict]:
        """
        Parse table data into structured format
        
        Args:
            table: Table data from PyMuPDF
            
        Returns:
            Parsed table or None
        """
        if not table.get('data'):
            return None
        
        data = table['data']
        
        # Identify table type based on content
        table_type = self._identify_table_type(data)
        
        parsed_table = {
            'type': table_type,
            'rows': table['rows'],
            'cols': table['cols'],
            'headers': data[0] if data else [],
            'data': data[1:] if len(data) > 1 else []
        }
        
        # Special parsing for medication tables
        if table_type == 'medication':
            parsed_table['medications'] = self._parse_medication_table(data)
        
        return parsed_table
    
    def _identify_table_type(self, data: List[List[str]]) -> str:
        """
        Identify the type of table based on content
        
        Args:
            data: Table data
            
        Returns:
            Table type
        """
        if not data:
            return 'unknown'
        
        # Check headers and content for clues
        headers_text = ' '.join(str(cell) for cell in data[0] if cell).lower()
        
        if any(word in headers_text for word in ['drug', 'medication', 'dose', 'mg', 'ml']):
            return 'medication'
        elif 'dehydration' in headers_text:
            return 'assessment'
        elif 'treatment' in headers_text:
            return 'treatment'
        else:
            return 'general'
    
    def _parse_medication_table(self, data: List[List[str]]) -> List[Dict]:
        """
        Parse medication table into structured format
        
        Args:
            data: Table data
            
        Returns:
            List of medications
        """
        medications = []
        
        if len(data) < 2:
            return medications
        
        headers = data[0]
        
        for row in data[1:]:
            if not any(row):  # Skip empty rows
                continue
            
            med = {}
            for i, cell in enumerate(row):
                if i < len(headers) and headers[i] and cell:
                    header = headers[i].lower()
                    if 'drug' in header or 'medication' in header:
                        med['name'] = cell
                    elif 'dose' in header:
                        med['dose'] = cell
                    elif 'route' in header:
                        med['route'] = cell
                    elif 'frequency' in header:
                        med['frequency'] = cell
            
            if med:
                medications.append(med)
        
        return medications
    
    def process_all_pages(self, extracted_data: Dict) -> Dict:
        """
        Process all pages from PyMuPDF extraction
        
        Args:
            extracted_data: Complete extraction data
            
        Returns:
            Fully parsed data
        """
        parsed_data = {}
        
        for page_num, page_data in extracted_data.items():
            if 'error' not in page_data:
                logger.info(f"Parsing page {page_num}...")
                parsed_data[page_num] = self.parse_structured_content(page_data)
        
        return parsed_data


def main():
    """Test the enhanced parser"""
    
    # Load PyMuPDF extracted data
    with open('pymupdf_output/extracted_content.json', 'r', encoding='utf-8') as f:
        extracted_data = json.load(f)
    
    # Initialize parser
    parser = EnhancedParser()
    
    # Parse all pages
    parsed_data = parser.process_all_pages(extracted_data)
    
    # Save parsed data
    output_dir = Path('pymupdf_output')
    output_file = output_dir / 'parsed_structured_data.json'
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(parsed_data, f, indent=2, ensure_ascii=False)
    
    # Calculate summary statistics
    total_sections = 0
    total_medications = 0
    total_tables = 0
    chapters_found = []
    
    for page_num, data in parsed_data.items():
        if data['chapter']:
            chapters_found.append((page_num, data['chapter']))
        
        total_sections += len(data['sections'])
        total_medications += len(data['medications'])
        total_tables += len(data['tables'])
    
    # Print concise summary
    print(f"\nEnhanced parsing complete:")
    print(f"  - Pages: {len(parsed_data)}")
    print(f"  - Chapters: {len(chapters_found)}")
    print(f"  - Sections: {total_sections}")
    print(f"  - Medications: {total_medications}")
    print(f"  - Tables: {total_tables}")
    print(f"  - Output: {output_file}")


if __name__ == "__main__":
    main()