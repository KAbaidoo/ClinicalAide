#!/usr/bin/env python3
"""
Medical-Focused OCR Extractor
Refined patterns for medical content extraction
"""

import fitz
import sqlite3
import re
import json
import logging
from typing import List, Dict, Optional
from dataclasses import dataclass

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class MedicalCondition:
    name: str
    icd_code: str = ""
    clinical_features: str = ""
    investigations: str = ""
    treatment: str = ""
    page_number: int = 0
    confidence: float = 0.0

@dataclass  
class MedicalTreatment:
    condition_name: str
    first_line: str = ""
    second_line: str = ""
    dosage: str = ""
    duration: str = ""
    page_number: int = 0

class MedicalOCRExtractor:
    """Enhanced extractor focused on medical content"""
    
    def __init__(self, pdf_path: str, db_path: str = "stg_medical_ocr.db"):
        self.pdf_path = pdf_path
        self.db_path = db_path
        
        # Refined medical condition patterns
        self.condition_patterns = [
            # Pattern for conditions with ICD codes
            r'^([A-Z][a-zA-Z\s\-]+?)\s*\((?:ICD[:\s-]*)?([A-Z]\d{2}(?:\.\d+)?)\)',
            # Pattern for medical conditions (not organizations)
            r'^((?:Acute|Chronic|Severe|Mild|Primary|Secondary)\s+[A-Za-z\s]+)$',
            # Common disease patterns
            r'^([A-Z][a-z]+(?:itis|osis|emia|pathy|trophy|plasia|oma))\s*',
            # Infection patterns
            r'^([A-Z][a-z]+\s+(?:infection|disease|syndrome|disorder))\s*',
            # Pain/symptom patterns
            r'^([A-Z][a-z]+\s+(?:pain|fever|cough|diarr?hoea))\s*',
        ]
        
        # Medical section identifiers
        self.medical_sections = {
            'clinical_features': [
                'clinical features', 'signs and symptoms', 'clinical presentation',
                'presenting features', 'clinical manifestations'
            ],
            'investigations': [
                'investigations', 'diagnostic tests', 'laboratory tests',
                'diagnostic procedures', 'tests required'
            ],
            'treatment': [
                'treatment', 'management', 'therapy', 'drug therapy',
                'pharmacological treatment', 'medication'
            ],
            'complications': [
                'complications', 'sequelae', 'adverse outcomes'
            ]
        }
        
        # Treatment patterns
        self.treatment_patterns = [
            r'First[- ]line[:\s]+([^.\n]+)',
            r'Second[- ]line[:\s]+([^.\n]+)', 
            r'Alternative[:\s]+([^.\n]+)',
            r'Drug[:\s]+([A-Z][a-z]+(?:\s+\d+\s*mg)?)',
            r'Dose[:\s]+([^.\n]+)',
            r'Duration[:\s]+([^.\n]+)'
        ]
        
        # Medication patterns with dosing
        self.medication_patterns = [
            r'([A-Z][a-z]+)\s+(\d+(?:\.\d+)?)\s*(mg|g|ml|IU|mcg|units?)(?:\s+(\w+))?',
            r'([A-Z][a-z]+)\s+(?:tablets?|capsules?|injection|syrup)\s+(\d+(?:\.\d+)?)\s*(mg|ml)',
        ]
        
        # Skip patterns (non-medical content)
        self.skip_patterns = [
            r'Department of',
            r'Ministry of', 
            r'University of',
            r'World Health',
            r'Ghana National',
            r'Table of Contents',
            r'Chapter \d+',
            r'Page \d+',
            r'\d{4}',  # Years
        ]
        
        self.stats = {
            "pages_processed": 0,
            "conditions_found": 0,
            "treatments_found": 0,
            "medications_found": 0
        }
    
    def extract_medical_content(self, start_page: int = 30, max_pages: int = 50):
        """Extract medical content from specified page range"""
        
        logger.info(f"Extracting medical content from pages {start_page} to {start_page + max_pages}")
        
        doc = fitz.open(self.pdf_path)
        total_pages = len(doc)
        
        if start_page >= total_pages:
            logger.error(f"Start page {start_page} exceeds document length {total_pages}")
            return
        
        # Create database
        self._create_database()
        
        end_page = min(start_page + max_pages, total_pages)
        
        conditions = []
        treatments = []
        medications = []
        
        current_condition = None
        
        for page_num in range(start_page - 1, end_page):  # Convert to 0-indexed
            logger.info(f"Processing page {page_num + 1}")
            
            page = doc[page_num]
            text = page.get_text()
            
            # Look for medical conditions
            page_conditions = self._extract_conditions_from_page(text, page_num + 1)
            conditions.extend(page_conditions)
            
            # Set current condition context
            if page_conditions:
                current_condition = page_conditions[-1]  # Most recent condition
            
            # Look for treatments in context of current condition
            if current_condition:
                page_treatments = self._extract_treatments_from_page(text, current_condition.name, page_num + 1)
                treatments.extend(page_treatments)
            
            # Look for medications
            page_medications = self._extract_medications_from_page(text, page_num + 1)
            medications.extend(page_medications)
            
            self.stats["pages_processed"] += 1
        
        doc.close()
        
        # Store in database
        self._store_medical_data(conditions, treatments, medications)
        
        # Update stats
        self.stats["conditions_found"] = len(conditions)
        self.stats["treatments_found"] = len(treatments)
        self.stats["medications_found"] = len(medications)
        
        # Print results
        self._print_results(conditions, treatments, medications)
    
    def _extract_conditions_from_page(self, text: str, page_num: int) -> List[MedicalCondition]:
        """Extract medical conditions from page text"""
        
        conditions = []
        lines = text.split('\n')
        
        for line in lines:
            line = line.strip()
            
            if len(line) < 5 or len(line) > 100:  # Skip very short/long lines
                continue
            
            # Skip non-medical content
            if any(re.search(pattern, line) for pattern in self.skip_patterns):
                continue
            
            # Check medical condition patterns
            for pattern in self.condition_patterns:
                match = re.search(pattern, line)
                if match:
                    condition_name = match.group(1).strip()
                    
                    # Additional validation
                    if not self._is_valid_medical_condition(condition_name):
                        continue
                    
                    condition = MedicalCondition(
                        name=condition_name,
                        page_number=page_num
                    )
                    
                    # Extract ICD code if present
                    if len(match.groups()) > 1:
                        condition.icd_code = match.group(2)
                    
                    # Look for clinical features in surrounding text
                    condition.clinical_features = self._extract_clinical_features(text, condition_name)
                    condition.investigations = self._extract_section_content(text, 'investigations')
                    condition.treatment = self._extract_section_content(text, 'treatment')
                    
                    conditions.append(condition)
                    logger.info(f"Found condition: {condition_name} on page {page_num}")
                    break
        
        return conditions
    
    def _extract_treatments_from_page(self, text: str, condition_name: str, page_num: int) -> List[MedicalTreatment]:
        """Extract treatment information for a condition"""
        
        treatments = []
        
        # Look for treatment patterns
        for pattern in self.treatment_patterns:
            matches = re.finditer(pattern, text, re.IGNORECASE)
            for match in matches:
                treatment_text = match.group(1).strip()
                
                if len(treatment_text) > 5:  # Minimum meaningful treatment
                    treatment = MedicalTreatment(
                        condition_name=condition_name,
                        page_number=page_num
                    )
                    
                    # Classify treatment type based on pattern
                    pattern_lower = pattern.lower()
                    if 'first' in pattern_lower:
                        treatment.first_line = treatment_text
                    elif 'second' in pattern_lower or 'alternative' in pattern_lower:
                        treatment.second_line = treatment_text
                    elif 'dose' in pattern_lower:
                        treatment.dosage = treatment_text
                    elif 'duration' in pattern_lower:
                        treatment.duration = treatment_text
                    else:
                        treatment.first_line = treatment_text  # Default
                    
                    treatments.append(treatment)
        
        return treatments
    
    def _extract_medications_from_page(self, text: str, page_num: int) -> List[Dict]:
        """Extract medications with dosing information"""
        
        medications = []
        
        for pattern in self.medication_patterns:
            matches = re.finditer(pattern, text)
            for match in matches:
                med_name = match.group(1).strip()
                
                # Validate medication name
                if not self._is_valid_medication_name(med_name):
                    continue
                
                medication = {
                    'name': med_name,
                    'page_number': page_num
                }
                
                # Extract dosage information
                if len(match.groups()) >= 3:
                    dose = match.group(2)
                    unit = match.group(3)
                    medication['strength'] = f"{dose}{unit}"
                
                # Extract route if available
                if len(match.groups()) >= 4 and match.group(4):
                    medication['route'] = match.group(4)
                
                medications.append(medication)
        
        return medications
    
    def _is_valid_medical_condition(self, name: str) -> bool:
        """Validate if text represents a medical condition"""
        
        # Length check
        if len(name) < 5 or len(name) > 60:
            return False
        
        # Skip organizational names
        if any(skip in name for skip in ['Department', 'Ministry', 'University', 'Ghana', 'WHO']):
            return False
        
        # Skip numbers and years
        if re.search(r'^\d+', name) or re.search(r'\b\d{4}\b', name):
            return False
        
        # Medical condition indicators
        medical_indicators = [
            'infection', 'disease', 'syndrome', 'disorder', 'pain', 'fever',
            'itis', 'osis', 'emia', 'pathy', 'trophy', 'plasia', 'oma',
            'acute', 'chronic', 'severe', 'mild', 'primary', 'secondary',
            'malaria', 'pneumonia', 'diarrhea', 'hypertension', 'diabetes'
        ]
        
        name_lower = name.lower()
        if any(indicator in name_lower for indicator in medical_indicators):
            return True
        
        # Check if it's a proper medical term (starts with capital, has medical structure)
        if name[0].isupper() and len(name.split()) <= 4:
            return True
        
        return False
    
    def _is_valid_medication_name(self, name: str) -> bool:
        """Validate medication name"""
        
        if len(name) < 3 or len(name) > 30:
            return False
        
        # Must start with capital letter
        if not name[0].isupper():
            return False
        
        # Skip common false positives
        skip_words = ['Table', 'Page', 'Chapter', 'Section', 'And', 'The', 'For', 'With']
        if any(skip in name for skip in skip_words):
            return False
        
        return True
    
    def _extract_clinical_features(self, text: str, condition_name: str) -> str:
        """Extract clinical features section for a condition"""
        
        # Look for clinical features section
        cf_patterns = [
            r'clinical features[:\s]+(.*?)(?:investigations|treatment|complications|$)',
            r'signs and symptoms[:\s]+(.*?)(?:investigations|treatment|complications|$)',
            r'presentation[:\s]+(.*?)(?:investigations|treatment|complications|$)'
        ]
        
        for pattern in cf_patterns:
            match = re.search(pattern, text, re.IGNORECASE | re.DOTALL)
            if match:
                features = match.group(1).strip()
                # Limit length and clean up
                features = re.sub(r'\s+', ' ', features)  # Normalize whitespace
                return features[:500] if len(features) > 500 else features
        
        return ""
    
    def _extract_section_content(self, text: str, section_type: str) -> str:
        """Extract content for a specific medical section"""
        
        if section_type not in self.medical_sections:
            return ""
        
        keywords = self.medical_sections[section_type]
        
        for keyword in keywords:
            # Create pattern to extract content after keyword
            pattern = f'{keyword}[:\s]+(.*?)(?:{"$|".join(sum(self.medical_sections.values(), []))}|$)'
            match = re.search(pattern, text, re.IGNORECASE | re.DOTALL)
            
            if match:
                content = match.group(1).strip()
                content = re.sub(r'\s+', ' ', content)  # Normalize whitespace
                return content[:300] if len(content) > 300 else content
        
        return ""
    
    def _create_database(self):
        """Create database for medical content"""
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        schema_sql = """
        -- Medical conditions
        CREATE TABLE IF NOT EXISTS conditions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            icd10_code TEXT,
            clinical_features TEXT,
            investigations TEXT,
            treatment TEXT,
            page_number INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        
        -- Treatments
        CREATE TABLE IF NOT EXISTS treatments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            condition_name TEXT NOT NULL,
            first_line TEXT,
            second_line TEXT,
            dosage TEXT,
            duration TEXT,
            page_number INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        
        -- Medications
        CREATE TABLE IF NOT EXISTS medications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            generic_name TEXT NOT NULL,
            strength TEXT,
            route TEXT,
            page_number INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        
        -- Search index
        CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts5(
            content_type,
            content_id,
            title,
            searchable_text
        );
        
        -- Indexes
        CREATE INDEX IF NOT EXISTS idx_conditions_name ON conditions(name);
        CREATE INDEX IF NOT EXISTS idx_treatments_condition ON treatments(condition_name);
        CREATE INDEX IF NOT EXISTS idx_medications_name ON medications(generic_name);
        """
        
        for statement in schema_sql.split(';'):
            if statement.strip():
                cursor.execute(statement)
        
        conn.commit()
        conn.close()
    
    def _store_medical_data(self, conditions: List[MedicalCondition], 
                          treatments: List[MedicalTreatment], medications: List[Dict]):
        """Store extracted medical data in database"""
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # Store conditions
        for condition in conditions:
            try:
                cursor.execute("""
                    INSERT OR IGNORE INTO conditions 
                    (name, icd10_code, clinical_features, investigations, treatment, page_number)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (condition.name, condition.icd_code, condition.clinical_features,
                      condition.investigations, condition.treatment, condition.page_number))
                
                condition_id = cursor.lastrowid or cursor.execute(
                    "SELECT id FROM conditions WHERE name = ?", (condition.name,)
                ).fetchone()[0]
                
                # Add to search index
                searchable_text = f"{condition.name} {condition.clinical_features} {condition.treatment}"
                cursor.execute("""
                    INSERT INTO search_index (content_type, content_id, title, searchable_text)
                    VALUES (?, ?, ?, ?)
                """, ('condition', condition_id, condition.name, searchable_text))
                
            except sqlite3.Error as e:
                logger.warning(f"Error storing condition {condition.name}: {e}")
        
        # Store treatments
        for treatment in treatments:
            try:
                cursor.execute("""
                    INSERT INTO treatments 
                    (condition_name, first_line, second_line, dosage, duration, page_number)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (treatment.condition_name, treatment.first_line, treatment.second_line,
                      treatment.dosage, treatment.duration, treatment.page_number))
            except sqlite3.Error as e:
                logger.warning(f"Error storing treatment: {e}")
        
        # Store medications
        for medication in medications:
            try:
                cursor.execute("""
                    INSERT INTO medications (generic_name, strength, route, page_number)
                    VALUES (?, ?, ?, ?)
                """, (medication['name'], medication.get('strength', ''),
                      medication.get('route', ''), medication['page_number']))
                
                medication_id = cursor.lastrowid
                
                # Add to search index
                searchable_text = f"{medication['name']} {medication.get('strength', '')}"
                cursor.execute("""
                    INSERT INTO search_index (content_type, content_id, title, searchable_text)
                    VALUES (?, ?, ?, ?)
                """, ('medication', medication_id, medication['name'], searchable_text))
                
            except sqlite3.Error as e:
                logger.warning(f"Error storing medication {medication['name']}: {e}")
        
        conn.commit()
        conn.close()
        
        logger.info(f"Stored {len(conditions)} conditions, {len(treatments)} treatments, {len(medications)} medications")
    
    def _print_results(self, conditions, treatments, medications):
        """Print extraction results"""
        
        print("\n" + "="*60)
        print("MEDICAL OCR EXTRACTION RESULTS")
        print("="*60)
        print(f"Pages processed: {self.stats['pages_processed']}")
        print(f"Conditions found: {self.stats['conditions_found']}")
        print(f"Treatments found: {self.stats['treatments_found']}")
        print(f"Medications found: {self.stats['medications_found']}")
        
        if conditions:
            print(f"\nSample Conditions:")
            for condition in conditions[:5]:
                icd = f" ({condition.icd_code})" if condition.icd_code else ""
                print(f"  • {condition.name}{icd} - Page {condition.page_number}")
                if condition.clinical_features:
                    print(f"    Features: {condition.clinical_features[:100]}...")
        
        if treatments:
            print(f"\nSample Treatments:")
            for treatment in treatments[:3]:
                print(f"  • {treatment.condition_name} - Page {treatment.page_number}")
                if treatment.first_line:
                    print(f"    First-line: {treatment.first_line}")
        
        if medications:
            print(f"\nSample Medications:")
            for medication in medications[:5]:
                strength = f" {medication.get('strength', '')}" if medication.get('strength') else ""
                print(f"  • {medication['name']}{strength} - Page {medication['page_number']}")
        
        print(f"\nDatabase: {self.db_path}")
        print("="*60)

def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="Medical-focused OCR extraction")
    parser.add_argument("--pdf", default="GHANA-STG-2017-1.pdf", help="PDF file")
    parser.add_argument("--start", type=int, default=30, help="Start page (where medical content begins)")
    parser.add_argument("--pages", type=int, default=50, help="Number of pages to process")
    parser.add_argument("--db", default="stg_medical_ocr.db", help="Output database")
    
    args = parser.parse_args()
    
    extractor = MedicalOCRExtractor(args.pdf, args.db)
    extractor.extract_medical_content(args.start, args.pages)

if __name__ == "__main__":
    main()