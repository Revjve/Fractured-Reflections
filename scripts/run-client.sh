#!/usr/bin/env bash
# Fractured Reflections - Client 1 (Survivor) (macOS / Linux). Includes Immersive Portals, LambDynamicLights, Simple Voice Chat.
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x ./gradlew
./gradlew runClient
