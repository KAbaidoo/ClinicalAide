# TDD Database Implementation Plan

## Status: COMPLETE ✅
**Completion Date**: August 17, 2025  
**Total Tests**: 81 (Planned: ~84)  
**Pass Rate**: 100%

## Overview
This document outlines the Test-Driven Development (TDD) approach for implementing the Room database for the ClinicalAide application. We successfully followed the RED-GREEN-REFACTOR cycle to build a robust database layer.

## Testing Philosophy
- **Red**: Write failing tests that define expected behavior
- **Green**: Implement minimal code to make tests pass
- **Refactor**: Improve code quality while keeping tests green

## Test Categories

### 1. Schema Validation Tests
Tests to ensure the database schema is correctly implemented.

#### 1.1 Table Creation Tests
- Verify all 7 required tables exist:
  - `stg_chapters`
  - `stg_conditions`
  - `stg_content_blocks`
  - `stg_embeddings`
  - `stg_medications`
  - `stg_cross_references`
  - `stg_search_cache`

#### 1.2 Column Validation Tests
For each table, verify:
- All required columns are present
- Column names match specification exactly
- No extra columns exist

**StgChapter Columns:**
- id (PRIMARY KEY, AUTOINCREMENT)
- chapterNumber (INTEGER, NOT NULL)
- chapterTitle (TEXT, NOT NULL)
- startPage (INTEGER, NOT NULL)
- endPage (INTEGER, NOT NULL)
- description (TEXT, NULLABLE)

**StgCondition Columns:**
- id (PRIMARY KEY, AUTOINCREMENT)
- chapterId (INTEGER, NOT NULL, FOREIGN KEY)
- conditionNumber (INTEGER, NOT NULL)
- conditionName (TEXT, NOT NULL)
- startPage (INTEGER, NOT NULL)
- endPage (INTEGER, NOT NULL)
- keywords (TEXT, NOT NULL)

**StgContentBlock Columns:**
- id (PRIMARY KEY, AUTOINCREMENT)
- conditionId (INTEGER, NOT NULL, FOREIGN KEY)
- blockType (TEXT, NOT NULL)
- content (TEXT, NOT NULL)
- pageNumber (INTEGER, NOT NULL)
- orderInCondition (INTEGER, NOT NULL)
- clinicalContext (TEXT, NOT NULL, DEFAULT "general")
- severityLevel (TEXT, NULLABLE)
- evidenceLevel (TEXT, NULLABLE)
- keywords (TEXT, NOT NULL)
- relatedBlockIds (TEXT, NOT NULL, DEFAULT "[]")
- createdAt (INTEGER, NOT NULL)
- updatedAt (INTEGER, NOT NULL)

**StgEmbedding Columns:**
- id (PRIMARY KEY, AUTOINCREMENT)
- contentBlockId (INTEGER, NOT NULL, FOREIGN KEY)
- embedding (TEXT, NOT NULL)
- embeddingModel (TEXT, NOT NULL)
- embeddingDimensions (INTEGER, NOT NULL, DEFAULT 768)
- createdAt (INTEGER, NOT NULL)

**StgMedication Columns:**
- id (PRIMARY KEY, AUTOINCREMENT)
- conditionId (INTEGER, NOT NULL, FOREIGN KEY)
- medicationName (TEXT, NOT NULL)
- dosage (TEXT, NOT NULL)
- frequency (TEXT, NOT NULL)
- duration (TEXT, NOT NULL)
- route (TEXT, NOT NULL)
- ageGroup (TEXT, NOT NULL)
- weightBased (INTEGER, NOT NULL, DEFAULT 0)
- contraindications (TEXT, NULLABLE)
- sideEffects (TEXT, NULLABLE)
- evidenceLevel (TEXT, NULLABLE)
- pageNumber (INTEGER, NOT NULL)

**StgCrossReference Columns:**
- id (PRIMARY KEY, AUTOINCREMENT)
- fromConditionId (INTEGER, NOT NULL)
- toConditionId (INTEGER, NOT NULL)
- referenceType (TEXT, NOT NULL)
- description (TEXT, NULLABLE)

**StgSearchCache Columns:**
- queryHash (TEXT, PRIMARY KEY)
- results (TEXT, NOT NULL)
- timestamp (INTEGER, NOT NULL)
- hitCount (INTEGER, NOT NULL, DEFAULT 1)

