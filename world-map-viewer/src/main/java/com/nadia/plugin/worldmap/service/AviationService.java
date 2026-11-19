package com.nadia.plugin.worldmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.worldmap.model.FaaAircraftRecord;
import com.nadia.plugin.worldmap.util.IcaoNNumberConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for live aircraft telemetry ingestion and World Map visualization.
 *
 * <p>
 * Integrates with the OpenSky Network REST API to fetch live transponder states
 * (ICAO24, callsign,
 * coordinates, barometric altitude, true track heading) within geographical
 * bounding boxes, plotting
 * active aircraft on the Leaflet World Map in real time and enriching US
 * registrations via {@link FaaRegistryService}.
 * </p>
 *
 * <p>
 * <b>Lifecycle &amp; Thread Safety:</b>
 * Uses a dedicated daemon thread ({@code aviation-tracker}) for periodic sweeps
 * and terminates cleanly
 * via {@link #shutdown()} when the plugin is stopped or unloaded.
 * </p>
 */
public class AviationService {

    private static final Logger logger = LoggerFactory.getLogger(AviationService.class);

    /** Property key for the OpenSky Network account username. */
    public static final String PROP_OPENSKY_USERNAME = "nadia.prop.opensky.username";

    /** Property key for the OpenSky Network account password. */
    public static final String PROP_OPENSKY_PASSWORD = "nadia.prop.opensky.password";

    private final MapService mapService;
    private final FaaRegistryService faaRegistryService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private NadiaPluginContext pluginContext;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> trackingFuture;
    private int currentIntervalSeconds = 15;

    /**
     * Constructs the {@code AviationService} with backward-compatible dependencies
     * and HTTP configuration.
     *
     * @param mapService            the map service used to query viewport bounds
     *                              and plot markers
     * @param objectMapper          JSON parser for OpenSky payload responses
     * @param connectTimeoutSeconds HTTP connection timeout in seconds
     */
    public AviationService(MapService mapService, ObjectMapper objectMapper, int connectTimeoutSeconds) {
        this(mapService, null, objectMapper, connectTimeoutSeconds, null);
    }

    /**
     * Constructs the {@code AviationService} with FAA Registry integration, map
     * service, and plugin context.
     *
     * @param mapService            the map service used to query viewport bounds
     *                              and plot markers
     * @param faaRegistryService    the FAA registry query service (optional)
     * @param objectMapper          JSON parser for OpenSky payload responses
     * @param connectTimeoutSeconds HTTP connection timeout in seconds
     * @param pluginContext         optional host {@link NadiaPluginContext}
     */
    public AviationService(MapService mapService,
            FaaRegistryService faaRegistryService,
            ObjectMapper objectMapper,
            int connectTimeoutSeconds,
            NadiaPluginContext pluginContext) {
        this.mapService = mapService;
        this.faaRegistryService = faaRegistryService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.pluginContext = pluginContext;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aviation-tracker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Updates the host {@link NadiaPluginContext} used to resolve OpenSky
     * credentials.
     *
     * @param pluginContext the host plugin context
     */
    public synchronized void setPluginContext(NadiaPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    /**
     * Retrieves the FAA Civil Aviation Registry service.
     *
     * @return the FAA registry service, or {@code null} if not configured
     */
    public FaaRegistryService getFaaRegistryService() {
        return faaRegistryService;
    }

    /**
     * Checks if continuous background flight tracking is currently active.
     *
     * @return {@code true} if continuous tracking task is actively executing
     */
    public synchronized boolean isTrackingActive() {
        return trackingFuture != null && !trackingFuture.isDone();
    }

    /**
     * Retrieves the current tracking polling interval in seconds.
     *
     * @return tracking interval in seconds
     */
    public synchronized int getTrackingInterval() {
        return currentIntervalSeconds;
    }

    /**
     * Starts continuous background flight tracking at the specified polling rate.
     *
     * @param intervalSeconds polling interval in seconds (clamped to minimum 5
     *                        seconds)
     */
    public synchronized void startContinuousTracking(int intervalSeconds) {
        if (isTrackingActive()) {
            stopContinuousTracking();
        }
        this.currentIntervalSeconds = Math.max(5, intervalSeconds);
        this.trackingFuture = scheduler.scheduleAtFixedRate(
                this::trackPlanesInCurrentView,
                0,
                currentIntervalSeconds,
                TimeUnit.SECONDS);
        logger.info("Continuous aviation tracking started every {} seconds.", currentIntervalSeconds);
    }

    /**
     * Stops the active continuous flight tracking background task.
     */
    public synchronized void stopContinuousTracking() {
        if (trackingFuture != null) {
            trackingFuture.cancel(true);
            trackingFuture = null;
            logger.info("Continuous aviation tracking stopped.");
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
     * Queries the active map viewport bounding box and plots all planes currently
     * in view.
     *
     * @return JSON response string with plot count summary or error message
     */
    public String trackPlanesInCurrentView() {
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

            return fetchAndPlotPlanes(lamin, lomin, lamax, lomax);
        } catch (Exception e) {
            logger.error("Failed to parse map bounds: {}", boundsJson, e);
            return "{\"error\": \"Failed to parse map bounds: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Fetches live flight states within the specified lat/lon bounding box from
     * OpenSky and plots them on the map.
     *
     * @param rawLamin southern latitude boundary
     * @param rawLomin western longitude boundary
     * @param rawLamax northern latitude boundary
     * @param rawLomax eastern longitude boundary
     * @return JSON response string indicating count of planes found and plotted
     */
    public String fetchAndPlotPlanes(double rawLamin, double rawLomin, double rawLamax, double rawLomax) {
        double lamin = Math.max(-90.0, Math.min(90.0, rawLamin));
        double lamax = Math.max(-90.0, Math.min(90.0, rawLamax));
        double lomin = Math.max(-180.0, Math.min(180.0, rawLomin));
        double lomax = Math.max(-180.0, Math.min(180.0, rawLomax));

        if (lamin > lamax) {
            double temp = lamin;
            lamin = lamax;
            lamax = temp;
        }
        if (lomin > lomax) {
            double temp = lomin;
            lomin = lomax;
            lomax = temp;
        }

        String url = String.format(Locale.US,
                "https://opensky-network.org/api/states/all?lamin=%f&lomin=%f&lamax=%f&lomax=%f",
                lamin, lomin, lamax, lomax);

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            NadiaPluginContext ctx;
            synchronized (this) {
                ctx = this.pluginContext;
            }
            String activeUser = ctx != null ? ctx.getProperty(PROP_OPENSKY_USERNAME, "") : "";
            String activePass = ctx != null ? ctx.getProperty(PROP_OPENSKY_PASSWORD, "") : "";

            if (activeUser != null && !activeUser.trim().isEmpty() && activePass != null
                    && !activePass.trim().isEmpty()) {
                String auth = activeUser.trim() + ":" + activePass;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                if (response.statusCode() == 429) {
                    return "{\"error\": \"OpenSky API limit reached (429 Too Many Requests). Please try again later or add credentials.\"}";
                }
                return "{\"error\": \"OpenSky API returned HTTP error: " + response.statusCode() + "\"}";
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode statesNode = root.get("states");
            int plottedCount = 0;

            if (statesNode != null && statesNode.isArray()) {
                java.util.List<MapService.PlotRequest> batch = new java.util.ArrayList<>(statesNode.size());
                for (JsonNode stateNode : statesNode) {
                    if (stateNode.isArray() && stateNode.size() >= 10) {
                        String icao24 = stateNode.get(0).asText();
                        String callsign = stateNode.get(1).asText().trim();
                        if (callsign.isEmpty()) {
                            callsign = icao24.toUpperCase(Locale.ROOT);
                        }

                        JsonNode latNode = stateNode.get(6);
                        JsonNode lonNode = stateNode.get(5);
                        if (latNode.isNull() || lonNode.isNull()) {
                            continue;
                        }

                        double lat = latNode.asDouble();
                        double lon = lonNode.asDouble();

                        double heading = 0.0;
                        if (stateNode.size() > 10) {
                            JsonNode headingNode = stateNode.get(10);
                            if (headingNode != null && !headingNode.isNull()) {
                                heading = headingNode.asDouble();
                            }
                        }

                        String originCountry = "";
                        if (stateNode.size() > 2 && !stateNode.get(2).isNull()) {
                            originCountry = stateNode.get(2).asText().trim();
                        }

                        double baroAltM = 0.0;
                        if (stateNode.size() > 7 && !stateNode.get(7).isNull()) {
                            baroAltM = stateNode.get(7).asDouble();
                        }

                        double velocityMs = 0.0;
                        if (stateNode.size() > 9 && !stateNode.get(9).isNull()) {
                            velocityMs = stateNode.get(9).asDouble();
                        }

                        String squawk = null;
                        if (stateNode.size() > 14 && !stateNode.get(14).isNull()) {
                            squawk = stateNode.get(14).asText().trim();
                        }

                        String nNumber = IcaoNNumberConverter.extractNNumber(callsign, icao24);

                        Map<String, Object> meta = new LinkedHashMap<>();
                        meta.put("icao24", icao24.toUpperCase(Locale.ROOT));
                        meta.put("callsign", callsign);
                        meta.put("squawk", squawk != null && !squawk.isEmpty() ? squawk : "N/A");
                        meta.put("country", originCountry);
                        meta.put("altitudeFt", Math.round(baroAltM * 3.28084));
                        meta.put("speedKts", Math.round(velocityMs * 1.94384));
                        meta.put("heading", Math.round(heading));
                        meta.put("nNumber", nNumber);
                        meta.put("lat", lat);
                        meta.put("lon", lon);

                        if (faaRegistryService != null && nNumber != null && !nNumber.isEmpty()) {
                            FaaAircraftRecord cached = faaRegistryService.getCachedRecord(nNumber);
                            if (cached != null) {
                                meta.put("faa", cached.toMap());
                            } else {
                                faaRegistryService.queryNNumberAsync(nNumber);
                            }
                        }

                        batch.add(new MapService.PlotRequest(callsign, lat, lon, heading, false, "plane", meta));
                        plottedCount++;
                    }
                }
                mapService.plotObjectsBatch(batch);
            }

            return String.format(Locale.US,
                    "{\"success\": true, \"planes_found\": %d, \"lamin\": %f, \"lomin\": %f, \"lamax\": %f, \"lomax\": %f}",
                    plottedCount, lamin, lomin, lamax, lomax);

        } catch (Exception e) {
            logger.error("Exception calling OpenSky API", e);
            return "{\"error\": \"Exception executing flight tracking: " + e.getMessage() + "\"}";
        }
    }
}
