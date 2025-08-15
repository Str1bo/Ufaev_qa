#!/bin/bash

echo "=== StampPDF Android App Build Test ==="
echo ""

# Check if we're in the right directory
if [ ! -f "settings.gradle" ]; then
    echo "❌ Error: settings.gradle not found. Please run this script from the project root."
    exit 1
fi

echo "✅ Project structure found"
echo ""

# Check if gradle wrapper exists, if not create it
if [ ! -f "gradlew" ]; then
    echo "📦 Creating Gradle wrapper..."
    gradle wrapper
fi

echo "🔨 Building project..."
echo ""

# Try to build the project
./gradlew build

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📱 To install on device:"
    echo "   ./gradlew installDebug"
    echo ""
    echo "📦 APK location:"
    echo "   app/build/outputs/apk/debug/app-debug.apk"
else
    echo ""
    echo "❌ Build failed!"
    echo "Please check the error messages above."
    exit 1
fi