import fitz  # PyMuPDF
import json
import re
from collections import defaultdict
from typing import Dict, List, Tuple
import pdfplumber

def analyze_with_pymupdf(pdf_path: str) -> Dict:
    """Analyze PDF structure using PyMuPDF"""
    doc = fitz.open(pdf_path)
    analysis = {
        "total_pages": len(doc),
        "metadata": doc.metadata,
        "toc": [],
        "page_analysis": [],
        "font_usage": defaultdict(int),
        "potential_sections": []
    }
    
    # Get table of contents if available
    toc = doc.get_toc()
    if toc:
        analysis["toc"] = toc
    
    # Sample pages for structure analysis (first 20, middle 10, last 10)
    sample_pages = list(range(min(20, len(doc))))
    if len(doc) > 40:
        middle = len(doc) // 2
        sample_pages.extend(range(middle - 5, middle + 5))
        sample_pages.extend(range(len(doc) - 10, len(doc)))
    
    for page_num in sample_pages:
        page = doc[page_num]
        text = page.get_text()
        
        # Analyze page structure
        page_info = {
            "page_num": page_num + 1,
            "text_length": len(text),
            "has_tables": False,
            "has_lists": False,
            "section_headers": [],
            "is_toc_page": False
        }
        
        # Check for table-like structures
        blocks = page.get_text("blocks")
        table_patterns = 0
        for block in blocks:
            if len(block) >= 5:
                block_text = block[4]
                # Check for table indicators
                if '\t' in block_text or '|' in block_text:
                    table_patterns += 1
                # Check for numbered lists (TOC pattern)
                if re.match(r'^\d+\.?\d*\s+\w+', block_text):
                    page_info["has_lists"] = True
        
        if table_patterns > 3:
            page_info["has_tables"] = True
        
        # Check if it's a TOC page
        toc_indicators = ["contents", "table of contents", "chapter", "section"]
        lower_text = text.lower()
        if any(indicator in lower_text[:500] for indicator in toc_indicators):
            if page_info["has_lists"] or re.findall(r'\d+\.\d+', text):
                page_info["is_toc_page"] = True
        
        # Extract potential headers (larger fonts)
        text_dict = page.get_text("dict")
        for block in text_dict["blocks"]:
            if "lines" in block:
                for line in block["lines"]:
                    for span in line["spans"]:
                        font_size = span["size"]
                        font_name = span["font"]
                        text_content = span["text"].strip()
                        
                        # Track font usage
                        analysis["font_usage"][f"{font_name}_{font_size:.1f}"] += 1
                        
                        # Identify headers (larger fonts)
                        if font_size > 12 and len(text_content) > 3:
                            page_info["section_headers"].append({
                                "text": text_content,
                                "font_size": font_size,
                                "font": font_name
                            })
        
        analysis["page_analysis"].append(page_info)
    
    doc.close()
    return analysis

def analyze_with_pdfplumber(pdf_path: str) -> Dict:
    """Analyze PDF structure using pdfplumber for better table detection"""
    analysis = {
        "tables_found": [],
        "structured_content": []
    }
    
    with pdfplumber.open(pdf_path) as pdf:
        # Sample pages for table detection
        sample_pages = list(range(min(20, len(pdf.pages))))
        if len(pdf.pages) > 40:
            middle = len(pdf.pages) // 2
            sample_pages.extend(range(middle - 5, middle + 5))
            sample_pages.extend(range(len(pdf.pages) - 10, len(pdf.pages)))
        
        for page_num in sample_pages:
            page = pdf.pages[page_num]
            
            # Extract tables
            tables = page.extract_tables()
            if tables:
                for i, table in enumerate(tables):
                    if table and len(table) > 0:
                        analysis["tables_found"].append({
                            "page": page_num + 1,
                            "table_index": i,
                            "rows": len(table),
                            "columns": len(table[0]) if table[0] else 0,
                            "sample_data": table[:3] if len(table) > 3 else table
                        })
            
            # Extract text with better structure
            text = page.extract_text()
            if text:
                # Look for specific patterns
                referral_pattern = r'R\d+\.\d+'
                drug_pattern = r'(?i)(tablet|capsule|injection|syrup|mg|ml|dose)'
                
                has_referral = bool(re.search(referral_pattern, text))
                has_drug_info = bool(re.search(drug_pattern, text))
                
                if has_referral or has_drug_info:
                    analysis["structured_content"].append({
                        "page": page_num + 1,
                        "has_referral_codes": has_referral,
                        "has_drug_info": has_drug_info,
                        "text_preview": text[:500]
                    })
    
    return analysis

