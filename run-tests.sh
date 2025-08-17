#!/bin/bash

echo "================================"
echo "Room Database Schema Tests"
echo "================================"
echo ""
echo "This script will run the Room database schema validation tests."
echo "These tests follow TDD principles and are expected to FAIL initially."
echo ""
echo "Prerequisites:"
echo "  - Android emulator running OR physical device connected"
echo "  - USB debugging enabled (for physical device)"
echo ""

# Check if any device is connected
echo "Checking for connected devices..."
adb devices -l | grep -v "List of devices attached"

if [ $? -ne 0 ] || [ -z "$(adb devices -l | grep -v 'List of devices attached')" ]; then
    echo ""
    echo "❌ No devices found!"
    echo ""
    echo "To start an emulator:"
    echo "  1. Open Android Studio"
    echo "  2. Click on 'Device Manager' (phone icon in toolbar)"
    echo "  3. Start an emulator"
    echo ""
    echo "OR from command line:"
    echo "  emulator -list-avds     # List available emulators"
    echo "  emulator -avd <name>    # Start specific emulator"
    echo ""
    exit 1
fi

echo ""
echo "✅ Device found! Running tests..."
echo ""
echo "Note: Tests are expected to FAIL - this is the RED phase of TDD"
echo "================================"
echo ""

# Run the schema validation tests
./gradlew connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=co.kobby.clinicalaide.data.database.StgDatabaseSchemaTest \
    --continue

# Check test results
if [ $? -eq 0 ]; then
    echo ""
    echo "⚠️  Tests PASSED - This is unexpected in TDD RED phase!"
    echo "The tests should fail until entities are implemented."
else
    echo ""
    echo "✅ Tests FAILED as expected (TDD RED phase)"
    echo ""
    echo "Expected failures:"
    echo "  - Missing tables (only stg_chapters exists)"
    echo "  - Missing columns in stg_chapters"
    echo "  - Missing foreign key relationships"
    echo "  - Missing default values"
fi

echo ""
echo "================================"
echo "Test Report Location:"
echo "  app/build/reports/androidTests/connected/index.html"
echo ""
echo "To view report:"
echo "  open app/build/reports/androidTests/connected/index.html"
echo "================================"