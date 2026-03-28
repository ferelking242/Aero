#!/bin/bash
# Velo Browser — Local Setup Script
# Run this once after cloning to complete setup

set -e

echo "=== Velo Browser Setup ==="

# 1. Download gradle-wrapper.jar (not committed to VCS)
echo "Downloading Gradle wrapper jar..."
curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" \
  -o gradle/wrapper/gradle-wrapper.jar
echo "OK: gradle-wrapper.jar downloaded"

# 2. Make gradlew executable
chmod +x gradlew
echo "OK: gradlew is executable"

# 3. Verify local.properties exists
if [ ! -f "local.properties" ]; then
  if [ -n "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo "OK: local.properties created from \$ANDROID_HOME"
  else
    cp local.properties.example local.properties
    echo "WARNING: Set sdk.dir in local.properties to your Android SDK path"
  fi
else
  echo "OK: local.properties already exists"
fi

echo ""
echo "=== Setup complete! ==="
echo "Build debug APK:   ./gradlew assembleDebug"
echo "Install on device: ./gradlew installDebug"
echo "Run lint:          ./gradlew lint"
