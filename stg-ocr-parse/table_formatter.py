import re
from typing import List, Dict, Optional, Tuple
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class TableFormatter:
    def __init__(self):
        """Initialize table formatter for converting table data to readable formats"""
        pass
    
    def format_as_ascii(self, table_data: List[List[str]], 
                       col_widths: Optional[List[int]] = None) -> str:
        """
        Format table data as ASCII table with borders
        
        Args:
            table_data: 2D list of table cells
            col_widths: Optional column widths, auto-calculated if not provided
            
        Returns:
            Formatted ASCII table string
        """
        if not table_data:
            return ""
        
        # Clean and prepare data
        cleaned_data = []
        for row in table_data:
            cleaned_row = []
            for cell in row:
                # Convert to string and clean
                cell_text = str(cell) if cell else ""
                # Replace newlines with spaces for ASCII format
                cell_text = cell_text.replace('\n', ' ')
                # Clean up multiple spaces
                cell_text = ' '.join(cell_text.split())
                cleaned_row.append(cell_text)
            cleaned_data.append(cleaned_row)
        
        # Calculate column widths if not provided
        if not col_widths:
            col_widths = self._calculate_column_widths(cleaned_data)
        
        # Build the table
        lines = []
        
        # Top border
        border_line = self._create_border_line(col_widths, '+', '-')
        lines.append(border_line)
        
        # Add rows
        for i, row in enumerate(cleaned_data):
            # Format cells in row
            row_line = self._format_row(row, col_widths)
            lines.append(row_line)
            
            # Add separator after header row (first row)
            if i == 0:
                lines.append(border_line)
        
        # Bottom border
        lines.append(border_line)
        
        return '\n'.join(lines)
    
    def format_as_markdown(self, table_data: List[List[str]]) -> str:
        """
        Format table data as Markdown table
        
        Args:
            table_data: 2D list of table cells
            
        Returns:
            Formatted Markdown table string
        """
        if not table_data:
            return ""
        
        lines = []
        
        for i, row in enumerate(table_data):
            # Clean cells
            cleaned_row = []
            for cell in row:
                cell_text = str(cell) if cell else ""
                # Replace newlines with <br> for markdown
                cell_text = cell_text.replace('\n', '<br>')
                # Escape pipe characters
                cell_text = cell_text.replace('|', '\\|')
                cleaned_row.append(cell_text)
            
            # Create row
            row_line = "| " + " | ".join(cleaned_row) + " |"
            lines.append(row_line)
            
            # Add separator after header
            if i == 0:
                separator = "|"
                for _ in range(len(row)):
                    separator += " --- |"
                lines.append(separator)
        
        return '\n'.join(lines)
    
    def format_as_text_block(self, table_data: List[List[str]], 
                            indent: int = 0) -> str:
        """
        Format table as aligned text block (simpler format)
        
        Args:
            table_data: 2D list of table cells
            indent: Number of spaces to indent the table
            
        Returns:
            Formatted text block
        """
        if not table_data:
            return ""
        
        # Clean data
        cleaned_data = []
        for row in table_data:
            cleaned_row = []
            for cell in row:
                cell_text = str(cell) if cell else ""
                cell_text = ' '.join(cell_text.replace('\n', ' ').split())
                cleaned_row.append(cell_text)
            cleaned_data.append(cleaned_row)
        
        # Calculate column widths
        col_widths = self._calculate_column_widths(cleaned_data)
        
        # Format rows
        lines = []
        indent_str = " " * indent
        
        for row in cleaned_data:
            row_parts = []
            for i, cell in enumerate(row):
                # Pad cell to column width
                if i < len(col_widths):
                    padded_cell = cell.ljust(col_widths[i])
                    row_parts.append(padded_cell)
            
            line = indent_str + "  ".join(row_parts)
            lines.append(line.rstrip())
        
        return '\n'.join(lines)
    
    def _calculate_column_widths(self, table_data: List[List[str]]) -> List[int]:
        """
        Calculate optimal column widths based on content
        
        Args:
            table_data: 2D list of table cells
            
        Returns:
            List of column widths
        """
        if not table_data:
            return []
        
        # Get maximum number of columns
        max_cols = max(len(row) for row in table_data)
        
        # Initialize widths
        widths = [0] * max_cols
        
        # Find maximum width for each column
        for row in table_data:
            for i, cell in enumerate(row):
                if i < max_cols:
                    cell_len = len(cell)
                    widths[i] = max(widths[i], cell_len)
        
        # Add padding
        widths = [w + 2 for w in widths]  # Add 2 spaces padding
        
        # Set minimum width
        widths = [max(w, 4) for w in widths]  # Minimum 4 characters
        
        return widths
    
    def _create_border_line(self, col_widths: List[int], 
                           corner: str, horizontal: str) -> str:
        """
        Create a border line for ASCII table
        
        Args:
            col_widths: Column widths
            corner: Corner character (e.g., '+')
            horizontal: Horizontal line character (e.g., '-')
            
        Returns:
            Border line string
        """
        parts = []
        for width in col_widths:
            parts.append(horizontal * width)
        
        return corner + corner.join(parts) + corner
    
    def _format_row(self, row: List[str], col_widths: List[int]) -> str:
        """
        Format a single row with proper padding
        
        Args:
            row: Row data
            col_widths: Column widths
            
        Returns:
            Formatted row string
        """
        parts = []
        
        for i, cell in enumerate(row):
            if i < len(col_widths):
                # Center the text in the cell
                padded = cell.center(col_widths[i] - 2)  # -2 for spaces
                parts.append(f" {padded} ")
            else:
                parts.append(f" {cell} ")
        
        # Pad remaining columns if row is shorter
        while len(parts) < len(col_widths):
            parts.append(" " * col_widths[len(parts)])
        
        return "|" + "|".join(parts) + "|"
    
    def _is_valid_table(self, data: List[List[str]]) -> bool:
        """
        Check if table data represents a valid table (not a false positive)
        
        Args:
            data: Table data
            
        Returns:
            True if valid table, False if likely a false positive
        """
        if not data or len(data) < 2:
            return False
        
        # Check if this is a Note box (Note 1-1, Note 1-2, etc.)
        # These are styled boxes, not tables
        if len(data) == 2 and len(data[0]) == 2:
            first_cell = str(data[0][0]) if data[0][0] else ""
            if first_cell.startswith("Note ") and "-" in first_cell:
                return False
        
        # Count non-empty cells
        total_cells = 0
        non_empty_cells = 0
        
        for row in data:
            for cell in row:
                total_cells += 1
                if cell and str(cell).strip():
                    non_empty_cells += 1
        
        # Table should have at least 30% non-empty cells
        if total_cells == 0 or (non_empty_cells / total_cells) < 0.3:
            return False
        
        # Check if it looks like a chapter header (false positive)
        # These often have very few non-empty cells and contain chapter text
        if len(data) <= 3 and non_empty_cells <= 3:
            # Check for chapter keywords
            all_text = ' '.join(' '.join(str(cell) for cell in row if cell) for row in data)
            if any(keyword in all_text for keyword in ['Chapter', 'Disorders of', 'Gastrointestinal Tract']):
                return False
        
        return True
    
    def extract_and_format_tables(self, page, format_type: str = "ascii") -> List[Dict]:
        """
        Extract tables from a page and format them
        
        Args:
            page: PyMuPDF page object
            format_type: Format type ("ascii", "markdown", "text")
            
        Returns:
            List of dictionaries with table data and formatted text
        """
        tables_info = []
        
        # Find tables on the page
        tables = page.find_tables()
        
        for table in tables:
            try:
                # Extract table data
                data = table.extract()
                
                # Skip invalid tables (false positives)
                if not self._is_valid_table(data):
                    logger.debug(f"Skipping invalid/false positive table")
                    continue
                
                # Get table bbox for position info
                bbox = table.bbox
                
                # Format based on type
                if format_type == "ascii":
                    formatted = self.format_as_ascii(data)
                elif format_type == "markdown":
                    formatted = self.format_as_markdown(data)
                else:
                    formatted = self.format_as_text_block(data)
                
                tables_info.append({
                    'bbox': bbox,
                    'data': data,
                    'formatted': formatted,
                    'rows': len(data),
                    'cols': len(data[0]) if data else 0
                })
                
            except Exception as e:
                logger.warning(f"Error processing table: {e}")
        
        return tables_info


