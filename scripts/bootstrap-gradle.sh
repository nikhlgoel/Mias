#!/bin/bash

# {Kid} V4 - Gradle Wrapper Bootstrap Script
# Run this from project root if gradle-wrapper.jar is missing

set -e

GRADLE_VERSION="8.12"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
WRAPPER_DIR="$(pwd)/gradle/wrapper"

echo "🔧 Bootstrapping Gradle Wrapper (v${GRADLE_VERSION})..."

# Create wrapper directory
mkdir -p "$WRAPPER_DIR"

# Download gradle distribution
echo "📥 Downloading Gradle ${GRADLE_VERSION}..."
curl -fsSL https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o gradle-${GRADLE_VERSION}-bin.zip

# Extract and setup gradle-wrapper.jar
echo "📦 Extracting wrapper files..."
unzip -q gradle-${GRADLE_VERSION}-bin.zip
cp gradle-${GRADLE_VERSION}/lib/gradle-wrapper.jar "$WRAPPER_DIR/gradle-wrapper.jar"
rm -rf gradle-${GRADLE_VERSION} gradle-${GRADLE_VERSION}-bin.zip

# Verify
if [ -f "$WRAPPER_DIR/gradle-wrapper.jar" ]; then
    echo "✅ Gradle wrapper successfully bootstrapped!"
    ls -lh "$WRAPPER_DIR/gradle-wrapper.jar"
else
    echo "❌ Failed to bootstrap gradle wrapper"
    exit 1
fi

echo ""
echo "🚀 Next steps:"
echo "  cd $(pwd)"
echo "  ./gradlew assembleDebug"
