package com.nadia.plugin.worldmap.service;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.worldmap.ui.MapWindowView;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service managing geospatial object plotting, waypoint trails, marker flags,
 * and map view synchronization.
 *
 * <p>
 * Coordinates with {@link MapWindowView} on the JavaFX application thread to
 * render dynamic aviation and
 * satellite tracking markers, customize icon types, switch map tile providers,
 * and toggle real-time
 * atmospheric wind stream and marine seamark overlays.
 * </p>
 *
 * <p>
 * <b>Lifecycle &amp; Thread Safety:</b>
 * Scoped to the {@code world-map-viewer} PF4J plugin lifecycle. All state
 * mutations are synchronized, and
 * UI callbacks are marshalled onto the JavaFX Application Thread.
 * </p>
 */
public class MapService {

    /**
     * Property key for the Windy Point Forecast &amp; Map Wind Patterns API key.
     */
    public static final String PROP_WINDY_API_KEY = "nadia.prop.windy.api-key";

    /** Property key for the Windy Webcams API key. */
    public static final String PROP_WEBCAMS_WINDY_API_KEY = "nadia.prop.webcams.windy.api-key";

    private static final String DEFAULT_WINDY_FALLBACK_KEY = "pQl6w8kuhxhv8plg3GjbATVEkVj3TdOJ";

    /**
     * Geospatial coordinate entry with heading, map panning directive, icon style,
     * and timestamp.
     */
    public static class Location {
        /** Latitude in degrees. */
        public final double latitude;
        /** Longitude in degrees. */
        public final double longitude;
        /** Heading/bearing in degrees (0 - 360). */
        public final double heading;
        /** Whether the map viewport should center/pan to this location. */
        public final boolean shouldPan;
        /** Marker icon identifier (e.g. {@code "plane"}, {@code "satellite"}). */
        public final String iconType;
        /** Epoch timestamp in milliseconds. */
        public final long timestamp;
        /**
         * Optional telemetry and registry metadata attributes (transponder, FAA
         * details, altitude, speed).
         */
        public final Map<String, Object> metadata;

        /**
         * Constructs a basic location with default heading, auto-pan enabled, and
         * {@code "plane"} icon.
         *
         * @param latitude  latitude in degrees
         * @param longitude longitude in degrees
         */
        public Location(double latitude, double longitude) {
            this(latitude, longitude, 0.0, true, "plane");
        }

        /**
         * Constructs a location with specified heading, no auto-pan, and default
         * {@code "plane"} icon.
         *
         * @param latitude  latitude in degrees
         * @param longitude longitude in degrees
         * @param heading   heading in degrees
         */
        public Location(double latitude, double longitude, double heading) {
            this(latitude, longitude, heading, false, "plane");
        }

        /**
         * Constructs a location with heading and auto-pan directive.
         *
         * @param latitude  latitude in degrees
         * @param longitude longitude in degrees
         * @param heading   heading in degrees
         * @param shouldPan true to pan map viewport
         */
        public Location(double latitude, double longitude, double heading, boolean shouldPan) {
            this(latitude, longitude, heading, shouldPan, "plane");
        }

        /**
         * Constructor specifying location properties without metadata.
         *
         * @param latitude  latitude in degrees
         * @param longitude longitude in degrees
         * @param heading   heading in degrees
         * @param shouldPan true to pan viewport
         * @param iconType  marker icon identifier string
         */
        public Location(double latitude, double longitude, double heading, boolean shouldPan, String iconType) {
            this(latitude, longitude, heading, shouldPan, iconType, null);
        }

