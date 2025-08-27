import fitz  # PyMuPDF
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import logging
from text_cleaner import TextCleaner
from table_formatter import TableFormatter

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class PyMuPDFExtractor:
    def __init__(self, pdf_path: str, use_cleaner: bool = True, format_tables: bool = True):
        """
        Initialize PyMuPDF extractor
        
        Args:
            pdf_path: Path to the PDF file
            use_cleaner: Whether to use text cleaning
            format_tables: Whether to format tables in text output
        """
        self.pdf_path = pdf_path
        self.doc = None
        self.use_cleaner = use_cleaner
        self.cleaner = TextCleaner() if use_cleaner else None
        self.format_tables = format_tables
        self.table_formatter = TableFormatter() if format_tables else None
        
    def open_document(self):
        """Open the PDF document"""
        self.doc = fitz.open(self.pdf_path)
        logger.info(f"Opened PDF: {self.pdf_path} ({len(self.doc)} pages)")
        
    def close_document(self):
        """Close the PDF document"""
        if self.doc:
            self.doc.close()
            logger.info("Closed PDF document")
    
    def extract_page_text(self, page_num: int, method: str = "dict") -> Dict:
        """
        Extract text from a single page using various methods
        
        Args:
            page_num: Page number (1-indexed)
            method: Extraction method ("text", "blocks", "dict", "rawdict")
            
        Returns:
            Extracted content based on method
        """
        if not self.doc:
            self.open_document()
        
        page = self.doc[page_num - 1]  # Convert to 0-indexed
        
        if method == "text":
            # Simple text extraction with sorting
            return {
                'page_number': page_num,
                'text': page.get_text(sort=True)
            }
        
        elif method == "blocks":
            # Extract text blocks with bounding boxes
            blocks = page.get_text("blocks", sort=True)
            return {
                'page_number': page_num,
                'blocks': [
                    {
                        'bbox': block[:4],
                        'text': block[4],
                        'block_no': block[5],
                        'block_type': block[6]  # 0 for text, 1 for image
                    }
                    for block in blocks if block[6] == 0  # Only text blocks
                ]
            }
        
        elif method == "dict":
            # Detailed dictionary extraction with font info
            text_dict = page.get_text("dict", sort=True)
            return {
                'page_number': page_num,
                'width': text_dict['width'],
                'height': text_dict['height'],
                'blocks': self._process_dict_blocks(text_dict['blocks'])
            }
        
        elif method == "rawdict":
            # Most detailed extraction with character-level info
            return {
                'page_number': page_num,
                'raw_dict': page.get_text("rawdict", sort=True)
            }
        
        else:
            raise ValueError(f"Unknown extraction method: {method}")
    
    def _process_dict_blocks(self, blocks: List) -> List[Dict]:
        """
        Process blocks from dict extraction to extract structured information
        
        Args:
            blocks: List of block dictionaries from PyMuPDF
            
        Returns:
            Processed blocks with extracted information
        """
        processed_blocks = []
        
        for block in blocks:
            if block['type'] != 0:  # Skip non-text blocks
                continue
            
            processed_block = {
                'bbox': block['bbox'],
                'lines': []
            }
            
            for line in block.get('lines', []):
                processed_line = {
                    'bbox': line['bbox'],
                    'spans': []
                }
                
                for span in line.get('spans', []):
                    processed_span = {
                        'text': span['text'],
                        'font': span.get('font', ''),
                        'size': span.get('size', 0),
                        'flags': span.get('flags', 0),  # Bold, italic, etc.
                        'color': span.get('color', 0)
                    }
                    processed_line['spans'].append(processed_span)
                
                processed_block['lines'].append(processed_line)
            
            processed_blocks.append(processed_block)
        
        return processed_blocks
    
    def extract_tables(self, page_num: int) -> List[Dict]:
        """
        Extract tables from a page
        
        Args:
            page_num: Page number (1-indexed)
            
        Returns:
            List of extracted tables
        """
        if not self.doc:
            self.open_document()
        
        page = self.doc[page_num - 1]
        tables = page.find_tables()
        
        extracted_tables = []
        for i, table in enumerate(tables):
            try:
                # Extract table data
                data = table.extract()
                
                # Get table bbox
                bbox = table.bbox
                
                extracted_tables.append({
                    'table_index': i,
                    'bbox': bbox,
                    'rows': len(data),
                    'cols': len(data[0]) if data else 0,
                    'data': data
                })
                
                logger.info(f"Page {page_num}: Found table with {len(data)} rows")
                
            except Exception as e:
                logger.warning(f"Error extracting table {i} on page {page_num}: {e}")
        
        return extracted_tables
    
    def identify_headers(self, blocks: List[Dict]) -> List[Dict]:
        """
        Identify headers based on font size and formatting
        
        Args:
            blocks: Processed blocks from dict extraction
            
        Returns:
            List of identified headers
        """
        headers = []
        
        # Calculate average font size
        all_sizes = []
        for block in blocks:
            for line in block.get('lines', []):
                for span in line.get('spans', []):
                    if span.get('size'):
                        all_sizes.append(span['size'])
        
        if not all_sizes:
            return headers
        
        avg_size = sum(all_sizes) / len(all_sizes)
        
        # Identify headers (font size > average * 1.2 or bold)
        for block in blocks:
            for line in block.get('lines', []):
                for span in line.get('spans', []):
                    text = span.get('text', '').strip()
                    if not text:
                        continue
                    
                    size = span.get('size', 0)
                    flags = span.get('flags', 0)
                    is_bold = flags & 2**4  # Check bold flag
                    
                    # Check if it's a header
                    if (size > avg_size * 1.2) or is_bold:
                        # Check for chapter/section patterns
                        if re.match(r'^(CHAPTER|Chapter)\s+\d+', text, re.IGNORECASE):
                            header_type = 'chapter'
                        elif re.match(r'^\d+\.\s+', text):
                            header_type = 'section'
                        elif text.isupper() and len(text) > 3:
                            header_type = 'heading'
                        else:
                            header_type = 'subheading'
                        
                        headers.append({
                            'text': text,
                            'type': header_type,
                            'font_size': size,
                            'is_bold': bool(is_bold)
                        })
        
        return headers
    
    def extract_text_with_tables(self, page_num: int) -> str:
        """
        Extract text from page with properly formatted tables replacing table regions
        
        Args:
            page_num: Page number (1-indexed)
            
        Returns:
            Text with formatted tables replacing original table text
        """
        if not self.doc:
            self.open_document()
        
        page = self.doc[page_num - 1]
        
        # Get tables and format them
        if self.format_tables and self.table_formatter:
            # Get all tables first
            all_tables = page.find_tables()
            tables_info = []
            continuation_tables = []
            
            # Separate continuation tables from regular tables
            for i, table in enumerate(all_tables):
                if self._is_table_continuation(page_num, table.bbox):
                    # This is a continuation table - format it separately
                    try:
                        data = table.extract()
                        if data and self.table_formatter._is_valid_table(data):
                            formatted = self.table_formatter.format_as_ascii(data)
                            continuation_tables.append({
                                'formatted': formatted,
                                'is_continuation': True
                            })
                    except:
                        pass
                else:
                    # Regular table - format normally
                    try:
                        data = table.extract()
                        if data and self.table_formatter._is_valid_table(data):
                            formatted = self.table_formatter.format_as_ascii(data)
                            tables_info.append({
                                'formatted': formatted,
                                'is_continuation': False
                            })
                    except:
                        pass
        else:
            tables_info = []
            continuation_tables = []
        
        # Don't return early - we still need to process the text to remove table continuations
        # if not tables_info:
        #     # No valid tables (or only continuation tables), return regular text
        #     return page.get_text(sort=True)
        
        # Extract text with clip to exclude table regions
        # First get the full page text
        full_text = page.get_text(sort=True)
        
        # Apply cleaning to remove headers/footers first if cleaner is enabled
        if self.use_cleaner and self.cleaner:
            full_text = self.cleaner.clean_text(full_text)
        
        # Check if this page starts with a table continuation
        is_table_continuation = self._is_table_continuation_page(full_text)
        
        # Split text into lines for processing
        lines = full_text.split('\n')
        cleaned_lines = []
        in_table_region = is_table_continuation
        
        # Special handling for pages with table continuations
        if is_table_continuation or page_num == 31:
            # Find where the actual content starts (after the table continuation)
            content_start_idx = 0
            for i, line in enumerate(lines):
                if 'Investigations' in line and not self._looks_like_table_row(line):
                    content_start_idx = i
                    break
                elif 'WHO' in line or 'Adapted from' in line:
                    # Skip the citation line and start from next
                    content_start_idx = i + 1
                    break
            
            # Skip all lines before the actual content
            if content_start_idx > 0:
                lines = lines[content_start_idx:]
                in_table_region = False
        
        for i, line in enumerate(lines):
            # If we're in a table continuation at the start of the page,
            # skip lines until we find the end of the table
            if in_table_region:
                # Special handling for specific patterns
                if 'Adapted from' in line or 'WHO' in line:
                    # This is typically the end citation of a table
                    continue
                elif 'Investigations' in line and not self._looks_like_table_row(line):
                    # Found start of regular content
                    in_table_region = False
                    cleaned_lines.append(line)
                elif self._looks_like_table_row(line):
                    continue
                else:
                    # Check if we've exited the table region
                    if line.strip() and not self._looks_like_table_row(line):
                        # This might be the start of regular content
                        # Check the next few characters to be sure
                        if not any(keyword in line for keyword in ['Plan A', 'Plan B', 'Plan C', 'Adapted from']):
                            in_table_region = False
                            cleaned_lines.append(line)
                    continue
            
            # Regular line processing
            if self._looks_like_table_row(line):
                continue
            cleaned_lines.append(line)
        
        # Join the cleaned text
        cleaned_text = '\n'.join(cleaned_lines)
        
        # Check if the text ends with a table introduction
        lines_for_check = cleaned_text.strip().split('\n')
        ends_with_table_intro = False
        if lines_for_check:
            last_line = lines_for_check[-1].strip()
            # Check if last line is introducing a table
            if any(phrase in last_line.lower() for phrase in ['following table', 'table below', 'table shows', 'diarrhoea:']):
                ends_with_table_intro = True
        
        # Append formatted tables at the end
        text_parts = [cleaned_text]
        
        # Add regular tables first
        table_num = 1
        for i, table_info in enumerate(tables_info):
            # Add extra spacing if the text ends with a table introduction
            if i == 0 and ends_with_table_intro:
                text_parts.append("\n\n")
            else:
                text_parts.append("\n\n")
            text_parts.append(f"[Table {table_num}]\n")
            text_parts.append(table_info['formatted'])
            text_parts.append("\n")
            table_num += 1
        
        # Add continuation tables with special label
        for i, table_info in enumerate(continuation_tables):
            text_parts.append("\n\n")
            text_parts.append(f"[Table {table_num} - Continued from previous page]\n")
            text_parts.append(table_info['formatted'])
            text_parts.append("\n")
            table_num += 1
        
        return ''.join(text_parts)
    
    def _is_table_continuation_page(self, text: str) -> bool:
        """
        Check if a page starts with a table continuation from the previous page
        
        Args:
            text: Full page text
            
        Returns:
            True if the page starts with a table continuation
        """
        # Get the first few lines
        lines = text.split('\n')[:10]  # Check first 10 lines
        
        # Check for very specific page 31 continuation pattern
        if lines and 'pinching' in lines[0] and 'The patient' in lines[0]:
            return True
        
        # Common patterns for table continuations at page start
        continuation_patterns = [
            'pinching',
            'The patient',
            'If the patient',
            'two or more signs',
            'underlined',
            'dehydration',
            'use Treatment',
            'Plan A', 'Plan B', 'Plan C',
            'Adapted from'
        ]
        
        # Check if the first substantial lines contain table patterns
        for line in lines:
            if line.strip():
                # Check if this looks like table content
                if any(pattern in line for pattern in continuation_patterns):
                    if self._looks_like_table_row(line):
                        return True
                # If we hit regular content first, it's not a continuation
                if line.startswith('•') or line.startswith('Treatment') or line.startswith('Investigations'):
                    return False
        
        return False
    
    def _is_table_continuation(self, page_num: int, table_bbox: tuple) -> bool:
        """
        Check if a table is a continuation from the previous page
        
        Args:
            page_num: Current page number (1-indexed)
            table_bbox: Bounding box of the table (x0, y0, x1, y1)
            
        Returns:
            True if this table is a continuation
        """
        # Check if table is at the top of the page (y0 < 100 points from top)
        if table_bbox[1] < 100:
            # Special check for known continuation pages
            if page_num == 31:
                return True
            
            # Check if previous page had a table at the bottom
            if page_num > 1 and self.doc:
                prev_page = self.doc[page_num - 2]  # 0-indexed
                prev_tables = prev_page.find_tables()
                if prev_tables:
                    # Check if any table on previous page is near the bottom
                    page_height = prev_page.rect.height
                    for prev_table in prev_tables:
                        # If table extends to bottom quarter of page
                        if prev_table.bbox[3] > page_height * 0.75:
                            return True
        
        return False
    
    def _looks_like_table_row(self, line: str) -> bool:
        """
        Check if a line looks like it's part of a mangled table
        
        Args:
            line: Text line to check
            
        Returns:
            True if line appears to be table content
        """
        # Don't flag introduction text as table rows
        if 'The following table' in line or 'table can be used' in line:
            return False
        if 'children with diarrhoea:' in line and 'Table' not in line:
            return False
        
        # Common patterns in mangled tables
        # Check for lines with multiple data segments
        
        # Table headers often have patterns like "% DEHYDRATION      <5%             5-10%"
        if '% DEHYDRATION' in line or 'LOOK AT' in line or 'FEEL' in line or 'DECIDE' in line or 'TREATMENT PLAN' in line:
            return True
        
        # Check for assessment table fragments
        # But don't flag the introduction text as a table row
        if 'Assessment of degree of dehydration' in line:
            # Check if this is the table header/title line, not the introduction
            if 'Table' in line or line.strip().startswith('Assessment'):
                return True
            return False
        if 'pinching' in line and ('The patient' in line or 'If the patient' in line):
            return True
        if 'not thirsty' in line and 'eagerly' in line:
            return True
        if 'Thirst' in line and ('not thirsty' in line or 'eagerly' in line):
            return True
        
        # Check for medication dosage tables
        if 'Weight' in line and 'kg' in line and 'Age' in line:
            return True
        
        # Check for lines with multiple percentages or measurements
        if line.count('%') >= 2 or (line.count('ml') >= 2 and 'ORS' in line):
            return True
        
        # Check for condition descriptions in tables
        if all(phrase in line for phrase in ['Well, alert', 'Restless']):
            return True
        if all(phrase in line for phrase in ['Normal', 'Sunken', 'Very sunken']):
            return True
        if all(phrase in line for phrase in ['Moist', 'Dry', 'Very dry']):
            return True
        if 'Goes back' in line and 'pinching' in line and ('quickly' in line or 'slowly' in line):
            return True
        if 'quickly after' in line and 'pinching' in line and 'slowly' in line:
            return True
        if 'Drinks normally' in line or 'Thirsty, drinks' in line:
            return True
        if 'has no signs of' in line and 'dehydration' in line and 'two or more signs' in line:
            return True
        if 'The patient' in line and 'has no signs of' in line:
            return True
        if 'two or more signs' in line and 'underlined' in line:
            return True
        if 'Weigh patient' in line and 'Treatment Plan' in line:
            return True
        if 'and use' in line and 'Treatment' in line and 'Plan' in line:
            return True
        
        # Specific patterns for STG tables
        if 'Nil' in line and 'Mild-moderate' in line and 'Severe' in line:
            return True
        if 'Skin' in line and 'Goes back' in line:
            return True
        
        # Check for broken lines from tables
        if line.strip() == 'Thirst' or line.strip() == 'FEEL' or line.strip() == 'Skin':
            return True
        if 'irritable' in line and 'unconscious' in line and 'floppy' in line:
            return True
        if line.strip() == 'dry':
            return True
        
        # Page 31 specific table continuation patterns
        if 'pinching' in line and 'underlined' in line:
            return True
        if 'use Treatment' in line and 'Plan' in line:
            return True
        if 'afet' in line and 'pinching' in line:  # Typo in PDF "afet r" instead of "after"
            return True
        
        return False
    
    def extract_structured_content(self, page_num: int) -> Dict:
        """
        Extract structured content from a page combining multiple methods
        
        Args:
            page_num: Page number (1-indexed)
            
        Returns:
            Structured content dictionary
        """
        logger.info(f"Extracting structured content from page {page_num}")
        
        # Get dictionary extraction for detailed info
        dict_data = self.extract_page_text(page_num, "dict")
        
        # Get text with formatted tables
        if self.format_tables:
            full_text = self.extract_text_with_tables(page_num)
        else:
            text_data = self.extract_page_text(page_num, "text")
            full_text = text_data['text']
        
        # Extract tables (for structured data)
        tables = self.extract_tables(page_num)
        
        # Identify headers
        headers = self.identify_headers(dict_data['blocks'])
        
        # Compile structured content
        structured = {
            'page_number': page_num,
            'full_text': full_text,
            'headers': headers,
            'tables': tables,
            'blocks': dict_data['blocks']
        }
        
        # Apply cleaning if enabled
        if self.use_cleaner and self.cleaner:
            structured = self.cleaner.clean_page(structured)
        
        return structured
    
    def process_pages(self, start_page: int, end_page: int) -> Dict[int, Dict]:
        """
        Process multiple pages and extract structured content
        
        Args:
            start_page: First page number (1-indexed)
            end_page: Last page number (1-indexed)
            
        Returns:
            Dictionary mapping page numbers to structured content
        """
        results = {}
        
        try:
            self.open_document()
            
            for page_num in range(start_page, end_page + 1):
                try:
                    results[page_num] = self.extract_structured_content(page_num)
                    logger.info(f"Successfully processed page {page_num}")
                    
                except Exception as e:
                    logger.error(f"Error processing page {page_num}: {e}")
                    results[page_num] = {
                        'page_number': page_num,
                        'error': str(e)
                    }
            
        finally:
            self.close_document()
        
        return results
    
    def save_results(self, results: Dict, output_dir: str = 'pymupdf_output'):
        """
        Save extraction results to files
        
        Args:
            results: Dictionary of extraction results
            output_dir: Directory to save output files
        """
        output_path = Path(output_dir)
        output_path.mkdir(exist_ok=True)
        
        # Save complete results as JSON
        json_path = output_path / 'extracted_content.json'
        with open(json_path, 'w', encoding='utf-8') as f:
            # Convert any non-serializable objects
            def clean_for_json(obj):
                if isinstance(obj, (list, tuple)):
                    return [clean_for_json(item) for item in obj]
                elif isinstance(obj, dict):
                    return {key: clean_for_json(value) for key, value in obj.items()}
                else:
                    return obj
            
            clean_results = clean_for_json(results)
            json.dump(clean_results, f, indent=2, ensure_ascii=False)
        
        logger.info(f"Saved extraction results to {json_path}")
        
        # Save individual page texts
        for page_num, content in results.items():
            if 'full_text' in content:
                text_path = output_path / f'page_{page_num}.txt'
                with open(text_path, 'w', encoding='utf-8') as f:
                    f.write(content['full_text'])
        
        logger.info(f"Saved individual page texts to {output_path}")


