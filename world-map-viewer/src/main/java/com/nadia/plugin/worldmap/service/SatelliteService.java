package com.nadia.plugin.worldmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for tracking satellite positions and plotting them onto the Nadia
 * World Map.
 *
 * <p>
 * Interfaces with the N2YO REST API to fetch overhead satellites based on the
 * current
 * geographical bounding box of the active map viewport, supporting both ad-hoc
 * queries
 * and background continuous periodic tracking.
 * </p>
 *
 * <p>
 * <b>Lifecycle &amp; Thread Safety:</b>
 * Uses a dedicated daemon thread ({@code satellite-tracker}) and terminates
 * cleanly via {@link #shutdown()}
 * when the plugin stops.
 * </p>
 */
public class SatelliteService {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteService.class);

    /** Property key for the N2YO Satellite Tracking API key. */
    public static final String PROP_N2YO_API_KEY = "nadia.prop.n2yo.apiKey";

    private final MapService mapService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private NadiaPluginContext pluginContext;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> trackingFuture;
    private int currentIntervalSeconds = 30;
    private int currentCategoryId = 0;

    /**
     * Constructs a new {@code SatelliteService} with required dependencies and
     * timeouts.
     *
     * @param mapService            the map service used to query bounding boxes and
     *                              plot objects
     * @param objectMapper          the JSON object mapper for response
     *                              deserialization
     * @param connectTimeoutSeconds the HTTP client connection timeout in seconds
     */
    public SatelliteService(MapService mapService, ObjectMapper objectMapper, int connectTimeoutSeconds) {
        this(mapService, objectMapper, connectTimeoutSeconds, null);
    }

    /**
     * Constructs a new {@code SatelliteService} bound to a host
     * {@link NadiaPluginContext}.
     *
     * @param mapService            the map service used to query bounding boxes and
     *                              plot objects
     * @param objectMapper          the JSON object mapper for response
     *                              deserialization
     * @param connectTimeoutSeconds the HTTP client connection timeout in seconds
     * @param pluginContext         optional host {@link NadiaPluginContext}
     */
    public SatelliteService(MapService mapService,
            ObjectMapper objectMapper,
            int connectTimeoutSeconds,
            NadiaPluginContext pluginContext) {
        this.mapService = mapService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.pluginContext = pluginContext;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "satellite-tracker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Updates the host {@link NadiaPluginContext} used to resolve
     * {@code nadia.prop.n2yo.apiKey}.
     *
     * @param pluginContext the host plugin context
     */
    public synchronized void setPluginContext(NadiaPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    /**
     * Checks if continuous satellite tracking is currently scheduled and active.
     *
     * @return {@code true} if continuous tracking is active; {@code false}
     *         otherwise
     */
    public synchronized boolean isTrackingActive() {
        return trackingFuture != null && !trackingFuture.isDone();
    }

    /**
     * Gets the current polling interval in seconds for continuous satellite
     * tracking.
     *
     * @return the tracking interval in seconds
     */
    public synchronized int getTrackingInterval() {
        return currentIntervalSeconds;
    }

    /**
     * Gets the active satellite category filter ID.
     *
     * @return the N2YO satellite category identifier
     */
    public synchronized int getTrackingCategoryId() {
        return currentCategoryId;
    }

    /**
     * Starts continuous periodic tracking of satellites within the active map
     * viewport.
     *
     * @param intervalSeconds the polling frequency in seconds (minimum 5s)
     * @param categoryId      the N2YO satellite category identifier (0 for all)
     */
    public synchronized void startContinuousTracking(int intervalSeconds, int categoryId) {
        if (isTrackingActive()) {
            stopContinuousTracking();
        }
        this.currentIntervalSeconds = Math.max(5, intervalSeconds);
        this.currentCategoryId = categoryId;
        this.trackingFuture = scheduler.scheduleAtFixedRate(
                () -> trackSatellitesInCurrentView(currentCategoryId),
                0,
                currentIntervalSeconds,
                TimeUnit.SECONDS);
        logger.info("Continuous satellite tracking started every {} seconds for category {}.", currentIntervalSeconds,
                currentCategoryId);
    }

    /**
     * Stops the continuous background satellite tracking task if active.
     */
    public synchronized void stopContinuousTracking() {
        if (trackingFuture != null) {
            trackingFuture.cancel(true);
            trackingFuture = null;
            logger.info("Continuous satellite tracking stopped.");
        }
    }

    /**
     * Stops active tracking and shuts down the background scheduler thread when the
     * plugin stops.
     */
    public synchronized void shutdown() {
        stopContinuousTracking();
        scheduler.shutdownNow();
    }

    /**
     * Queries the active map viewport bounds and plots all matching overhead
     * satellites.
     *
     * @param categoryId the N2YO category filter ID
     * @return JSON string containing query status and count of plotted satellites
     */
    public String trackSatellitesInCurrentView(int categoryId) {
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

            return fetchAndPlotSatellites(lamin, lomin, lamax, lomax, categoryId);
        } catch (Exception e) {
            logger.error("Failed to parse map bounds: {}", boundsJson, e);
            return "{\"error\": \"Failed to parse map bounds: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Fetches satellites from the N2YO API for the specified bounding coordinates
     * and plots them.
     *
     * @param lamin      minimum latitude of the bounding box
     * @param lomin      minimum longitude of the bounding box
     * @param lamax      maximum latitude of the bounding box
     * @param lomax      maximum longitude of the bounding box
     * @param categoryId N2YO category ID filter
     * @return JSON response detailing observer coordinates, search radius, and
     *         satellite counts
     */
    public String fetchAndPlotSatellites(double lamin, double lomin, double lamax, double lomax, int categoryId) {
        NadiaPluginContext ctx;
        synchronized (this) {
            ctx = this.pluginContext;
        }
        String activeKey = ctx != null ? ctx.getProperty(PROP_N2YO_API_KEY, "")
                : System.getProperty(PROP_N2YO_API_KEY, "");
        if (activeKey == null || activeKey.trim().isEmpty()) {
            return "{\"error\": \"N2YO apiKey is not configured in nadia.properties (nadia.prop.n2yo.apiKey).\"}";
        }

        double centerLat = (lamin + lamax) / 2.0;
        double centerLng = (lomin + lomax) / 2.0;
        if (lomin > lomax) {
            centerLng = (lomin + lomax + 360.0) / 2.0;
            if (centerLng > 180.0) {
                centerLng -= 360.0;
            }
        }

        double latRadius = Math.abs(lamax - lamin) / 2.0;
        double lonRadius = Math.abs(lomax - lomin) / 2.0;
        if (lomin > lomax) {
            lonRadius = (360.0 - (lomin - lomax)) / 2.0;
        }
        double searchRadius = Math.max(latRadius, lonRadius);
        searchRadius = Math.min(90.0, Math.max(1.0, searchRadius));

        try {
            String url = String.format(Locale.US,
                    "https://api.n2yo.com/rest/v1/satellite/above/%f/%f/%f/%f/%d&apiKey=%s",
                    centerLat, centerLng, 0.0, searchRadius, categoryId, activeKey.trim());

            logger.info("Calling N2YO above API: above/{}/{}/{}/{}/{}",
                    String.format("%.4f", centerLat), String.format("%.4f", centerLng), "0.0",
                    String.format("%.1f", searchRadius), categoryId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("N2YO API returned status code: {}", response.statusCode());
                return "{\"error\": \"N2YO API returned HTTP " + response.statusCode() + "\"}";
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            JsonNode aboveNode = root.get("above");
            int plottedCount = 0;

            if (aboveNode != null && aboveNode.isArray()) {
                for (JsonNode satNode : aboveNode) {
                    int satid = satNode.get("satid").asInt();
                    String satname = satNode.get("satname").asText().trim();
                    double satlat = satNode.get("satlat").asDouble();
                    double satlng = satNode.get("satlng").asDouble();

                    String displayName = satname + " (" + satid + ")";
                    mapService.plotObject(displayName, satlat, satlng, 0.0, false, "satellite");
                    plottedCount++;
                }
            }

            return String.format(Locale.US,
                    "{\"success\": true, \"satellites_found\": %d, \"observer_lat\": %f, \"observer_lng\": %f, \"search_radius\": %f}",
                    plottedCount, centerLat, centerLng, searchRadius);

        } catch (Exception e) {
            logger.error("Exception calling N2YO API", e);
            return "{\"error\": \"Exception executing satellite tracking: " + e.getMessage() + "\"}";
        }
    }
}
