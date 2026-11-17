# World Map Viewer Plugin (`world-map-viewer`)

PF4J plugin contributing the interactive **World Map Viewer** (`🗺️ World Map Viewer`) dockable window and geospatial, aviation, and orbital satellite `@NadiaTool` extensions to Nadia.

## Contributed Extensions

- **UI Extension (`WorldMapUiExtension`)**:
  - BentoFX dockable workspace window: `map` (`🗺️ World Map Viewer`), dynamically mounted in Nadia's **View** menu and primary workspace canvas.
- **Tool Extensions**:
  - `MapTool`: `showMap`, `plotObject`, `setFlag`, `clearMap`, `setMapSatelliteView`, `setMapTerrainView`, `setMapDayNightView`, `setMapTrafficView`, `setMapTransitView`, `setMapBikingView`, `setMapAirQualityView`, `setMapWildfiresView`, `setMapWindyView`, `setMapNauticalView`
  - `AviationTool`: `trackPlanes`, `startContinuousPlaneTracking`, `stopContinuousPlaneTracking`, `getPlaneTrackingStatus`
  - `SatelliteTool`: `trackSatellites`, `startContinuousSatelliteTracking`, `stopContinuousSatelliteTracking`, `getSatelliteTrackingStatus`, `getSatelliteCategories`
  - `EarthquakeTool`: `trackEarthquakes`, `setEarthquakeLookbackDays`, `trackGlobalEarthquakes`, `startContinuousEarthquakeTracking`, `stopContinuousEarthquakeTracking`, `getEarthquakeTrackingStatus`

## Configuration Properties (`~/.nadia/nadia.properties`)

- `nadia.prop.windy.api-key` — Windy.com wind streamline overlay API key
- `nadia.prop.webcams.windy.api-key` — Windy Webcams API key
- `nadia.prop.opensky.username` / `nadia.prop.opensky.password` — OpenSky Network credentials (optional; increases rate limits)
- `nadia.prop.n2yo.apiKey` — N2YO REST API key for live orbital satellite tracking