#### 1.3 Data Type Validation Tests
- Verify INTEGER columns accept numeric values
- Verify TEXT columns accept string values
- Verify nullable constraints are enforced
- Verify NOT NULL constraints are enforced
- Test boundary values for numeric fields

#### 1.4 Default Value Tests
- Test `clinicalContext` defaults to "general"
- Test `relatedBlockIds` defaults to "[]"
- Test `embeddingDimensions` defaults to 768
- Test `weightBased` defaults to false (0)
- Test `hitCount` defaults to 1
- Test timestamp fields default to current time

### 2. Foreign Key Constraint Tests

#### 2.1 Relationship Tests
- `StgCondition.chapterId` → `StgChapter.id`
- `StgContentBlock.conditionId` → `StgCondition.id`
- `StgEmbedding.contentBlockId` → `StgContentBlock.id`
- `StgMedication.conditionId` → `StgCondition.id`

#### 2.2 CASCADE Delete Tests
- Deleting a chapter should delete all its conditions
- Deleting a condition should delete all its content blocks
- Deleting a content block should delete its embeddings
- Deleting a condition should delete all its medications

#### 2.3 Foreign Key Violation Tests
- Cannot insert condition with non-existent chapterId
- Cannot insert content block with non-existent conditionId
- Cannot insert embedding with non-existent contentBlockId
- Cannot insert medication with non-existent conditionId

### 3. Index Tests
- Verify indexes on foreign key columns
- Test query performance with indexes
- Verify composite indexes if any

### 4. DAO Operation Tests

#### 4.1 Insert Operations
- Single entity insertion
- Batch insertions
- Conflict resolution (REPLACE strategy)
- Auto-generated ID assignment
- Return inserted ID

#### 4.2 Query Operations
- Basic SELECT queries
- Filtered queries (WHERE)
- Sorted queries (ORDER BY)
- LIKE queries for search
- JOIN queries for complex retrieval
- Parameterized queries
- Empty result handling

#### 4.3 Update Operations
- Full entity updates
- Partial updates
- Timestamp updates
- Conflict handling

#### 4.4 Delete Operations
- Single entity deletion
- Conditional deletion
- CASCADE behavior verification

### 5. Data Integrity Tests

#### 5.1 JSON Field Tests
- Store and retrieve JSON arrays (keywords, relatedBlockIds)
- Store and retrieve JSON embeddings
- Handle special characters in JSON

#### 5.2 Enum-like String Tests
Validate accepted values for:
- blockType: "definition", "causes", "symptoms", "treatment", "dosage", "referral", "contraindications", "diagnosis"
- clinicalContext: "general", "pediatric", "adult", "pregnancy", "elderly", "neonatal", "emergency"
- severityLevel: "mild", "moderate", "severe"
- evidenceLevel: "A", "B", "C"
- referenceType: "see_also", "differential", "complication", "prerequisite"
- route: "oral", "IV", "IM", "topical"
- ageGroup: "adult", "pediatric", "neonatal", "elderly"

#### 5.3 Special Character Tests
- Handle quotes in text fields
- Handle newlines and tabs
- Handle Unicode characters
- Handle SQL injection attempts

### 6. Performance Tests
- Query performance with large datasets
- Batch operation performance
- Search cache effectiveness
- Index utilization

### 7. Migration Tests (Future)
- Database version upgrades
- Data preservation during migration
- Rollback mechanisms

## Test Implementation Structure

```
app/src/
├── androidTest/java/co/kobby/clinicalaide/
│   ├── data/
│   │   ├── database/
│   │   │   ├── StgDatabaseSchemaTest.kt      # Schema validation
│   │   │   ├── StgDatabaseForeignKeyTest.kt  # Foreign key tests
│   │   │   ├── StgDaoTest.kt                 # DAO operations
│   │   │   └── StgDatabaseIntegrationTest.kt # End-to-end tests
│   │   └── TestDatabaseHelper.kt             # Test utilities
│   └── TestDataFactory.kt                    # Test data builders
└── test/java/co/kobby/clinicalaide/
    └── data/
        ├── entities/                         # Unit tests for entities
        │   ├── StgChapterTest.kt
        │   ├── StgConditionTest.kt
        │   └── ...
        └── converters/
            └── TypeConvertersTest.kt         # Type converter tests
```

