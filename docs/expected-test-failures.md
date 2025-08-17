# Expected Test Failures - TDD RED Phase

## Overview
This document shows the expected test failures for `StgDatabaseSchemaTest.kt` when run against the current stub implementation. These failures are intentional and guide our implementation.

## Current Implementation Status
✅ **Stub Database Created**: `StgDatabase.kt` with minimal setup  
✅ **Stub Entity Created**: `StgChapter.kt` with only `id` field  
❌ **Missing**: All other entities and complete field definitions  

## Expected Test Results

### 1. Table Creation Tests

#### ❌ `verify_all_required_tables_exist()`
**Expected Failure:**
```
java.lang.AssertionError: 
Expected to contain at least: [stg_chapters, stg_conditions, stg_content_blocks, stg_embeddings, stg_medications, stg_cross_references, stg_search_cache]
Actual: [stg_chapters]
```
**Reason**: Only `stg_chapters` table exists; missing 6 other tables

#### ❌ `verify_no_extra_tables_exist()`
**Expected Failure:**
```
java.lang.AssertionError:
Expected exactly: [stg_chapters, stg_conditions, stg_content_blocks, stg_embeddings, stg_medications, stg_cross_references, stg_search_cache]
Actual: [stg_chapters]
```
**Reason**: Missing 6 required tables

### 2. Column Validation Tests

#### ❌ `verify_stg_chapters_table_columns()`
**Expected Failure:**
```
java.lang.AssertionError:
Expected columns: [id, chapterNumber, chapterTitle, startPage, endPage, description]
Actual columns: [id]
```
**Reason**: StgChapter entity only has `id` field, missing 5 other columns

#### ❌ `verify_stg_conditions_table_columns()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_conditions
```
**Reason**: Table doesn't exist

#### ❌ `verify_stg_content_blocks_table_columns()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_content_blocks
```
**Reason**: Table doesn't exist

#### ❌ `verify_stg_embeddings_table_columns()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_embeddings
```
**Reason**: Table doesn't exist

#### ❌ `verify_stg_medications_table_columns()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_medications
```
**Reason**: Table doesn't exist

#### ❌ `verify_stg_cross_references_table_columns()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_cross_references
```
**Reason**: Table doesn't exist

#### ❌ `verify_stg_search_cache_table_columns()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_search_cache
```
**Reason**: Table doesn't exist

### 3. Data Type Validation Tests

#### ❌ `verify_stg_chapters_column_types()`
**Expected Failure:**
```
java.lang.NullPointerException
```
**Reason**: Missing columns (only `id` exists)

#### ❌ All other type validation tests
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table
```
**Reason**: Tables don't exist

### 4. Constraint Validation Tests

#### ❌ `verify_stg_chapters_constraints()`
**Expected Failure:**
```
java.lang.AssertionError: Expected notNull to be true for chapterNumber
Actual: column doesn't exist
```
**Reason**: Missing columns

#### ❌ All other constraint tests
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table
```
**Reason**: Tables don't exist

### 5. Foreign Key Tests

#### ❌ `verify_foreign_keys_are_defined()`
**Expected Failure:**
```
android.database.sqlite.SQLiteException: no such table: stg_conditions
```
**Reason**: Table doesn't exist, so foreign keys can't be checked

## Test Summary

| Category | Tests | Expected Pass | Expected Fail | Reason |
|----------|-------|---------------|---------------|--------|
| Table Creation | 2 | 0 | 2 | 6 tables missing |
| Column Validation | 7 | 0 | 7 | 1 incomplete, 6 missing tables |
| Data Type Validation | 6 | 0 | 6 | Missing columns and tables |
| Constraint Validation | 4 | 0 | 4 | Missing columns and tables |
| Foreign Key | 1 | 0 | 1 | Missing tables |
| Default Values | 2 | 0 | 2 | Skipped (TODO) |
| **TOTAL** | **22** | **0** | **22** | **All fail (TDD RED phase)** |

## What These Failures Tell Us

The test failures provide a clear specification for what needs to be implemented:

### 1. Create Entity Classes
- ✅ `StgChapter` (exists but incomplete)
- ❌ `StgCondition`
- ❌ `StgContentBlock`
- ❌ `StgEmbedding`
- ❌ `StgMedication`
- ❌ `StgCrossReference`
- ❌ `StgSearchCache`

### 2. Add All Required Fields
Each entity needs specific fields with correct:
- Data types (Long, String, Boolean)
- Nullability constraints
- Default values
- Primary keys
- Foreign key relationships

### 3. Configure Room Database
Update `StgDatabase.kt` to include all entities in the `@Database` annotation

## How to Run Tests

1. **Start an emulator** (Android Studio → Device Manager)
2. **Run the test script**:
   ```bash
   ./run-tests.sh
   ```
   OR
3. **Run directly with Gradle**:
   ```bash
   ./gradlew connectedAndroidTest
   ```

## Next Steps (GREEN Phase)

To make tests pass, implement each entity with proper Room annotations:

```kotlin
// Example: Complete StgChapter implementation
@Entity(tableName = "stg_chapters")
data class StgChapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterNumber: Int,
    val chapterTitle: String,
    val startPage: Int,
    val endPage: Int,
    val description: String? = null
)
```

Then update the database class:

```kotlin
@Database(
    entities = [
        StgChapter::class,
        StgCondition::class,
        StgContentBlock::class,
        StgEmbedding::class,
        StgMedication::class,
        StgCrossReference::class,
        StgSearchCache::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StgDatabase : RoomDatabase() {
    // DAOs will be added here
}
```

## Benefits of TDD Approach

1. **Clear Requirements**: Test failures show exactly what's needed
2. **Confidence**: When tests pass, we know implementation is correct
3. **Documentation**: Tests serve as living documentation
4. **Refactoring Safety**: Can improve code knowing tests will catch breaks
5. **Design Quality**: Forces us to think about structure before coding

---

**Remember**: These failures are SUCCESS in TDD! They provide the specification that our implementation must satisfy.