def test_formatter():
    """Test the table formatter with sample data"""
    
    # Sample table data (from page 31)
    sample_data = [
        ['% DEHYDRATION', '<5%\nNil', '5-10%\nMild-moderate', '>10%\nSevere'],
        ['Thirst', 'Drinks normally,\nnot thirsty', 'Thirsty, drinks\neagerly', 'Drinks poorly'],
        ['FEEL', '', '', ''],
        ['Skin', 'Goes back\nquickly after\npinching', 'Goes back slowly\nafter pinching', 'Goes back very\nslowly after\npinching'],
        ['DECIDE', '', '', ''],
        ['', 'The patient\nhas no signs of\ndehydration', 'If the patient has\ntwo or more signs,\nincluding at least\none sign underlined,\nthere is some\ndehydration', 'If the patient\nhas two or more\nsigns, including\nat least one sign\nunderlined, there is\nsevere dehydration'],
        ['TREATMENT PLAN', 'Weigh patient\nand use\nTreatment Plan A', 'Weigh patient and\nuse Treatment\nPlan B', 'Weigh patient and\nuse Treatment\nPlan C']
    ]
    
    formatter = TableFormatter()
    
    # Test all three formatting options
    ascii_table = formatter.format_as_ascii(sample_data)
    markdown_table = formatter.format_as_markdown(sample_data)
    text_table = formatter.format_as_text_block(sample_data)
    
    # Tests completed successfully
    return ascii_table, markdown_table, text_table


if __name__ == "__main__":
    test_formatter()