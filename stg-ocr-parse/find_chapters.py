#!/usr/bin/env python3
"""
Script to find all chapter boundaries in the Ghana STG document
"""
import json
import re

def find_chapters():
    """Find all chapter titles and their starting page numbers"""
    
    with open('pymupdf_output/extracted_content.json', 'r') as f:
        data = json.load(f)
    
    chapters = {}
    
    # Known chapter patterns to search for
    chapter_patterns = [
        r'^Disorders of the Gastrointestinal Tract',
        r'^Disorders of the Liver',
        r'^Nutritional Disorders',
        r'^Haematological Disorders', 
        r'^Immunisable Diseases',
        r'^Problems of the Newborn',
        r'^Disorders of the Cardiovascular System',
        r'^Disorders of the Respiratory System',
        r'^Disorders of the Central Nervous System',
        r'^Psychiatric Disorders',
        r'^Disorders of the Skin',
        r'^Endocrine and Metabolic Disorders',
        r'^Obstetric Care and Obstetric Disorders',
        r'^Gynaecological Disorders',
        r'^Disorders of the Kidney and Genitourinary System',
        r'^Sexually Transmitted Infections',
        r'^HIV Infections and AIDS',
        r'^Infectious Diseases and Infestations',
        r'^Eye Disorders',
        r'^Ear, Nose and Throat Disorders',
        r'^Oral and Dental Conditions',
        r'^Disorders Of The Musculoskeletal System',
        r'^Trauma And Injuries'
    ]
    
    # Also look for simpler patterns that might appear
    simple_patterns = [
        r'^Cardiovascular System',
        r'^Respiratory System'
    ]
    
    all_patterns = chapter_patterns + simple_patterns
    
    for page_num, page_data in data.items():
        if isinstance(page_data, dict) and 'full_text' in page_data:
            text = page_data['full_text'].strip()
            headers = page_data.get('headers', [])
            tables = page_data.get('tables', [])
            
            # Check if text starts with any chapter pattern
            for pattern in all_patterns:
                if re.match(pattern, text, re.IGNORECASE):
                    chapter_title = re.match(pattern, text, re.IGNORECASE).group()
                    chapters[int(page_num)] = chapter_title
                    print(f"Found chapter on page {page_num}: {chapter_title}")
                    break
            
            # Check tables for chapter titles (like on page 29)
            for table in tables:
                for row in table.get('data', []):
                    for cell in row:
                        if cell and isinstance(cell, str):
                            for pattern in all_patterns:
                                if re.match(pattern, cell, re.IGNORECASE):
                                    chapter_title = cell.replace('\n', ' ')
                                    if int(page_num) not in chapters:
                                        chapters[int(page_num)] = chapter_title
                                        print(f"Found chapter in table on page {page_num}: {chapter_title}")
                                    break
            
            # Also check headers with font_size >= 13 for chapter titles
            for header in headers:
                if header.get('font_size', 0) >= 13 and header.get('is_bold'):
                    header_text = header.get('text', '').strip()
                    
                    # Check if header matches any pattern
                    for pattern in all_patterns:
                        if re.match(pattern, header_text, re.IGNORECASE):
                            chapter_title = header_text
                            if int(page_num) not in chapters:  # Don't overwrite if already found
                                chapters[int(page_num)] = chapter_title
                                print(f"Found chapter header on page {page_num}: {chapter_title}")
                            break
    
    # Sort by page number
    sorted_chapters = dict(sorted(chapters.items()))
    
    print("\n" + "="*80)
    print("COMPLETE CHAPTER LIST:")
    print("="*80)
    
    chapter_dict = {}
    for i, (page, title) in enumerate(sorted_chapters.items(), 1):
        print(f"{page:3d}: Chapter {i:2d} - {title}")
        chapter_dict[page] = {
            'chapter_number': i,
            'title': title
        }
    
    # Generate Python dictionary format
    print("\n" + "="*80)
    print("PYTHON DICTIONARY FORMAT:")
    print("="*80)
    print("chapters = {")
    for page, info in chapter_dict.items():
        print(f"    {page}: {{'chapter_number': {info['chapter_number']}, 'title': '{info['title']}'}},")
    print("}")
    
    return chapter_dict

if __name__ == "__main__":
    find_chapters()