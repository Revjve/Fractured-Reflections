#!/usr/bin/env bash
# Fractured Reflections - decompiled Minecraft sources for IntelliJ (macOS / Linux). Run once after importing into IntelliJ.
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x ./gradlew
./gradlew genSources
