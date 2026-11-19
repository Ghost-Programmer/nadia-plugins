package com.nadia.plugin.firms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Client for querying NASA FIRMS (Fire Information for Resource Management System) active fire
 * hotspots and NASA EONET (Earth Observatory Natural Event Tracker) v3 events.
 */
public class NasaFirmsClient {

    private static final Logger logger = LoggerFactory.getLogger(NasaFirmsClient.class);
    public static final String DEFAULT_EONET_URL = "https://eonet.gsfc.nasa.gov/api/v3";
    private static final String FIRMS_AREA_BASE = "https://firms.modaps.eosdis.nasa.gov/api/area/csv";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code NasaFirmsClient} backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public NasaFirmsClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Queries NASA FIRMS satellite active fire detections within a bounding box.
     *
     * @param bbox      bounding box as {@code west,south,east,north} (default {@code world})
     * @param source    satellite sensor (default {@code VIIRS_SNPP_NRT})
     * @param dayRange  lookback days (1..10, default 1)
     * @param limit     maximum hotspots to return (1..200, default 50)
     * @return structured JSON string with active fire hotspots
     */
    public String getActiveFires(String bbox, String source, Integer dayRange, Integer limit) {
        String apiKey = getProp(NasaFirmsPlugin.PROP_FIRMS_API_KEY, "");
        if (apiKey.isEmpty()) {
            return errorJson("NASA FIRMS MAP_KEY is not configured. Request a free key at https://firms.modaps.eosdis.nasa.gov/api/area/ and set "
                    + NasaFirmsPlugin.PROP_FIRMS_API_KEY + " in Settings -> Properties.");
        }
        String area = (bbox == null || bbox.isBlank()) ? "world" : bbox.trim();
        String sensor = (source == null || source.isBlank()) ? "VIIRS_SNPP_NRT" : source.trim();
        int days = (dayRange == null || dayRange <= 0) ? 1 : Math.min(dayRange, 10);
        int max = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);

        String url = FIRMS_AREA_BASE + "/" + encode(apiKey) + "/" + encode(sensor) + "/" + area + "/" + days;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Nadia-NASA-FIRMS-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("NASA FIRMS returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            String[] lines = resp.body().split("\\r?\\n");
            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("source", sensor);
            root.put("area", area);
            root.put("dayRange", days);
            ArrayNode fires = root.putArray("fires");
            if (lines.length > 1) {
                String[] headers = lines[0].split(",");
                int total = 0;
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty()) continue;
                    total++;
                    if (fires.size() < max) {
                        String[] cols = line.split(",");
                        ObjectNode row = fires.addObject();
                        for (int c = 0; c < Math.min(headers.length, cols.length); c++) {
                            row.put(headers[c].trim(), cols[c].trim());
                        }
                    }
                }
                root.put("totalDetected", total);
            } else {
                root.put("totalDetected", 0);
            }
            root.put("returnedCount", fires.size());
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[NasaFirmsClient] getActiveFires failed: {}", e.getMessage());
            return errorJson("NASA FIRMS request failed: " + e.getMessage());
        }
    }

    /**
     * Searches NASA EONET v3 natural hazard events (wildfires, volcanoes, severe storms, floods, sea/lake ice).
     *
     * @param category optional EONET category slug (e.g., {@code wildfires}, {@code volcanoes}, {@code severeStorms}, {@code floods})
     * @param status   optional status ({@code open} or {@code closed}, default {@code open})
     * @param days     optional lookback days (e.g., 14, 30)
     * @param limit    maximum events to return (1..100, default 20)
     * @return structured JSON string with EONET natural events
     */
    public String searchNaturalEvents(String category, String status, Integer days, Integer limit) {
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        String base = getProp(NasaFirmsPlugin.PROP_EONET_API_URL, DEFAULT_EONET_URL);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        StringBuilder url = new StringBuilder(base).append("/events?limit=").append(max);
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(encode(status.trim().toLowerCase()));
        } else {
            url.append("&status=open");
        }
        if (category != null && !category.isBlank()) {
            url.append("&category=").append(encode(category.trim()));
        }
        if (days != null && days > 0) {
            url.append("&days=").append(Math.min(days, 365));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-NASA-EONET-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("NASA EONET returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            JsonNode events = parsed.path("events");
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("count", events.isArray() ? events.size() : 0);
            out.set("events", events);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[NasaFirmsClient] searchNaturalEvents failed: {}", e.getMessage());
            return errorJson("NASA EONET request failed: " + e.getMessage());
        }
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) return defaultVal;
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "...";
    }

    private String errorJson(String message) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("status", "ERROR");
            n.put("message", message);
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"Serialization error\"}";
        }
    }
}
