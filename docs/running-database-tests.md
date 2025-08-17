# Running Database Tests

## Overview

The ClinicalAide application includes comprehensive database tests following Test-Driven Development (TDD) principles. This guide explains how to run and understand the complete test suite.

## Test Categories

### 1. Schema Validation Tests (`StgDatabaseSchemaTest.kt`)
**Tests:** 21 tests  
**Purpose:** Verify database structure and schema integrity
- Table existence verification
- Column validation for all entities
- Default value verification
- Foreign key constraint validation

### 2. Data Access Object Tests (`StgDaoTest.kt`)
**Tests:** 18 tests  
**Purpose:** Test CRUD operations and queries
- Insert operations
- Query operations
- Update operations
- Delete operations with CASCADE behavior

### 3. Data Integrity Tests (`StgDataIntegrityTest.kt`)
**Tests:** 18 tests  
**Purpose:** Validate data handling and constraints
- JSON field storage and retrieval
- Enum-like string validation
- Special character handling
- SQL injection prevention
- Unicode support

### 4. Integration Tests (`StgDatabaseIntegrationTest.kt`)
**Tests:** 12 tests  
**Purpose:** End-to-end workflow testing
- Complete hierarchy creation
- Search workflow with caching
- Clinical context filtering
- Cross-reference navigation
- Transaction handling

### 5. Performance Tests (`StgDatabasePerformanceTest.kt`)
**Tests:** 15 tests  
**Purpose:** Verify performance under load
- Large dataset operations (1000+ records)
- Complex query performance
- Cache effectiveness
- Concurrent access patterns
- Index utilization

## Total Test Coverage
**Total Tests:** 84 tests across 5 test classes

## Why Device-Based Testing?
As per [Android's official documentation](https://developer.android.com/training/data-storage/room/testing-db):
- Room database tests should run on an Android device
- Provides real SQLite implementation
- Tests actual Android behavior
- More reliable than mocked implementations

## Prerequisites

### Option 1: Using Android Studio (Recommended for Development)
1. Open Android Studio
2. Open AVD Manager: **Tools → AVD Manager**
3. Create a new Virtual Device or use existing one
4. Start the emulator by clicking the play button

### Option 2: Using Command Line
```bash
# List available AVDs
emulator -list-avds

# Start an emulator (replace 'Pixel_3a_API_34' with your AVD name)
emulator -avd Pixel_3a_API_34
```

### Option 3: Using Physical Device
1. Enable Developer Options on your Android device
2. Enable USB Debugging
3. Connect device via USB
4. Verify connection: `adb devices`

## Running the Tests

### From Android Studio
1. Right-click on test class or package
2. Select "Run 'TestName'"
3. Choose your connected device/emulator

### From Command Line

#### Run All Database Tests
```bash
# Run all instrumentation tests
./gradlew connectedAndroidTest

# Run all database tests (using package)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=co.kobby.clinicalaide.data.database
```

#### Run Specific Test Classes
```bash
# Schema tests (21 tests)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.kobby.clinicalaide.data.database.StgDatabaseSchemaTest

# DAO tests (18 tests)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.kobby.clinicalaide.data.database.StgDaoTest

# Data integrity tests (18 tests)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.kobby.clinicalaide.data.database.StgDataIntegrityTest

# Integration tests (12 tests)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.kobby.clinicalaide.data.database.StgDatabaseIntegrationTest

# Performance tests (15 tests)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.kobby.clinicalaide.data.database.StgDatabasePerformanceTest

# Run with detailed output
./gradlew connectedAndroidTest --info
```

#### Build Test APK Without Running
```bash
# Build test APK to verify compilation
./gradlew assembleDebugAndroidTest
```

## Viewing Test Results

### HTML Report
After running tests, view the detailed HTML report:
```bash
open app/build/reports/androidTests/connected/index.html
```

### Console Output
For immediate feedback, check the console output or Logcat in Android Studio.

## Test Output

### Console Output
Tests show:
- Test name and status (PASSED/FAILED)
- Execution time for each test
- Performance metrics for performance tests
- Stack traces for failures

### Performance Test Metrics
Performance tests output additional timing information:
```
Performance: Inserted 1000 chapters in 234ms
Performance: JOIN query with 100 records in 45ms
Performance: Cache speedup: 15.2x
```

## Performance Benchmarks

### Expected Performance Targets
- **Batch Insert (1000 records):** < 5 seconds
- **Complex JOIN queries:** < 1 second
- **Primary key lookups:** < 10ms average
- **Foreign key queries:** < 100ms
- **Cache hits:** 10x faster than cache misses
- **Cascade delete (800+ records):** < 1 second

## Test Development Status

### Current Test Suite Status ✅
All database layers have been implemented following TDD:

1. **Phase 1: Schema Validation** ✅ (21 tests passing)
2. **Phase 2: DAO Operations** ✅ (18 tests passing)
3. **Phase 3: Data Integrity** ✅ (18 tests passing)
4. **Phase 4: Integration Tests** ✅ (12 tests passing)
5. **Phase 5: Performance Tests** ✅ (15 tests passing)

**Total:** 84 tests, all passing

## Troubleshooting

### No Connected Devices Error
```
com.android.builder.testing.api.DeviceException: No connected devices!
```
**Solution**: Start an emulator or connect a physical device

### Out of Memory Error
**Solution**: Increase emulator RAM in AVD settings or use a physical device

### Tests Not Found
**Solution**: Sync project with Gradle files: **File → Sync Project with Gradle Files**

## CI/CD Integration (Future)

For GitHub Actions or other CI systems:
```yaml
# Example GitHub Actions workflow
- name: Run instrumentation tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 29
    script: ./gradlew connectedAndroidTest
```

## Best Practices

1. **Use @SmallTest, @MediumTest, @LargeTest** annotations to categorize tests
2. **Clean state**: Each test should be independent
3. **In-memory database**: Use `Room.inMemoryDatabaseBuilder()` for speed
4. **Test data builders**: Create helper methods for test data
5. **Assertions**: Use Truth library for readable assertions

## Quick Commands Reference

```bash
# Start emulator
emulator -avd <AVD_NAME>

# Check connected devices
adb devices

# Run tests
./gradlew connectedAndroidTest

# View results
open app/build/reports/androidTests/connected/index.html

# Clean and run
./gradlew clean connectedAndroidTest
```

## Next Steps

With the database layer complete and all 84 tests passing, the next phases include:

1. **PDF Parsing Pipeline** - Parse Ghana STG PDF content
2. **AI Integration** - Local embeddings and LLM implementation
3. **User Interface** - Jetpack Compose UI development
4. **Clinical Validation** - Verify medical accuracy

## Summary

The database test suite ensures:
- ✅ Schema integrity (21 tests)
- ✅ Data access correctness (18 tests)
- ✅ Data integrity and validation (18 tests)
- ✅ End-to-end workflows (12 tests)
- ✅ Performance under load (15 tests)

**Total:** 84 comprehensive tests provide full coverage of the database layer, following TDD principles throughout development.

---

**Note**: Following Android's best practices, we test Room databases on actual devices/emulators rather than using Robolectric, ensuring our tests accurately reflect production behavior.