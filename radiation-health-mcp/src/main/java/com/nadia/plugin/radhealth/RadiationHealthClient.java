package com.nadia.plugin.radhealth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Client for retrieving environmental radiation readings from Safecast and public-health
 * disease outbreak alerts from the World Health Organization (WHO DON).
 */
public class RadiationHealthClient {

    private static final Logger logger = LoggerFactory.getLogger(RadiationHealthClient.class);
    public static final String DEFAULT_SAFECAST_URL = "https://api.safecast.org/measurements.json";
    public static final String DEFAULT_WHO_DON_URL = "https://www.who.int/api/news/diseaseoutbreaknews";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code RadiationHealthClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public RadiationHealthClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Retrieves environmental radiation measurements near a geographic coordinate via Safecast.
     *
     * @param latitude   latitude in decimal degrees
     * @param longitude  longitude in decimal degrees
     * @param distanceKm search radius in kilometers (default 100)
     * @param limit      maximum readings to return (1..100, default 25)
     * @return structured JSON string with radiation readings
     */
    public String getRadiationReadings(Double latitude, Double longitude, Double distanceKm, Integer limit) {
        if (latitude == null || longitude == null) {
            return errorJson("latitude and longitude parameters are required.");
        }
        double distMeters = (distanceKm == null || distanceKm <= 0 ? 100.0 : Math.min(distanceKm, 1000.0)) * 1000.0;
        int max = (limit == null || limit <= 0) ? 25 : Math.min(limit, 100);
        String base = getProp(RadiationHealthPlugin.PROP_SAFECAST_API_URL, DEFAULT_SAFECAST_URL);
        String url = base + "?latitude=" + latitude + "&longitude=" + longitude
                + "&distance=" + Math.round(distMeters) + "&per_page=" + max;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-RadiationHealth-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("Safecast API returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("count", parsed.isArray() ? parsed.size() : 0);
            out.set("measurements", parsed);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[RadiationHealthClient] getRadiationReadings failed: {}", e.getMessage());
            return errorJson("Safecast request failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves recent World Health Organization (WHO) Disease Outbreak News (DON) alerts.
     *
     * @param query optional keyword filter (disease name or country)
     * @param limit maximum alerts to return (1..50, default 15)
     * @return structured JSON string with WHO outbreak alerts
     */
    public String getWhoOutbreakNews(String query, Integer limit) {
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 50);
        int top = (query != null && !query.isBlank()) ? 100 : max;
        String base = getProp(RadiationHealthPlugin.PROP_WHO_DON_API_URL, DEFAULT_WHO_DON_URL);
        String url = base + "?$orderby=PublicationDate%20desc&$top=" + top;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-RadiationHealth-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("WHO DON API returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            JsonNode values = parsed.path("value");
            String qLower = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            ArrayNode alerts = out.putArray("outbreaks");
            if (values.isArray()) {
                for (JsonNode v : values) {
                    String title = v.path("Title").asText("");
                    String summary = v.path("Summary").asText("");
                    if (!qLower.isEmpty() && !(title + " " + summary).toLowerCase(Locale.ROOT).contains(qLower)) {
                        continue;
                    }
                    ObjectNode item = alerts.addObject();
                    item.put("donId", v.path("DonId").asText(""));
                    item.put("title", title);
                    item.put("publicationDate", v.path("PublicationDate").asText(""));
                    item.put("urlName", v.path("UrlName").asText(""));
                    item.put("summary", summary.length() > 400 ? summary.substring(0, 400) + "..." : summary);
                    if (alerts.size() >= max) break;
                }
            }
            out.put("count", alerts.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[RadiationHealthClient] getWhoOutbreakNews failed: {}", e.getMessage());
            return errorJson("WHO DON request failed: " + e.getMessage());
        }
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) return defaultVal;
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
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
