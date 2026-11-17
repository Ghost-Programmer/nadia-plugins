#!/usr/bin/env bash

# ============================================================================
# Nadia Plugins Builder & Packager (POSIX Shell / Bash)
# ============================================================================
# Builds all PF4J plugin subprojects and collects final plugin JAR files into:
#   dist/*.jar
#
# Usage:
#   ./build.sh                Build all final plugin JARs into dist/ (skips tests)
#   ./build.sh --with-tests   Run unit & integration tests before packaging
#   ./build.sh --install      Also copy built plugin JARs into ~/.nadia/plugins
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"
cd "$SCRIPT_DIR"

if [ -x "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
elif [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD="gradle"
else
    echo "[Nadia Plugins Builder] ERROR: Neither ./gradlew nor gradle was found on PATH."
    exit 1
fi

RUN_TESTS=0
INSTALL_PLUGINS=0
EXTRA_ARGS=()
for arg in "$@"; do
    if [ "$arg" = "--with-tests" ]; then
        RUN_TESTS=1
    elif [ "$arg" = "--install" ]; then
        INSTALL_PLUGINS=1
    else
        EXTRA_ARGS+=("$arg")
    fi
done

M2_API_JAR="$HOME/.m2/repository/com/nadia/nadia-plugin-api/1.0.0/nadia-plugin-api-1.0.0.jar"
if [ ! -f "$M2_API_JAR" ] && [ -f "../nadia/gradlew" ]; then
    echo "[Nadia Plugins Builder] Publishing nadia-plugin-api to mavenLocal from ../nadia..."
    (cd "../nadia" && chmod +x ./gradlew && ./gradlew :nadia-plugin-api:publishToMavenLocal)
fi

TASKS=(clean)
if [ "$RUN_TESTS" -eq 1 ]; then
    TASKS+=(check packagePlugins)
else
    TASKS+=(packagePlugins -x test)
fi
if [ "$INSTALL_PLUGINS" -eq 1 ]; then
    TASKS+=(installPlugin)
fi

echo "============================================================================"
echo "[Nadia Plugins Builder] Working Directory: $(pwd)"
if [ "$RUN_TESTS" -eq 1 ]; then
    echo "[Nadia Plugins Builder] Building all plugin JARs (with test suite)..."
else
    echo "[Nadia Plugins Builder] Building all plugin JARs (skipping tests)..."
fi
echo "============================================================================"

"$GRADLE_CMD" "${TASKS[@]}" "${EXTRA_ARGS[@]}"

echo ""
echo "============================================================================"
echo "[Nadia Plugins Builder] Build Completed Successfully!"
echo "============================================================================"
for jar in dist/*.jar; do
    if [ -f "$jar" ]; then
        size=$(wc -c < "$jar" | tr -d ' ')
        echo "  Plugin JAR: $(cd "$(dirname "$jar")" && pwd)/$(basename "$jar") ($size bytes)"
    fi
done
if [ "$INSTALL_PLUGINS" -eq 1 ]; then
    echo "  Installed to: $HOME/.nadia/plugins"
fi
echo "============================================================================"

