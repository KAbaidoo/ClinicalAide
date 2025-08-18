#!/usr/bin/env python3
"""
Simple script to analyze the Ghana STG PDF structure
"""

import sys
try:
    import PyPDF2
except ImportError:
    print("PyPDF2 not installed. Install with: pip3 install PyPDF2")
    sys.exit(1)

def analyze_pdf(pdf_path):
    with open(pdf_path, 'rb') as file:
        reader = PyPDF2.PdfReader(file)
        
        print(f"=== Ghana STG PDF Analysis ===")
        print(f"Total pages: {len(reader.pages)}")
        print(f"PDF metadata: {reader.metadata}")
        
        # Extract first page
        print("\n=== First Page Content (500 chars) ===")
        first_page = reader.pages[0]
        text = first_page.extract_text()
        print(text[:500])
        
        # Look for table of contents
        print("\n=== Searching for Table of Contents ===")
        for i in range(min(20, len(reader.pages))):
            page_text = reader.pages[i].extract_text()
            if "TABLE OF CONTENTS" in page_text.upper() or "CONTENTS" in page_text.upper():
                print(f"Found TOC on page {i+1}")
                lines = page_text.split('\n')[:20]
                for line in lines:
                    if line.strip():
                        print(f"  {line[:100]}")
                break
        
        # Look for chapter patterns
        print("\n=== Searching for Chapter Patterns ===")
        sample_pages = [30, 50, 70, 100, 150, 200]
        for page_num in sample_pages:
            if page_num >= len(reader.pages):
                break
            
            page_text = reader.pages[page_num].extract_text()
            lines = page_text.split('\n')
            
            for line in lines:
                if 'CHAPTER' in line.upper() or line.strip().startswith(('1.', '2.', '3.', '4.', '5.')):
                    print(f"Page {page_num+1}: {line[:100]}")
                    break
        
        # Extract medical content sample
        print("\n=== Sample Medical Content (Page 100) ===")
        if len(reader.pages) > 100:
            page_100 = reader.pages[99].extract_text()
            
            # Look for medical terms
            medical_terms = ['dosage', 'dose', 'mg', 'ml', 'tablet', 'capsule',
                           'treatment', 'diagnosis', 'symptoms', 'contraindication']
            
            print("Medical terms found:")
            for term in medical_terms:
                if term.lower() in page_100.lower():
                    print(f"  ✓ {term}")
            
            # Find numbered sections
            lines = page_100.split('\n')
            numbered = [line for line in lines if line.strip() and line.strip()[0].isdigit()]
            if numbered:
                print("\nNumbered sections:")
                for line in numbered[:5]:
                    print(f"  {line[:80]}")

if __name__ == "__main__":
    pdf_path = "/Users/kobby/Desktop/MOH-STG/GHANA-STG-2017-1.pdf"
    analyze_pdf(pdf_path)