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

## Building & Testing Plugins

Build and test all plugin subprojects:

```powershell
.\gradlew.bat build
```

Install all built plugin `.jar` archives directly into `~/.nadia/plugins/`:

```powershell
.\gradlew.bat installPlugin
```

Or install only `world-map-viewer`:

```powershell
.\gradlew.bat :world-map-viewer:installPlugin
```
