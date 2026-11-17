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

## Building & Packaging Plugins

Build all final plugin `.jar` files into `dist/`:

```powershell
# Windows (Batch / PowerShell):
.\build.bat

# Run tests and also install into ~/.nadia/plugins/:
.\build.bat --with-tests --install
```

```bash
# macOS / Linux:
./build.sh
./build.sh --with-tests --install
```

You can also invoke Gradle tasks directly:

```powershell
.\gradlew.bat packagePlugins
.\gradlew.bat installPlugin
```
