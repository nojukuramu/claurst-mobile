#!/usr/bin/env bash
# download-assets.sh
#
# Run this once before building locally to fetch:
#   1. The Gradle wrapper JAR (needed by ./gradlew)
#   2. xterm.js assets (bundled in the APK)
#
# In CI (GitHub Actions) these are handled automatically by the workflow.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Gradle wrapper JAR ──────────────────────────────────────────────────────
GRADLE_VER="8.8"
WRAPPER_DIR="$SCRIPT_DIR/gradle/wrapper"
JAR="$WRAPPER_DIR/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "Downloading gradle-wrapper.jar …"
  curl -fsSL -o "$JAR" \
    "https://github.com/gradle/gradle/raw/v${GRADLE_VER}.0/gradle/wrapper/gradle-wrapper.jar"
  echo "  → $JAR"
else
  echo "gradle-wrapper.jar already present."
fi

# ── xterm.js ────────────────────────────────────────────────────────────────
XTERM_VER="5.3.0"
XTERM_DIR="$SCRIPT_DIR/app/src/main/assets/xterm"
mkdir -p "$XTERM_DIR"

if [ ! -f "$XTERM_DIR/xterm.js" ]; then
  echo "Downloading xterm.js v${XTERM_VER} …"
  curl -fsSL -o "$XTERM_DIR/xterm.js" \
    "https://cdn.jsdelivr.net/npm/xterm@${XTERM_VER}/lib/xterm.js"
  echo "  → $XTERM_DIR/xterm.js"
else
  echo "xterm.js already present."
fi

if [ ! -f "$XTERM_DIR/xterm.css" ]; then
  echo "Downloading xterm.css …"
  curl -fsSL -o "$XTERM_DIR/xterm.css" \
    "https://cdn.jsdelivr.net/npm/xterm@${XTERM_VER}/css/xterm.css"
  echo "  → $XTERM_DIR/xterm.css"
else
  echo "xterm.css already present."
fi

echo ""
echo "All assets ready. You can now run: ./gradlew assembleDebug"
