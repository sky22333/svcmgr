#!/bin/bash

echo "==============================================="
echo "Android Service Manager - Build Test Script"
echo "==============================================="
echo

echo "Step 1: Checking Gradle Wrapper..."
if [ ! -f "gradlew" ]; then
    echo "ERROR: gradlew not found!"
    exit 1
fi

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "ERROR: gradle-wrapper.jar not found!"
    echo "Please run: gradle wrapper --gradle-version 8.9"
    echo "Or see SETUP_GRADLE_WRAPPER.md for detailed instructions"
    exit 1
fi

echo "Step 2: Making gradlew executable..."
chmod +x gradlew

echo "Step 3: Testing Gradle Wrapper..."
./gradlew --version
if [ $? -ne 0 ]; then
    echo "ERROR: Gradle Wrapper test failed!"
    exit 1
fi

echo "Step 4: Cleaning project..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "ERROR: Clean failed!"
    exit 1
fi

echo "Step 5: Building debug APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "ERROR: Build failed!"
    exit 1
fi

echo "Step 6: Running lint check..."
./gradlew lintDebug
if [ $? -ne 0 ]; then
    echo "WARNING: Lint check failed!"
fi

echo
echo "==============================================="
echo "Build completed successfully!"
echo "==============================================="

if [ -f "app/build/outputs/apk/debug/app-arm64-v8a-debug.apk" ]; then
    echo "✓ APK generated: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
    ls -la "app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
else
    echo "✗ APK not found at expected location"
    echo "Checking all debug APKs:"
    ls -la app/build/outputs/apk/debug/ 2>/dev/null || echo "Debug APK directory not found"
fi

echo