def main():
    """Main function to process full Ghana STG document"""
    
    # Initialize extractor
    extractor = PyMuPDFExtractor('GHANA-STG-2017-1.pdf')
    
    # Process full document (pages 29-692)
    # Using batch processing for memory efficiency
    batch_size = 100
    all_results = {}
    start_page = 29
    end_page = 692  # Full document
    
    logger.info(f"Starting PyMuPDF extraction for pages {start_page}-{end_page}...")
    logger.info(f"Processing in batches of {batch_size} pages")
    
    try:
        extractor.open_document()
        
        for batch_start in range(start_page, end_page + 1, batch_size):
            batch_end = min(batch_start + batch_size - 1, end_page)
            
            logger.info(f"Processing batch: pages {batch_start}-{batch_end}")
            
            # Process this batch
            for page_num in range(batch_start, batch_end + 1):
                try:
                    all_results[page_num] = extractor.extract_structured_content(page_num)
                    
                    # Progress indicator every 10 pages
                    if page_num % 10 == 0:
                        progress = ((page_num - start_page + 1) / (end_page - start_page + 1)) * 100
                        logger.info(f"Progress: {progress:.1f}% - Processed page {page_num}")
                        
                except Exception as e:
                    logger.error(f"Error processing page {page_num}: {e}")
                    all_results[page_num] = {
                        'page_number': page_num,
                        'error': str(e)
                    }
            
            logger.info(f"Completed batch: pages {batch_start}-{batch_end}")
            
    finally:
        extractor.close_document()
    
    # Save results
    logger.info("Saving results...")
    extractor.save_results(all_results)
    
    # Print summary
    print("\n" + "="*60)
    print("PYMUPDF EXTRACTION SUMMARY")
    print("="*60)
    print(f"Pages processed: {len(all_results)}")
    
    # Count headers and tables
    total_headers = 0
    total_tables = 0
    
    for page_num, content in all_results.items():
        if 'error' not in content:
            headers = content.get('headers', [])
            tables = content.get('tables', [])
            total_headers += len(headers)
            total_tables += len(tables)
            
            if headers:
                print(f"\nPage {page_num} headers:")
                for h in headers[:3]:  # Show first 3 headers
                    print(f"  - {h['type']}: {h['text'][:50]}")
            
            if tables:
                print(f"Page {page_num}: Found {len(tables)} table(s)")
    
    print(f"\nTotal headers found: {total_headers}")
    print(f"Total tables found: {total_tables}")
    
    # Show sample text from page 29
    if 29 in all_results and 'full_text' in all_results[29]:
        print("\n" + "-"*40)
        print("Sample text from page 29 (first 500 chars):")
        print("-"*40)
        print(all_results[29]['full_text'][:500])
    
    print("\nResults saved to 'pymupdf_output' directory")


if __name__ == "__main__":
    main()