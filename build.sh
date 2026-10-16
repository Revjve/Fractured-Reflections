#!/usr/bin/env bash
# =============================================================================
#  Fractured Reflections - one-command build (macOS / Linux)
#  Run: ./build.sh     The finished mod jar is copied to ./dist
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1 && [ -z "${JAVA_HOME:-}" ]; then
    echo "Java was not found. Install a JDK 17 or 21 (e.g. Eclipse Temurin from https://adoptium.net) and try again." >&2
    exit 1
fi

chmod +x ./gradlew
echo "Building Fractured Reflections... (the first build downloads Minecraft and dependencies)"
./gradlew dist

echo
echo "Done! Your mod:"
ls -1 "$(pwd)"/dist/*.jar
echo
echo "Put it in .minecraft/mods next to Fabric API (1.20.1)."
echo "Recommended: Immersive Portals, LambDynamicLights, Simple Voice Chat."
