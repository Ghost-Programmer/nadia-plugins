# nadia-plugins

Multi-project Gradle repository containing official PF4J plugins targeting the **Nadia Cognitive Agent Platform** (`com.nadia:nadia-plugin-api`).

## Subprojects

| Subproject | Plugin ID | Description |
| :--- | :--- | :--- |
| [`world-map-viewer`](./world-map-viewer) | `world-map-viewer` | Interactive Leaflet.js World Map Viewer (`MapWindowView`) with GIS plotting tools (`MapTool`), live OpenSky Network flight tracking (`AviationTool`), FAA Civil Aviation Registry lookup (`FaaRegistryService`), N2YO orbital satellite tracking (`SatelliteTool`), and live USGS earthquake tracking (`EarthquakeTool`). |

## Prerequisites

Publish `nadia-plugin-api` to your local Maven repository from the `nadia` project once (or whenever `nadia-plugin-api` changes):

```powershell
cd ..\nadia
.\gradlew.bat :nadia-plugin-api:publishToMavenLocal
```

## Building, Versioning & Packaging Plugins

Default versions (`pluginsVersion` and optional per-plugin `<subproject>.version`) are stored in `gradle.properties`. Build all final plugin `.jar` files into `dist/`:

```powershell
# Windows (Batch / PowerShell):
.\build.bat

# Build with a one-off version override:
.\build.bat --version 1.2.0

# Persist a new global version (or per-plugin version) to gradle.properties and build:
.\build.bat --set-version 1.2.0
.\build.bat --plugin world-map-viewer --set-version 1.3.0

# Run tests and also install into ~/.nadia/plugins/ (automatically removes older versions):
.\build.bat --with-tests --install
```

```bash
# macOS / Linux:
./build.sh
./build.sh --version 1.2.0
./build.sh --set-version 1.2.0
./build.sh --with-tests --install
```

You can also invoke Gradle tasks directly:

```powershell
.\gradlew.bat packagePlugins
.\gradlew.bat installPlugin
```
