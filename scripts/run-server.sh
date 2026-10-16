#!/usr/bin/env bash
# Fractured Reflections - Dev Server (macOS / Linux). Includes Immersive Portals, LambDynamicLights, Simple Voice Chat.
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x ./gradlew
./gradlew help -q >/dev/null 2>&1 || true   # lets the build script write run-server/ (eula only if accepted)
if [ ! -f run-server/eula.txt ]; then
    echo "The dev server needs the Minecraft EULA accepted: read https://aka.ms/MinecraftEULA" >&2
    echo "and set accept_minecraft_eula=true in gradle.properties, then run this again." >&2
    exit 1
fi
./gradlew runServer