        /**
         * Full constructor specifying all location properties and metadata attributes.
         *
         * @param latitude  latitude in degrees
         * @param longitude longitude in degrees
         * @param heading   heading in degrees
         * @param shouldPan true to pan viewport
         * @param iconType  marker icon identifier string
         * @param metadata  telemetry and registry metadata map
         */
        public Location(double latitude, double longitude, double heading, boolean shouldPan, String iconType,
                Map<String, Object> metadata) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.heading = heading;
            this.shouldPan = shouldPan;
            this.iconType = iconType != null ? iconType : "plane";
            this.timestamp = System.currentTimeMillis();
            this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        }
    }

    /**
     * Named geospatial entity maintaining a chronological historical path trail of
     * {@link Location} points.
     */
    public static class MapObject {
        /** Unique name or callsign of the tracked object. */
        public final String name;
        /** Path trail of recorded locations. */
        public final List<Location> path = new ArrayList<>();
        /**
         * Transponder, registry, and flight telemetry metadata for this tracked entity.
         */
        public final Map<String, Object> metadata = new LinkedHashMap<>();

        /**
         * Constructs a new map object with the given entity name.
         *
         * @param name entity name or callsign
         */
        public MapObject(String name) {
            this.name = name;
        }
    }

    /**
     * Stationary colored pin or flag marker plotted on the map.
     */
    public static class MapFlag {
        /** Latitude in degrees. */
        public final double latitude;
        /** Longitude in degrees. */
        public final double longitude;
        /** Color hex code or CSS color string (e.g. {@code "#FF0000"}). */
        public final String color;

        /**
         * Constructs a new stationary map flag.
         *
         * @param latitude  latitude in degrees
         * @param longitude longitude in degrees
         * @param color     color string
         */
        public MapFlag(double latitude, double longitude, String color) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.color = color;
        }
    }

    private final Map<String, MapObject> objects = new LinkedHashMap<>();
    private final List<MapFlag> flags = new ArrayList<>();
    private Runnable onChangeCallback;
    private NadiaPluginContext pluginContext;

    private MapWindowView mapView;
    private boolean satelliteView = false;
    private boolean terrainView = false;
    private boolean dayNightOverlay = true;
    private boolean trafficOverlay = true;
    private boolean transitOverlay = false;
    private boolean bikingOverlay = false;
    private boolean airQualityOverlay = false;
    private boolean wildfiresOverlay = false;
    private boolean windyOverlay = false;
    private boolean nauticalOverlay = false;

    private String windyApiKey = DEFAULT_WINDY_FALLBACK_KEY;
    private String webcamsWindyApiKey = "";

    /**
     * Constructs a standalone {@code MapService}.
     */
    public MapService() {
        this(null);
    }

    /**
     * Constructs a {@code MapService} bound to the host {@link NadiaPluginContext}.
     *
     * @param pluginContext optional host plugin context for reading
     *                      {@code nadia.prop.*} keys
     */
    public MapService(NadiaPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    /**
     * Sets or updates the host {@link NadiaPluginContext}.
     *
     * @param pluginContext the host plugin context
     */
    public synchronized void setPluginContext(NadiaPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    /**
     * Plots a coordinate for a named entity with auto-pan and default plane icon.
     *
     * @param name      entity name
     * @param latitude  latitude in degrees
     * @param longitude longitude in degrees
     */
    public synchronized void plotObject(String name, double latitude, double longitude) {
        plotObject(name, latitude, longitude, 0.0, true, "plane");
    }

    /**
     * Plots a coordinate with heading for a named entity.
     *
     * @param name      entity name
     * @param latitude  latitude in degrees
     * @param longitude longitude in degrees
     * @param heading   heading in degrees
     */
    public synchronized void plotObject(String name, double latitude, double longitude, double heading) {
        plotObject(name, latitude, longitude, heading, false, "plane");
    }

    /**
     * Plots a coordinate with heading and auto-pan directive for a named entity.
     *
     * @param name      entity name
     * @param latitude  latitude in degrees
     * @param longitude longitude in degrees
     * @param heading   heading in degrees
     * @param shouldPan true to pan viewport
     */
    public synchronized void plotObject(String name, double latitude, double longitude, double heading,
            boolean shouldPan) {
        plotObject(name, latitude, longitude, heading, shouldPan, "plane");
    }

    /**
     * Fully specified entity plot method appending a waypoint to the object's path
     * without metadata.
     *
     * @param name      entity name
     * @param latitude  latitude in degrees
     * @param longitude longitude in degrees
     * @param heading   heading in degrees
     * @param shouldPan true to pan viewport
     * @param iconType  icon identifier
     */
    public synchronized void plotObject(String name, double latitude, double longitude, double heading,
            boolean shouldPan, String iconType) {
        plotObject(name, latitude, longitude, heading, shouldPan, iconType, null);
    }

    /**
     * Fully specified entity plot method appending a waypoint to the object's path
     * with metadata attributes.
     *
     * @param name      entity name
     * @param latitude  latitude in degrees
     * @param longitude longitude in degrees
     * @param heading   heading in degrees
     * @param shouldPan true to pan viewport
     * @param iconType  icon identifier
     * @param metadata  telemetry and registry metadata map
     */
    public synchronized void plotObject(String name, double latitude, double longitude, double heading,
            boolean shouldPan, String iconType, Map<String, Object> metadata) {
        MapObject obj = objects.computeIfAbsent(name, MapObject::new);
        if (metadata != null) {
            obj.metadata.putAll(metadata);
        }
        if ("earthquake".equalsIgnoreCase(iconType)) {
            obj.path.clear();
        }
        obj.path.add(new Location(latitude, longitude, heading, shouldPan, iconType, metadata));
        notifyChange();
    }

    /**
     * Adds a static flag/pin marker to the map.
     *
     * @param latitude  latitude in degrees
     * @param longitude longitude in degrees
     * @param color     color hex or name
     */
    public synchronized void setFlag(double latitude, double longitude, String color) {
        flags.add(new MapFlag(latitude, longitude, color));
        notifyChange();
    }

    /**
     * Returns a copy of all currently plotted map objects.
     *
     * @return list of map objects
     */
    public synchronized List<MapObject> getObjects() {
        return new ArrayList<>(objects.values());
    }

    /**
     * Returns a copy of all currently placed flags.
     *
     * @return list of map flags
     */
    public synchronized List<MapFlag> getFlags() {
        return new ArrayList<>(flags);
    }

    /**
     * Clears all plotted objects and flag markers from the map.
     */
    public synchronized void clearMap() {
        objects.clear();
        flags.clear();
        notifyChange();
    }

    /**
     * Checks whether any currently plotted object uses the specified {@code iconType}.
     *
     * @param iconType marker icon identifier (e.g. {@code "earthquake"})
     * @return {@code true} if at least one matching object is plotted
     */
    public synchronized boolean hasObjectsOfIconType(String iconType) {
        if (iconType == null || iconType.isBlank()) {
            return false;
        }
        for (MapObject obj : objects.values()) {
            if (!obj.path.isEmpty() && iconType.equalsIgnoreCase(obj.path.get(obj.path.size() - 1).iconType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Atomically replaces all plotted objects of the given {@code iconType} with {@code replacementObjects}
     * in a single UI update pass, preserving all other tracked entities (planes, satellites) and flags.
     *
     * @param iconType           marker icon identifier to replace (e.g. {@code "earthquake"})
     * @param replacementObjects new objects to insert
     */
    public synchronized void replaceObjectsByIconType(String iconType, List<MapObject> replacementObjects) {
        if (iconType != null && !iconType.isBlank()) {
            objects.entrySet().removeIf(entry -> {
                MapObject obj = entry.getValue();
                return obj != null && !obj.path.isEmpty()
                        && iconType.equalsIgnoreCase(obj.path.get(obj.path.size() - 1).iconType);
            });
        }
        if (replacementObjects != null) {
            for (MapObject obj : replacementObjects) {
                if (obj != null && obj.name != null) {
                    objects.put(obj.name, obj);
                }
            }
        }
        notifyChange();
    }

    /**
     * Registers a callback invoked whenever objects or flags are modified.
     *
     * @param callback the change callback runnable
     */
    public synchronized void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    private void notifyChange() {
        Runnable cb = this.onChangeCallback;
        if (cb != null) {
            runOnFxIfInitialized(cb);
        }
    }

    private static void runOnFxIfInitialized(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException ignored) {
            // Headless unit test without JavaFX toolkit initialized
        }
    }

    /**
     * Attaches an active {@link MapWindowView} UI controller.
     *
     * @param mapView the map window view
     */
    public synchronized void registerMapView(MapWindowView mapView) {
        this.mapView = mapView;
    }

    /**
     * Enables or disables satellite map tile imagery in the attached map view.
     *
     * @param enabled true for satellite tiles, false for standard OSM/roadmap
     */
    public synchronized void setSatelliteView(boolean enabled) {
        this.satelliteView = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setSatelliteView(enabled));
        }
    }

    /**
     * Checks if satellite tile layer is currently active.
     *
     * @return true if satellite mode is enabled
     */
    public synchronized boolean isSatelliteView() {
        return satelliteView;
    }

    /**
     * Enables or disables topographic terrain relief and elevation contour tile
     * imagery in the attached map view.
     *
     * @param enabled {@code true} for terrain tiles, {@code false} for default
     *                roadmap
     */
    public synchronized void setTerrainView(boolean enabled) {
        this.terrainView = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setTerrainView(enabled));
        }
    }

    /**
     * Checks if topographic terrain tile layer is currently active.
     *
     * @return {@code true} if terrain mode is enabled, {@code false} otherwise
     */
    public synchronized boolean isTerrainView() {
        return terrainView;
    }

    /**
     * Enables or disables the real-time solar terminator day/night illumination
     * shadow overlay.
     *
     * @param enabled {@code true} to display the day/night shadow overlay,
     *                {@code false} to hide it
     */
    public synchronized void setDayNightOverlay(boolean enabled) {
        this.dayNightOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setDayNightView(enabled));
        }
    }

    /**
     * Checks if the real-time solar terminator day/night illumination shadow
     * overlay is currently active.
     *
     * @return {@code true} if day/night shadow tracking is active, {@code false}
     *         otherwise
     */
    public synchronized boolean isDayNightOverlay() {
        return dayNightOverlay;
    }

    /**
     * Enables or disables the real-time live traffic congestion overlay on the map.
     *
     * @param enabled {@code true} to display the traffic overlay, {@code false} to
     *                hide it
     */
    public synchronized void setTrafficOverlay(boolean enabled) {
        this.trafficOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setTrafficView(enabled));
        }
    }

    /**
     * Checks if the real-time live traffic congestion overlay is currently active.
     *
     * @return {@code true} if traffic overlay is active, {@code false} otherwise
     */
    public synchronized boolean isTrafficOverlay() {
        return trafficOverlay;
    }

    /**
     * Enables or disables the public transit routes and stations overlay on the
     * map.
     *
     * @param enabled {@code true} to display public transit lines and stations,
     *                {@code false} to hide it
     */
    public synchronized void setTransitOverlay(boolean enabled) {
        this.transitOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setTransitView(enabled));
        }
    }

    /**
     * Checks if the public transit overlay is currently active.
     *
     * @return {@code true} if transit overlay is active, {@code false} otherwise
     */
    public synchronized boolean isTransitOverlay() {
        return transitOverlay;
    }

    /**
     * Enables or disables the biking and cycling trail network overlay on the map.
     *
     * @param enabled {@code true} to display biking routes and trails,
     *                {@code false} to hide it
     */
    public synchronized void setBikingOverlay(boolean enabled) {
        this.bikingOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setBikingView(enabled));
        }
    }

    /**
     * Checks if the biking trails overlay is currently active.
     *
     * @return {@code true} if biking overlay is active, {@code false} otherwise
     */
    public synchronized boolean isBikingOverlay() {
        return bikingOverlay;
    }

    /**
     * Enables or disables the global Air Quality Index (AQI) heatmap and station
     * overlay on the map.
     *
     * @param enabled {@code true} to display air quality heatmap, {@code false} to
     *                hide it
     */
    public synchronized void setAirQualityOverlay(boolean enabled) {
        this.airQualityOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setAirQualityView(enabled));
        }
    }

    /**
     * Checks if the global Air Quality Index overlay is currently active.
     *
     * @return {@code true} if air quality overlay is active, {@code false}
     *         otherwise
     */
    public synchronized boolean isAirQualityOverlay() {
        return airQualityOverlay;
    }

    /**
     * Enables or disables the NASA FIRMS active wildfire hotspots and thermal
     * anomaly overlay on the map.
     *
     * @param enabled {@code true} to display active wildfire hotspots,
     *                {@code false} to hide it
     */
    public synchronized void setWildfiresOverlay(boolean enabled) {
        this.wildfiresOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setWildfiresView(enabled));
        }
    }

    /**
     * Checks if the NASA FIRMS active wildfire overlay is currently active.
     *
     * @return {@code true} if wildfire overlay is active, {@code false} otherwise
     */
    public synchronized boolean isWildfiresOverlay() {
        return wildfiresOverlay;
    }

    /**
     * Enables or disables the real-time animated global wind streamline particles
     * and atmospheric flow overlay.
     *
     * @param enabled {@code true} to display dynamic wind streamlines,
     *                {@code false} to hide it
     */
    public synchronized void setWindyOverlay(boolean enabled) {
        this.windyOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setWindyView(enabled));
        }
    }

    /**
     * Checks if the Windy.com wind pattern overlay is currently active.
     *
     * @return {@code true} if wind patterns overlay is active, {@code false}
     *         otherwise
     */
    public synchronized boolean isWindyOverlay() {
        return windyOverlay;
    }

    /**
     * Gets the configured Windy.com API Key.
     *
     * @return Windy API Key string
     */
    public synchronized String getWindyApiKey() {
        if (pluginContext != null) {
            String propKey = pluginContext.getProperty(PROP_WINDY_API_KEY, "");
            if (propKey != null && !propKey.isBlank()) {
                return propKey.trim();
            }
        }
        return windyApiKey;
    }

    /**
     * Updates the configured Windy.com API Key.
     *
     * @param apiKey the new Windy.com API key
     */
    public synchronized void setWindyApiKey(String apiKey) {
        this.windyApiKey = apiKey;
    }

    /**
     * Gets the configured Windy Webcams API Key
     * ({@code nadia.prop.webcams.windy.api-key}).
     *
     * @return Windy Webcams API Key string
     */
    public synchronized String getWebcamsWindyApiKey() {
        if (pluginContext != null) {
            String propKey = pluginContext.getProperty(PROP_WEBCAMS_WINDY_API_KEY, "");
            if (propKey != null && !propKey.isBlank()) {
                return propKey.trim();
            }
        }
        return webcamsWindyApiKey;
    }

    /**
     * Updates the configured Windy Webcams API Key.
     *
     * @param apiKey the new Windy Webcams API key
     */
    public synchronized void setWebcamsWindyApiKey(String apiKey) {
        this.webcamsWindyApiKey = apiKey;
    }

    /**
     * Enables or disables the OpenSeaMap nautical seamarks and marine navigation
     * overlay on the map.
     *
     * @param enabled {@code true} to display nautical seamarks, buoys, beacons and
     *                harbor navigation aids, {@code false} to hide it
     */
    public synchronized void setNauticalOverlay(boolean enabled) {
        this.nauticalOverlay = enabled;
        MapWindowView activeView = this.mapView;
        if (activeView != null) {
            runOnFxIfInitialized(() -> activeView.setNauticalView(enabled));
        }
    }

    /**
     * Checks if the OpenSeaMap nautical seamarks overlay is currently active.
     *
     * @return {@code true} if nautical overlay is active, {@code false} otherwise
     */
    public synchronized boolean isNauticalOverlay() {
        return nauticalOverlay;
    }

    /**
     * Queries the current geographic bounding box from the active Leaflet map view.
     *
     * @return JSON string or coordinate bounds format, or {@code null} if map view
     *         is detached
     */
    public String getCurrentBounds() {
        MapWindowView activeView;
        synchronized (this) {
            activeView = this.mapView;
        }
        if (activeView == null) {
            return null;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                return activeView.getMapBounds();
            }
            CompletableFuture<String> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    future.complete(activeView.getMapBounds());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }
}