def identify_document_structure(pymupdf_analysis: Dict, pdfplumber_analysis: Dict) -> Dict:
    """Identify the overall document structure"""
    structure = {
        "document_map": {
            "toc_pages": [],
            "chapter_starts": [],
            "table_pages": [],
            "referral_pages": [],
            "appendix_pages": []
        },
        "content_types": {
            "has_toc": False,
            "has_chapters": False,
            "has_referral_tables": False,
            "has_drug_tables": False,
            "has_appendices": False
        },
        "recommended_extraction_strategy": []
    }
    
    # Identify TOC pages
    for page_info in pymupdf_analysis["page_analysis"]:
        if page_info["is_toc_page"]:
            structure["document_map"]["toc_pages"].append(page_info["page_num"])
            structure["content_types"]["has_toc"] = True
    
    # Identify pages with tables
    for table_info in pdfplumber_analysis["tables_found"]:
        structure["document_map"]["table_pages"].append(table_info["page"])
        
        # Check if it's a referral table or drug table
        sample_text = str(table_info["sample_data"])
        if "R" in sample_text and any(char.isdigit() for char in sample_text):
            structure["document_map"]["referral_pages"].append(table_info["page"])
            structure["content_types"]["has_referral_tables"] = True
        
        if any(drug_term in sample_text.lower() for drug_term in ["mg", "ml", "tablet", "dose"]):
            structure["content_types"]["has_drug_tables"] = True
    
    # Identify chapter structure
    if pymupdf_analysis["toc"]:
        structure["content_types"]["has_chapters"] = True
        for item in pymupdf_analysis["toc"]:
            if item[0] == 1:  # Top-level entries
                structure["document_map"]["chapter_starts"].append(item[2])
    
    # Recommend extraction strategy
    if structure["content_types"]["has_toc"]:
        structure["recommended_extraction_strategy"].append(
            "Extract TOC first to create document navigation structure"
        )
    
    if structure["content_types"]["has_chapters"]:
        structure["recommended_extraction_strategy"].append(
            "Process document chapter by chapter for better organization"
        )
    
    if structure["content_types"]["has_referral_tables"]:
        structure["recommended_extraction_strategy"].append(
            "Use table extraction for referral codes and structured data"
        )
    
    if structure["content_types"]["has_drug_tables"]:
        structure["recommended_extraction_strategy"].append(
            "Extract drug information tables separately for structured storage"
        )
    
    # Identify font hierarchy for headers
    font_counts = pymupdf_analysis["font_usage"]
    sorted_fonts = sorted(font_counts.items(), key=lambda x: x[1], reverse=True)
    structure["font_hierarchy"] = sorted_fonts[:10]
    
    return structure

def main():
    pdf_path = "GHANA-STG-2017-1.pdf"
    
    print("Analyzing PDF structure...")
    print("=" * 60)
    
    # Analyze with PyMuPDF
    print("\n1. Analyzing with PyMuPDF...")
    pymupdf_analysis = analyze_with_pymupdf(pdf_path)
    
    print(f"Total pages: {pymupdf_analysis['total_pages']}")
    print(f"Metadata: {json.dumps(pymupdf_analysis['metadata'], indent=2)}")
    print(f"TOC entries found: {len(pymupdf_analysis['toc'])}")
    
    # Show sample TOC entries
    if pymupdf_analysis['toc']:
        print("\nSample TOC entries (first 10):")
        for entry in pymupdf_analysis['toc'][:10]:
            level, title, page = entry
            indent = "  " * (level - 1)
            print(f"{indent}{title} ... page {page}")
    
    # Analyze with pdfplumber
    print("\n2. Analyzing with pdfplumber...")
    pdfplumber_analysis = analyze_with_pdfplumber(pdf_path)
    
    print(f"Tables found: {len(pdfplumber_analysis['tables_found'])}")
    print(f"Pages with structured content: {len(pdfplumber_analysis['structured_content'])}")
    
    # Identify overall structure
    print("\n3. Identifying document structure...")
    structure = identify_document_structure(pymupdf_analysis, pdfplumber_analysis)
    
    print(f"\nDocument Map:")
    print(f"  TOC pages: {structure['document_map']['toc_pages'][:5]}...")
    print(f"  Chapter start pages: {structure['document_map']['chapter_starts'][:5]}...")
    print(f"  Pages with tables: {structure['document_map']['table_pages'][:5]}...")
    print(f"  Referral table pages: {structure['document_map']['referral_pages'][:5]}...")
    
    print(f"\nContent Types Detected:")
    for content_type, present in structure['content_types'].items():
        print(f"  {content_type}: {present}")
    
    print(f"\nTop Font Styles (for header identification):")
    for font, count in structure['font_hierarchy'][:5]:
        print(f"  {font}: {count} occurrences")
    
    print(f"\nRecommended Extraction Strategy:")
    for strategy in structure['recommended_extraction_strategy']:
        print(f"  - {strategy}")
    
    # Save detailed analysis
    with open('pdf_structure_analysis.json', 'w') as f:
        json.dump({
            "pymupdf_analysis": {
                "total_pages": pymupdf_analysis['total_pages'],
                "metadata": pymupdf_analysis['metadata'],
                "toc_entries": len(pymupdf_analysis['toc']),
                "sample_toc": pymupdf_analysis['toc'][:20],
                "page_samples": pymupdf_analysis['page_analysis'][:10]
            },
            "pdfplumber_analysis": {
                "tables_found": pdfplumber_analysis['tables_found'][:10],
                "structured_content_samples": pdfplumber_analysis['structured_content'][:10]
            },
            "document_structure": structure
        }, f, indent=2)
    
    print("\n" + "=" * 60)
    print("Analysis complete! Detailed results saved to 'pdf_structure_analysis.json'")
    
    return structure

if __name__ == "__main__":
    structure = main()