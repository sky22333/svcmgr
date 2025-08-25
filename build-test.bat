@echo off
echo "==============================================="
echo "Android Service Manager - Build Test Script"
echo "==============================================="
echo.

echo "Step 1: Checking Gradle Wrapper..."
if not exist "gradlew.bat" (
    echo "ERROR: gradlew.bat not found!"
    exit /b 1
)

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo "ERROR: gradle-wrapper.jar not found!"
    echo "Please run: gradle wrapper --gradle-version 8.9"
    echo "Or see SETUP_GRADLE_WRAPPER.md for detailed instructions"
    exit /b 1
)

echo "Step 2: Testing Gradle Wrapper..."
call gradlew.bat --version
if %errorlevel% neq 0 (
    echo "ERROR: Gradle Wrapper test failed!"
    exit /b 1
)

echo "Step 3: Cleaning project..."
call gradlew.bat clean
if %errorlevel% neq 0 (
    echo "ERROR: Clean failed!"
    exit /b 1
)

echo "Step 4: Building debug APK..."
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo "ERROR: Build failed!"
    exit /b 1
)

echo "Step 5: Running lint check..."
call gradlew.bat lintDebug
if %errorlevel% neq 0 (
    echo "WARNING: Lint check failed!"
)

echo.
echo "==============================================="
echo "Build completed successfully!"
echo "==============================================="

if exist "app\build\outputs\apk\debug\app-arm64-v8a-debug.apk" (
    echo "✓ APK generated: app\build\outputs\apk\debug\app-arm64-v8a-debug.apk"
    dir "app\build\outputs\apk\debug\app-arm64-v8a-debug.apk"
) else (
    echo "✗ APK not found at expected location"
    echo "Checking all debug APKs:"
    dir "app\build\outputs\apk\debug\" 2>nul || echo "Debug APK directory not found"
)

echo.
pause