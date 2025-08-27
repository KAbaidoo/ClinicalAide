import fitz  # PyMuPDF
import json
import re
from pathlib import Path

def analyze_with_pymupdf(pdf_path, sample_size=100):
    """Analyze PDF structure using PyMuPDF"""
    
    doc = fitz.open(pdf_path)
    total_pages = len(doc)
    
    print(f"Document has {total_pages} pages")
    print(f"Analyzing first {min(sample_size, total_pages)} pages...\n")
    
    analysis = {
        'total_pages': total_pages,
        'page_details': [],
        'toc_pages': [],
        'chapter_starts': {},
        'main_content_start': None,
        'front_matter_pages': [],
        'structure_summary': {}
    }
    
    # Patterns for content detection
    patterns = {
        'toc': r'(table of contents|contents\s*$|chapter\s+\d+.*\.{3,}|\d+\.\d+.*\.{3,})',
        'list_tables': r'(list of tables|^table\s+\d+[:\.])',
        'list_figures': r'(list of figures|^figure\s+\d+[:\.])',
        'chapter': r'^CHAPTER\s+(\d+)[:\s\.]?\s*(.+)?',
        'section': r'^(\d+\.\d+\.?\s+)([A-Z].+)',
        'introduction': r'^(introduction|preface|foreword|acknowledgement)s?\s*$',
        'index': r'^(index|indices)\s*$',
        'appendix': r'^appendix\s+[a-z0-9]',
        'medical_condition': r'^(\d+\.)\s+([A-Z][a-z]+.+?)$'  # Pattern for numbered medical conditions
    }
    
    for page_num in range(min(sample_size, total_pages)):
        page = doc[page_num]
        text = page.get_text()
        
        # Clean text for analysis
        text_lines = text.strip().split('\n')
        first_lines = '\n'.join(text_lines[:10]) if text_lines else ''
        
        page_info = {
            'page': page_num + 1,  # 1-indexed
            'text_preview': text[:500],
            'first_lines': first_lines,
            'content_type': [],
            'has_substantial_content': len(text.strip()) > 200
        }
        
        # Check for TOC
        if re.search(patterns['toc'], text[:1000], re.IGNORECASE | re.MULTILINE):
            page_info['content_type'].append('toc')
            analysis['toc_pages'].append(page_num + 1)
            analysis['front_matter_pages'].append(page_num + 1)
        
        # Check for chapter starts
        chapter_match = re.search(patterns['chapter'], text[:500], re.IGNORECASE | re.MULTILINE)
        if chapter_match:
            chapter_num = chapter_match.group(1)
            chapter_title = chapter_match.group(2) if chapter_match.group(2) else ''
            
            # Only count as chapter start if it's not in TOC
            if 'toc' not in page_info['content_type']:
                page_info['content_type'].append('chapter_start')
                analysis['chapter_starts'][chapter_num] = {
                    'page': page_num + 1,
                    'title': chapter_title.strip()
                }
                
                # First real chapter marks start of main content
                if not analysis['main_content_start']:
                    analysis['main_content_start'] = page_num + 1
        
        # Check for medical conditions (numbered items that look like conditions)
        conditions = re.findall(patterns['medical_condition'], text, re.MULTILINE)
        if conditions and len(conditions) > 2:  # Multiple conditions on page
            page_info['content_type'].append('medical_conditions')
            page_info['condition_count'] = len(conditions)
        
        # Check for other front matter
        if re.search(patterns['introduction'], first_lines, re.IGNORECASE | re.MULTILINE):
            page_info['content_type'].append('introduction')
            if not analysis['main_content_start']:  # Still in front matter
                analysis['front_matter_pages'].append(page_num + 1)
        
        # Check for lists
        if re.search(patterns['list_tables'], text[:500], re.IGNORECASE | re.MULTILINE):
            page_info['content_type'].append('list_of_tables')
            analysis['front_matter_pages'].append(page_num + 1)
        
        if re.search(patterns['list_figures'], text[:500], re.IGNORECASE | re.MULTILINE):
            page_info['content_type'].append('list_of_figures')
            analysis['front_matter_pages'].append(page_num + 1)
        
        # Store page info
        analysis['page_details'].append(page_info)
        
        # Print progress for key pages
        if page_info['content_type']:
            print(f"Page {page_num + 1}: {', '.join(page_info['content_type'])}")
            if 'chapter_start' in page_info['content_type']:
                print(f"  → Chapter {chapter_num}: {chapter_title[:50]}")
    
    # Compile summary
    analysis['structure_summary'] = {
        'front_matter_end': max(analysis['front_matter_pages']) if analysis['front_matter_pages'] else None,
        'main_content_start': analysis['main_content_start'],
        'toc_span': f"{min(analysis['toc_pages'])}-{max(analysis['toc_pages'])}" if analysis['toc_pages'] else None,
        'chapters_found': len(analysis['chapter_starts'])
    }
    
    doc.close()
    return analysis

def check_specific_pages(pdf_path, page_numbers):
    """Check specific pages for content"""
    doc = fitz.open(pdf_path)
    
    for page_num in page_numbers:
        if page_num <= len(doc):
            page = doc[page_num - 1]  # Convert to 0-indexed
            text = page.get_text()
            
            # Display page sample for analysis
            if text:
                print(f"\nPage {page_num}: {len(text)} characters")
                print(f"First 200 chars: {text[:200].strip()}...")
    
    doc.close()

def main():
    pdf_path = "GHANA-STG-2017-1.pdf"
    
    print("\nAnalyzing Ghana STG document structure...")
    
    # First, analyze structure
    analysis = analyze_with_pymupdf(pdf_path, sample_size=60)
    
    # Save detailed analysis
    with open('pymupdf_analysis.json', 'w') as f:
        json.dump(analysis, f, indent=2)
    
    print("\n" + "="*60)
    print("DOCUMENT STRUCTURE SUMMARY")
    print("="*60)
    print(f"Total pages: {analysis['total_pages']}")
    print(f"TOC pages: {analysis['structure_summary']['toc_span']}")
    print(f"Front matter ends: Page {analysis['structure_summary']['front_matter_end']}")
    print(f"Main content starts: Page {analysis['structure_summary']['main_content_start']}")
    print(f"Chapters found: {analysis['structure_summary']['chapters_found']}")
    
    print("\nChapters detected:")
    for ch_num, ch_info in sorted(analysis['chapter_starts'].items())[:10]:
        print(f"  Chapter {ch_num}: Page {ch_info['page']} - {ch_info['title'][:50]}")
    
    # Check pages 27-32 specifically as user mentioned page 29
    print("\n" + "="*60)
    print("SAMPLE PAGES (27-32)")
    print("="*60)
    check_specific_pages(pdf_path, [27, 28, 29, 30, 31, 32])

if __name__ == "__main__":
    main()