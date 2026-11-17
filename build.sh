#!/usr/bin/env bash

# ============================================================================
# Nadia Plugins Builder & Packager (POSIX Shell / Bash)
# ============================================================================
# Default version is stored in gradle.properties (pluginsVersion / pluginApiVersion
# and optional per-plugin <plugin-name>.version).
#
# Builds all PF4J plugin subprojects and collects final plugin JAR files into:
#   dist/<plugin-id>-<version>.jar
#
# Usage:
#   ./build.sh                         Build using versions in gradle.properties
#   ./build.sh --version 1.2.0         Build all plugins as 1.2.0 (one-off override)
#   ./build.sh --set-version 1.2.0     Save pluginsVersion=1.2.0 to gradle.properties and build
#   ./build.sh --plugin <name> --set-version 1.3.0
#                                      Save <name>.version=1.3.0 to gradle.properties and build
#   ./build.sh --api-version 1.0.0     Override host nadia-plugin-api version
#   ./build.sh --with-tests            Run unit & integration tests before packaging
#   ./build.sh --install               Also install built JARs into ~/.nadia/plugins
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

update_prop() {
    local key="$1"
    local val="$2"
    local file="gradle.properties"
    if [ -f "$file" ] && grep -F -q "${key}=" "$file"; then
        awk -v k="$key" -v v="$val" 'BEGIN{FS=OFS="="} $1==k {$0=k"="v} {print}' "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
    else
        printf "\n%s=%s\n" "$key" "$val" >> "$file"
    fi
}

RUN_TESTS=0
INSTALL_PLUGINS=0
TARGET_PLUGIN=""
OVERRIDE_VER=""
PERSIST_VER=""
OVERRIDE_API_VER=""
EXTRA_ARGS=()

while [ $# -gt 0 ]; do
    case "$1" in
        --with-tests)
            RUN_TESTS=1
            shift
            ;;
        --install)
            INSTALL_PLUGINS=1
            shift
            ;;
        --plugin)
            [ $# -ge 2 ] || { echo "[Nadia Plugins Builder] ERROR: --plugin requires a subproject name."; exit 1; }
            TARGET_PLUGIN="$2"
            shift 2
            ;;
        --version)
            [ $# -ge 2 ] || { echo "[Nadia Plugins Builder] ERROR: --version requires a value."; exit 1; }
            OVERRIDE_VER="$2"
            shift 2
            ;;
        --set-version)
            [ $# -ge 2 ] || { echo "[Nadia Plugins Builder] ERROR: --set-version requires a value."; exit 1; }
            PERSIST_VER="$2"
            shift 2
            ;;
        --api-version)
            [ $# -ge 2 ] || { echo "[Nadia Plugins Builder] ERROR: --api-version requires a value."; exit 1; }
            OVERRIDE_API_VER="$2"
            shift 2
            ;;
        *)
            EXTRA_ARGS+=("$1")
            shift
            ;;
    esac
done

if [ -n "$PERSIST_VER" ]; then
    PROP_KEY="pluginsVersion"
    if [ -n "$TARGET_PLUGIN" ]; then
        PROP_KEY="${TARGET_PLUGIN}.version"
    fi
    echo "[Nadia Plugins Builder] Updating ${PROP_KEY}=${PERSIST_VER} in gradle.properties..."
    update_prop "$PROP_KEY" "$PERSIST_VER"
fi

if [ -n "$OVERRIDE_VER" ]; then
    if [ -n "$TARGET_PLUGIN" ]; then
        EXTRA_ARGS+=("-P${TARGET_PLUGIN}.version=${OVERRIDE_VER}")
    else
        EXTRA_ARGS+=("-PpluginsVersionOverride=${OVERRIDE_VER}")
    fi
fi
if [ -n "$OVERRIDE_API_VER" ]; then
    EXTRA_ARGS+=("-PpluginApiVersion=${OVERRIDE_API_VER}")
fi

EFFECTIVE_API_VER="1.0.0"
EFFECTIVE_VER="1.0.0"
if [ -f "gradle.properties" ]; then
    PROP_API=$(grep -E '^pluginApiVersion=' gradle.properties | head -n1 | cut -d'=' -f2- | tr -d '\r' || true)
    PROP_VER=$(grep -E '^pluginsVersion=' gradle.properties | head -n1 | cut -d'=' -f2- | tr -d '\r' || true)
    [ -n "$PROP_API" ] && EFFECTIVE_API_VER="$PROP_API"
    [ -n "$PROP_VER" ] && EFFECTIVE_VER="$PROP_VER"
fi
[ -n "$OVERRIDE_API_VER" ] && EFFECTIVE_API_VER="$OVERRIDE_API_VER"
[ -n "$OVERRIDE_VER" ] && EFFECTIVE_VER="$OVERRIDE_VER"
[ -n "$PERSIST_VER" ] && EFFECTIVE_VER="$PERSIST_VER"

M2_API_JAR="$HOME/.m2/repository/com/nadia/nadia-plugin-api/${EFFECTIVE_API_VER}/nadia-plugin-api-${EFFECTIVE_API_VER}.jar"
if [ ! -f "$M2_API_JAR" ] && [ -f "../nadia/gradlew" ]; then
    echo "[Nadia Plugins Builder] Publishing nadia-plugin-api:${EFFECTIVE_API_VER} to mavenLocal from ../nadia..."
    (cd "../nadia" && chmod +x ./gradlew && ./gradlew :nadia-plugin-api:publishToMavenLocal "-PpluginApiVersion=${EFFECTIVE_API_VER}")
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
echo "[Nadia Plugins Builder] Target Plugin Version: ${EFFECTIVE_VER} (API v${EFFECTIVE_API_VER})"
if [ "$RUN_TESTS" -eq 1 ]; then
    echo "[Nadia Plugins Builder] Building all plugin JARs (with test suite)..."
else
    echo "[Nadia Plugins Builder] Building all plugin JARs (skipping tests)..."
fi
echo "============================================================================"

"$GRADLE_CMD" "${TASKS[@]}" "${EXTRA_ARGS[@]}"

echo ""
echo "============================================================================"
echo "[Nadia Plugins Builder] Build Completed Successfully"
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
