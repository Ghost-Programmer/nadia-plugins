package com.nadia.plugin.worldmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.worldmap.service.AviationService;
import com.nadia.plugin.worldmap.service.EarthquakeService;
import com.nadia.plugin.worldmap.service.FaaRegistryService;
import com.nadia.plugin.worldmap.service.MapService;
import com.nadia.plugin.worldmap.service.SatelliteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Shared runtime container coordinating the {@link MapService},
 * {@link FaaRegistryService},
 * {@link AviationService}, {@link SatelliteService}, and extracted WebView
 * static assets across
 * all PF4J {@code @Extension} instances inside the {@code world-map-viewer}
 * plugin.
 *
 * <p>
 * <b>Lifecycle &amp; Thread Safety:</b>
 * Initialized once per plugin start inside the plugin's dedicated PF4J
 * {@code PluginClassLoader}
 * and torn down cleanly via {@link #shutdown()} when the plugin is stopped or
 * unloaded.
 * </p>
 */
public final class WorldMapRuntime {

    private static final Logger logger = LoggerFactory.getLogger(WorldMapRuntime.class);

    /** Canonical PF4J plugin identifier. */
    public static final String PLUGIN_ID = "world-map-viewer";

    /** Canonical BentoFX dockable identifier for the World Map Viewer window. */
    public static final String DOCKABLE_ID = "map";

    private static WorldMapRuntime instance;

    private final MapService mapService;
    private final FaaRegistryService faaRegistryService;
    private final AviationService aviationService;
    private final SatelliteService satelliteService;
    private final EarthquakeService earthquakeService;
    private NadiaPluginContext pluginContext;
    private String extractedMapHtmlUri;

    private WorldMapRuntime(NadiaPluginContext context) {
        this.pluginContext = context;
        ObjectMapper objectMapper = new ObjectMapper();
        this.mapService = new MapService(context);
        this.faaRegistryService = new FaaRegistryService(10);
        this.aviationService = new AviationService(mapService, faaRegistryService, objectMapper, 10, context);
        this.satelliteService = new SatelliteService(mapService, objectMapper, 10, context);
        this.earthquakeService = new EarthquakeService(mapService, objectMapper, 10);
        initializeAssetsAndProperties(context);
    }

    /**
     * Returns or lazily initializes the active {@code WorldMapRuntime} singleton
     * for this plugin classloader.
     *
     * @param context optional host {@link NadiaPluginContext}
     * @return active {@code WorldMapRuntime} instance
     */
    public static synchronized WorldMapRuntime getOrCreate(NadiaPluginContext context) {
        if (instance == null) {
            instance = new WorldMapRuntime(context);
        } else if (context != null) {
            instance.attachContext(context);
        }
        return instance;
    }

    /**
     * Stops background schedulers and releases the active runtime singleton when
     * the plugin stops.
     */
    public static synchronized void shutdownCurrent() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    private synchronized void attachContext(NadiaPluginContext context) {
        if (context == null || this.pluginContext == context) {
            return;
        }
        this.pluginContext = context;
        this.mapService.setPluginContext(context);
        this.aviationService.setPluginContext(context);
        this.satelliteService.setPluginContext(context);
        initializeAssetsAndProperties(context);
    }

    private void initializeAssetsAndProperties(NadiaPluginContext context) {
        if (context == null) {
            return;
        }
        try {
            context.registerProperties(
                    List.of(
                            MapService.PROP_WINDY_API_KEY,
                            MapService.PROP_WEBCAMS_WINDY_API_KEY,
                            AviationService.PROP_OPENSKY_USERNAME,
                            AviationService.PROP_OPENSKY_PASSWORD,
                            SatelliteService.PROP_N2YO_API_KEY),
                    "Plugin: " + PLUGIN_ID);
        } catch (Exception e) {
            logger.debug("[WorldMapRuntime] Could not register properties: {}", e.getMessage());
        }

        try {
            Path dataDir = context.getPluginDataDir(PLUGIN_ID);
            if (dataDir != null) {
                Files.createDirectories(dataDir);
                extractResource("/map.html", dataDir.resolve("map.html"));
                extractResource("/leaflet.js", dataDir.resolve("leaflet.js"));
                extractResource("/leaflet.css", dataDir.resolve("leaflet.css"));
                Path mapHtmlPath = dataDir.resolve("map.html");
                if (Files.isRegularFile(mapHtmlPath)) {
                    this.extractedMapHtmlUri = mapHtmlPath.toUri().toString();
                }
            }
        } catch (Exception e) {
            logger.warn("[WorldMapRuntime] Failed to extract map assets to plugin data dir: {}", e.getMessage());
        }
    }

    private void extractResource(String resourcePath, Path targetFile) throws Exception {
        try (InputStream is = WorldMapRuntime.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Returns the shared {@link MapService}.
     *
     * @return the map service
     */
    public MapService getMapService() {
        return mapService;
    }

    /**
     * Returns the shared {@link FaaRegistryService}.
     *
     * @return the FAA registry service
     */
    public FaaRegistryService getFaaRegistryService() {
        return faaRegistryService;
    }

    /**
     * Returns the shared {@link AviationService}.
     *
     * @return the aviation tracking service
     */
    public AviationService getAviationService() {
        return aviationService;
    }

    /**
     * Returns the shared {@link SatelliteService}.
     *
     * @return the satellite tracking service
     */
    public SatelliteService getSatelliteService() {
        return satelliteService;
    }

    /**
     * Returns the shared {@link EarthquakeService}.
     *
     * @return the USGS earthquake tracking service
     */
    public EarthquakeService getEarthquakeService() {
        return earthquakeService;
    }

    /**
     * Returns the {@code file://} URI of the extracted {@code map.html} asset if
     * available,
     * avoiding Windows JAR file locks inside JavaFX {@code WebEngine}.
     *
     * @return extracted {@code map.html} URI or {@code null}
     */
    public synchronized String getExtractedMapHtmlUri() {
        return extractedMapHtmlUri;
    }

    /**
     * Shuts down background aviation, satellite, and earthquake tracking executors.
     */
    public void shutdown() {
        aviationService.shutdown();
        satelliteService.shutdown();
        earthquakeService.shutdown();
        faaRegistryService.shutdown();
    }
}
