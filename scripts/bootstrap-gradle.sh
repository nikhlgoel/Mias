#!/bin/bash

# {Kid} V4 - Gradle Wrapper Bootstrap Script
# Run this from project root if gradle-wrapper.jar is missing

set -e

GRADLE_VERSION="8.13"
WRAPPER_DIR="$(pwd)/gradle/wrapper"
WRAPPER_JAR="${WRAPPER_DIR}/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.12.0/gradle/wrapper/gradle-wrapper.jar"

echo "🔧 Bootstrapping Gradle Wrapper (v${GRADLE_VERSION})..."

# Create wrapper directory
mkdir -p "$WRAPPER_DIR"

# Download wrapper jar directly (fast + reliable)
echo "📥 Downloading gradle-wrapper.jar..."
curl -fL --retry 3 --retry-delay 2 "$WRAPPER_URL" -o "$WRAPPER_JAR"

# Verify
if [ -f "$WRAPPER_JAR" ]; then
    echo "✅ Gradle wrapper successfully bootstrapped!"
    ls -lh "$WRAPPER_JAR"
else
    echo "❌ Failed to bootstrap gradle wrapper"
    exit 1
fi

echo ""
echo "🚀 Next steps:"
echo "  cd $(pwd)"
echo "  ./gradlew assembleDebug"
