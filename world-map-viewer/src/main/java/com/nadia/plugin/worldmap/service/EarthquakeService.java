package com.nadia.plugin.worldmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.plugin.worldmap.service.MapService.Location;
import com.nadia.plugin.worldmap.service.MapService.MapObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for querying real-time seismic activity from the USGS Earthquake Hazards Program
 * and plotting earthquake epicenters onto the Nadia World Map.
 *
 * <p>Interfaces with the official keyless USGS FDSN Event GeoJSON REST API
 * ({@code https://earthquake.usgs.gov/fdsnws/event/1/query}) to retrieve earthquakes either within
 * the active World Map viewport bounding box or globally across a configurable 1-to-7 day lookback
 * window (or custom hour window), supporting ad-hoc agent queries and continuous background seismic monitoring.</p>
 *
 * <p><b>Lifecycle &amp; Thread Safety:</b>
 * Uses a dedicated daemon thread ({@code earthquake-tracker}) for periodic sweeps and terminates
 * cleanly via {@link #shutdown()} when the plugin is stopped or unloaded.</p>
 */
public class EarthquakeService {

    private static final Logger logger = LoggerFactory.getLogger(EarthquakeService.class);

    /** Official USGS FDSN Event API GeoJSON query endpoint. */
    public static final String USGS_QUERY_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query";

    /** Minimum selectable earthquake lookback window in days. */
    public static final int MIN_LOOKBACK_DAYS = 1;

    /** Maximum selectable earthquake lookback window in days for the World Map Viewer UI selector. */
    public static final int MAX_LOOKBACK_DAYS = 7;

    private static final String USER_AGENT = "Nadia-WorldMap-USGSEarthquakes/1.0";
    private static final DateTimeFormatter ISO_UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DISPLAY_UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final MapService mapService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> trackingFuture;
    private int currentIntervalSeconds = 60;
    private double currentMinMagnitude = 2.5;
    private int currentLookbackHours = 24;
    private Runnable onConfigChangeCallback;

    /**
     * Constructs a new {@code EarthquakeService} with required dependencies and HTTP connection timeout.
     *
     * @param mapService            the map service used to query bounding boxes and plot seismic epicenters
     * @param objectMapper          JSON parser for USGS GeoJSON feature collections
     * @param connectTimeoutSeconds HTTP client connection timeout in seconds
     */
    public EarthquakeService(MapService mapService, ObjectMapper objectMapper, int connectTimeoutSeconds) {
        this.mapService = mapService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "earthquake-tracker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Clamps a day count into the supported 1-to-7 day lookback range.
     *
     * @param days requested number of days
     * @return clamped day count between {@link #MIN_LOOKBACK_DAYS} (1) and {@link #MAX_LOOKBACK_DAYS} (7)
     */
    public static int clampLookbackDays(int days) {
        return Math.max(MIN_LOOKBACK_DAYS, Math.min(MAX_LOOKBACK_DAYS, days));
    }

    /**
     * Registers an optional UI callback invoked whenever earthquake tracking or lookback settings change.
     *
     * @param callback runnable callback
     */
    public synchronized void setOnConfigChangeCallback(Runnable callback) {
        this.onConfigChangeCallback = callback;
    }

    private void notifyConfigChanged() {
        Runnable cb;
        synchronized (this) {
            cb = this.onConfigChangeCallback;
        }
        if (cb != null) {
            cb.run();
        }
    }

    /**
     * Checks if continuous background earthquake tracking is currently scheduled and active.
     *
     * @return {@code true} if continuous tracking is active; {@code false} otherwise
     */
    public synchronized boolean isTrackingActive() {
        return trackingFuture != null && !trackingFuture.isDone();
    }

    /**
     * Gets the current polling interval in seconds for continuous earthquake tracking.
     *
     * @return polling interval in seconds
     */
    public synchronized int getTrackingInterval() {
        return currentIntervalSeconds;
    }

    /**
     * Gets the minimum Richter magnitude filter for continuous earthquake tracking.
     *
     * @return minimum magnitude threshold
     */
    public synchronized double getMinMagnitude() {
        return currentMinMagnitude;
    }

    /**
     * Gets the lookback window in hours for continuous earthquake tracking.
     *
     * @return lookback duration in hours
     */
    public synchronized int getLookbackHours() {
        return currentLookbackHours;
    }

    /**
     * Gets the active earthquake lookback window in days, clamped to the 1-to-7 day range.
     *
     * @return lookback window in days (1..7)
     */
    public synchronized int getLookbackDays() {
        int days = (int) Math.ceil(currentLookbackHours / 24.0);
        return clampLookbackDays(days);
    }

    /**
     * Updates the earthquake lookback window in days (clamped to 1..7 days).
     *
     * <p>If continuous earthquake tracking is currently active, restarts the periodic sweep immediately
     * using the updated day window. Otherwise, if earthquake markers are currently displayed on the map,
     * asynchronously refreshes them for the new day window.</p>
     *
     * @param days number of past days to display (1 to 7)
     * @return the clamped lookback days applied (1..7)
     */
    public int setLookbackDays(int days) {
        int clampedDays = clampLookbackDays(days);
        boolean active;
        int interval;
        double minMag;
        synchronized (this) {
            this.currentLookbackHours = clampedDays * 24;
            active = isTrackingActive();
            interval = this.currentIntervalSeconds;
            minMag = this.currentMinMagnitude;
        }

        if (active) {
            startContinuousTracking(interval, minMag, clampedDays * 24);
        } else {
            notifyConfigChanged();
            if (mapService != null && mapService.hasObjectsOfIconType("earthquake")) {
                final int hours = clampedDays * 24;
                CompletableFuture.runAsync(() -> trackEarthquakesInCurrentView(minMag, hours));
            }
        }
        return clampedDays;
    }

    /**
     * Starts continuous periodic tracking of USGS earthquakes within the active map viewport.
     *
     * @param intervalSeconds polling frequency in seconds (clamped to minimum 15s)
     * @param minMagnitude    minimum Richter magnitude threshold (e.g. {@code 2.5})
     * @param lookbackHours   lookback time window in hours (clamped to 1..720 hours)
     */
    public synchronized void startContinuousTracking(int intervalSeconds, double minMagnitude, int lookbackHours) {
        if (isTrackingActive()) {
            stopContinuousTrackingInternal();
        }
        this.currentIntervalSeconds = Math.max(15, intervalSeconds);
        this.currentMinMagnitude = Math.max(0.0, minMagnitude);
        this.currentLookbackHours = Math.max(1, Math.min(720, lookbackHours));
        final double magFilter = this.currentMinMagnitude;
        final int hoursFilter = this.currentLookbackHours;

        this.trackingFuture = scheduler.scheduleAtFixedRate(
                () -> trackEarthquakesInCurrentView(magFilter, hoursFilter),
                0,
                currentIntervalSeconds,
                TimeUnit.SECONDS);
        logger.info("Continuous USGS earthquake tracking started every {}s (minMag={}, lookback={}h / {}d).",
                currentIntervalSeconds, currentMinMagnitude, currentLookbackHours, getLookbackDays());
        notifyConfigChanged();
    }

    private void stopContinuousTrackingInternal() {
        if (trackingFuture != null) {
            trackingFuture.cancel(true);
            trackingFuture = null;
        }
    }

    /**
     * Stops the active continuous earthquake tracking background task.
     */
    public synchronized void stopContinuousTracking() {
        if (trackingFuture != null) {
            stopContinuousTrackingInternal();
            logger.info("Continuous USGS earthquake tracking stopped.");
            notifyConfigChanged();
        }
    }

    /**
     * Stops active tracking and shuts down the background scheduler thread when the plugin stops.
     */
    public synchronized void shutdown() {
        stopContinuousTrackingInternal();
        scheduler.shutdownNow();
    }

    /**
     * Queries the active map viewport bounding box and plots all matching USGS earthquakes in view.
     *
     * @param minMagnitude  minimum Richter magnitude threshold
     * @param lookbackHours lookback window in hours
     * @return JSON summary string with count, strongest magnitude, and bounding box
     */
    public String trackEarthquakesInCurrentView(double minMagnitude, int lookbackHours) {
        String boundsJson = mapService.getCurrentBounds();
        if (boundsJson == null || boundsJson.equals("null")) {
            return "{\"error\": \"Map is not loaded or viewport bounds are not available. Please ensure the World Map is open.\"}";
        }

        try {
            JsonNode bounds = objectMapper.readTree(boundsJson);
            double lamin = bounds.get("lamin").asDouble();
            double lomin = bounds.get("lomin").asDouble();
            double lamax = bounds.get("lamax").asDouble();
            double lomax = bounds.get("lomax").asDouble();

            return fetchAndPlotEarthquakes(lamin, lomin, lamax, lomax, minMagnitude, lookbackHours, 200);
        } catch (Exception e) {
            logger.error("Failed to parse map bounds for earthquake tracking: {}", boundsJson, e);
            return "{\"error\": \"Failed to parse map bounds: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Fetches USGS earthquakes within a geographic bounding box and plots them onto the World Map.
     *
     * @param rawLamin      southern latitude boundary
     * @param rawLomin      western longitude boundary
     * @param rawLamax      northern latitude boundary
     * @param rawLomax      eastern longitude boundary
     * @param minMagnitude  minimum Richter magnitude threshold
     * @param lookbackHours lookback window in hours (1..720)
     * @param limit         maximum number of seismic events to return (1..500)
     * @return JSON response detailing count of earthquakes plotted and maximum magnitude
     */
    public String fetchAndPlotEarthquakes(double rawLamin,
                                          double rawLomin,
                                          double rawLamax,
                                          double rawLomax,
                                          double minMagnitude,
                                          int lookbackHours,
                                          int limit) {
        double lamin = Math.max(-90.0, Math.min(90.0, rawLamin));
        double lamax = Math.max(-90.0, Math.min(90.0, rawLamax));
        double lomin = Math.max(-180.0, Math.min(180.0, rawLomin));
        double lomax = Math.max(-180.0, Math.min(180.0, rawLomax));

        if (lamin > lamax) {
            double tmp = lamin;
            lamin = lamax;
            lamax = tmp;
        }
        if (lomin > lomax) {
            double tmp = lomin;
            lomin = lomax;
            lomax = tmp;
        }

        double safeMag = Math.max(0.0, minMagnitude);
        int safeHours = Math.max(1, Math.min(720, lookbackHours));
        int safeLimit = Math.max(1, Math.min(500, limit));
        String startTimeIso = ISO_UTC_FORMATTER.format(Instant.now().minus(safeHours, ChronoUnit.HOURS));

        String url = String.format(Locale.US,
                "%s?format=geojson&starttime=%s&minmagnitude=%.1f&minlatitude=%f&maxlatitude=%f&minlongitude=%f&maxlongitude=%f&orderby=time&limit=%d",
                USGS_QUERY_URL,
                URLEncoder.encode(startTimeIso, StandardCharsets.UTF_8),
                safeMag,
                lamin,
                lamax,
                lomin,
                lomax,
                safeLimit);

        return executeUsgsQueryAndPlot(url, safeMag, safeHours);
    }

    /**
     * Fetches recent global USGS earthquakes worldwide (independent of current viewport zoom) and plots them.
     *
     * @param minMagnitude  minimum Richter magnitude threshold (e.g. {@code 4.5})
     * @param lookbackHours lookback window in hours (1..720, e.g. {@code 168} for 7 days)
     * @param limit         maximum number of seismic events to return (1..500)
     * @return JSON response detailing count of global earthquakes plotted and strongest event
     */
    public String fetchAndPlotGlobalEarthquakes(double minMagnitude, int lookbackHours, int limit) {
        double safeMag = Math.max(0.0, minMagnitude);
        int safeHours = Math.max(1, Math.min(720, lookbackHours));
        int safeLimit = Math.max(1, Math.min(500, limit));
        String startTimeIso = ISO_UTC_FORMATTER.format(Instant.now().minus(safeHours, ChronoUnit.HOURS));

        String url = String.format(Locale.US,
                "%s?format=geojson&starttime=%s&minmagnitude=%.1f&orderby=time&limit=%d",
                USGS_QUERY_URL,
                URLEncoder.encode(startTimeIso, StandardCharsets.UTF_8),
                safeMag,
                safeLimit);

        return executeUsgsQueryAndPlot(url, safeMag, safeHours);
    }

    private String executeUsgsQueryAndPlot(String url, double minMagnitude, int lookbackHours) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger.warn("USGS Earthquake API returned HTTP {}", response.statusCode());
                return "{\"error\": \"USGS Earthquake API returned HTTP " + response.statusCode() + "\"}";
            }

            return parseAndPlotGeoJson(response.body(), minMagnitude, lookbackHours);
        } catch (Exception e) {
            logger.error("Exception calling USGS Earthquake API", e);
            return "{\"error\": \"Exception executing earthquake tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Parses a USGS GeoJSON FeatureCollection payload, atomically replaces plotted earthquake epicenters
     * on {@link MapService}, and returns a structured JSON summary for agent reasoning.
     *
     * @param geoJsonBody   raw USGS GeoJSON response string
     * @param minMagnitude  applied minimum magnitude filter
     * @param lookbackHours applied lookback hours filter
     * @return JSON summary of plotted seismic events
     */
    public String parseAndPlotGeoJson(String geoJsonBody, double minMagnitude, int lookbackHours) {
        if (geoJsonBody == null || geoJsonBody.isBlank()) {
            return "{\"error\": \"Empty GeoJSON response from USGS.\"}";
        }

        try {
            JsonNode root = objectMapper.readTree(geoJsonBody);
            JsonNode features = root.get("features");
            List<MapObject> newQuakeObjects = new ArrayList<>();
            Map<String, MapObject> dedupByName = new LinkedHashMap<>();
            double maxMag = 0.0;
            String strongestEvent = "";

            if (features != null && features.isArray()) {
                for (JsonNode feat : features) {
                    JsonNode props = feat.path("properties");
                    JsonNode coords = feat.path("geometry").path("coordinates");
                    if (!coords.isArray() || coords.size() < 2) {
                        continue;
                    }

                    double lon = coords.get(0).asDouble(Double.NaN);
                    double lat = coords.get(1).asDouble(Double.NaN);
                    if (Double.isNaN(lat) || Double.isNaN(lon)) {
                        continue;
                    }
                    double depthKm = coords.size() > 2 && !coords.get(2).isNull() ? coords.get(2).asDouble(0.0) : 0.0;

                    double mag = !props.path("mag").isNull() ? props.path("mag").asDouble(0.0) : 0.0;
                    String magType = props.path("magType").asText("").trim();
                    String place = props.path("place").asText("Unknown Location").trim();
                    if (place.isEmpty()) {
                        place = "Unknown Location";
                    }
                    String eventId = feat.path("id").asText("").trim();
                    long timeMs = props.path("time").asLong(0L);
                    String timeUtc = timeMs > 0
                            ? DISPLAY_UTC_FORMATTER.format(Instant.ofEpochMilli(timeMs))
                            : "Unknown";
                    boolean tsunami = props.path("tsunami").asInt(0) == 1;
                    String alertLevel = props.path("alert").isNull() ? "" : props.path("alert").asText("").trim();
                    int felt = props.path("felt").isNull() ? 0 : props.path("felt").asInt(0);
                    int significance = props.path("sig").isNull() ? 0 : props.path("sig").asInt(0);
                    String status = props.path("status").asText("automatic").trim();
                    String eventUrl = props.path("url").asText("https://earthquake.usgs.gov/earthquakes/map/").trim();

                    String magBadge = String.format(Locale.US, "M%.1f", mag);
                    String labelName = eventId.isEmpty()
                            ? magBadge + " · " + place
                            : magBadge + " (" + eventId + ")";

                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("eventId", eventId);
                    meta.put("magnitude", Math.round(mag * 10.0) / 10.0);
                    meta.put("magType", magType);
                    meta.put("place", place);
                    meta.put("depthKm", Math.round(depthKm * 10.0) / 10.0);
                    meta.put("timeMs", timeMs);
                    meta.put("timeUtc", timeUtc);
                    meta.put("tsunami", tsunami);
                    meta.put("alert", alertLevel);
                    meta.put("felt", felt);
                    meta.put("significance", significance);
                    meta.put("status", status);
                    meta.put("url", eventUrl);
                    meta.put("lat", lat);
                    meta.put("lon", lon);

                    MapObject obj = new MapObject(labelName);
                    obj.metadata.putAll(meta);
                    obj.path.add(new Location(lat, lon, 0.0, false, "earthquake", meta));
                    dedupByName.put(labelName, obj);

                    if (dedupByName.size() == 1 || mag > maxMag) {
                        maxMag = mag;
                        strongestEvent = magBadge + " - " + place;
                    }
                }
            }

            newQuakeObjects.addAll(dedupByName.values());
            mapService.replaceObjectsByIconType("earthquake", newQuakeObjects);

            int plottedCount = newQuakeObjects.size();
            int lookbackDays = Math.max(1, (int) Math.ceil(lookbackHours / 24.0));

            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("success", true);
            responseMap.put("earthquakes_found", plottedCount);
            responseMap.put("min_magnitude", minMagnitude);
            responseMap.put("lookback_hours", lookbackHours);
            responseMap.put("lookback_days", lookbackDays);
            if (plottedCount > 0) {
                responseMap.put("max_magnitude", Math.round(maxMag * 10.0) / 10.0);
                responseMap.put("strongest_event", strongestEvent);
            }
            return objectMapper.writeValueAsString(responseMap);
        } catch (Exception e) {
            logger.error("Failed to parse USGS GeoJSON payload", e);
            return "{\"error\": \"Failed to parse USGS earthquake feed: " + e.getMessage() + "\"}";
        }
    }
}