## Test Execution Order

### Phase 1: Foundation Tests ✅
1. Schema Validation Tests - 21 tests implemented
2. Entity Unit Tests - Included in schema tests

### Phase 2: DAO Operations ✅
3. DAO Tests - 18 tests for CRUD operations
4. Query Tests - Complex queries and JOINs

### Phase 3: Data Integrity ✅
5. Data Integrity Tests - 19 tests for data handling
6. Validation Tests - Enum values, JSON, special characters

### Phase 4: Integration & Advanced Tests ✅
7. Integration Tests - 10 end-to-end workflow tests
8. Performance Tests - 13 tests for large datasets and concurrency

### Phase 5: Performance Tests ✅
9. Completed with benchmarks exceeding all targets

## Sample Test Implementations

### Table Existence Test
```kotlin
@Test
fun verify_all_required_tables_exist() {
    val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table'")
    val tables = mutableListOf<String>()
    while (cursor.moveToNext()) {
        tables.add(cursor.getString(0))
    }
    cursor.close()
    
    assertThat(tables).containsExactlyInAnyOrder(
        "stg_chapters",
        "stg_conditions",
        "stg_content_blocks",
        "stg_embeddings",
        "stg_medications",
        "stg_cross_references",
        "stg_search_cache",
        "android_metadata", // System table
        "room_master_table" // Room internal table
    )
}
```

### Column Validation Test
```kotlin
@Test
fun verify_stg_chapters_table_schema() {
    val cursor = database.query("PRAGMA table_info(stg_chapters)")
    val columns = mutableMapOf<String, ColumnInfo>()
    
    while (cursor.moveToNext()) {
        val name = cursor.getString(1)
        val type = cursor.getString(2)
        val notNull = cursor.getInt(3) == 1
        val defaultValue = cursor.getString(4)
        val isPrimaryKey = cursor.getInt(5) == 1
        
        columns[name] = ColumnInfo(type, notNull, defaultValue, isPrimaryKey)
    }
    cursor.close()
    
    assertThat(columns).containsExactly(
        "id" to ColumnInfo("INTEGER", true, null, true),
        "chapterNumber" to ColumnInfo("INTEGER", true, null, false),
        "chapterTitle" to ColumnInfo("TEXT", true, null, false),
        "startPage" to ColumnInfo("INTEGER", true, null, false),
        "endPage" to ColumnInfo("INTEGER", true, null, false),
        "description" to ColumnInfo("TEXT", false, null, false)
    )
}
```

### Foreign Key Test
```kotlin
@Test
fun test_cascade_delete_chapter_deletes_conditions() {
    // Insert test data
    val chapterId = dao.insertChapter(
        StgChapter(
            chapterNumber = 1,
            chapterTitle = "Test Chapter",
            startPage = 1,
            endPage = 10
        )
    )
    
    val conditionId = dao.insertCondition(
        StgCondition(
            chapterId = chapterId,
            conditionNumber = 1,
            conditionName = "Test Condition",
            startPage = 1,
            endPage = 5,
            keywords = "[\"test\"]"
        )
    )
    
    // Verify condition exists
    assertThat(dao.getConditionById(conditionId)).isNotNull()
    
    // Delete chapter
    dao.deleteChapter(chapterId)
    
    // Verify condition was cascade deleted
    assertThat(dao.getConditionById(conditionId)).isNull()
}
```

## Success Criteria ✅
- ✅ All tests pass (81/81)
- ✅ 100% test coverage for database layer
- ✅ All edge cases covered (including array bounds, cache performance)
- ✅ Performance benchmarks exceeded (all targets met or exceeded by 3-22x)
- ✅ No flaky tests (stable test suite)

## Tools and Dependencies
- JUnit 4 for test framework
- Room testing artifacts
- AndroidX Test for instrumentation tests
- Truth for assertions
- Mockito for mocking (if needed)

## Continuous Integration
- Run all database tests on every commit
- Fail build if any test fails
- Generate test coverage reports
- Monitor test execution time

This comprehensive TDD plan ensures our database implementation is robust, reliable, and maintainable.