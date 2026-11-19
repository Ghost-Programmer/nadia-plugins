package com.nadia.plugin.maritime;

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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Client providing curated strategic global maritime chokepoint reference data and live
 * NGA Maritime Safety Information (MSI) Broadcast Navigational Warnings.
 */
public class MaritimeChokepointClient {

    private static final Logger logger = LoggerFactory.getLogger(MaritimeChokepointClient.class);
    public static final String DEFAULT_NGA_MSI_URL = "https://msi.nga.mil/api/publications/broadcast-warn?output=json";

    private record Chokepoint(String id, String name, String navArea, double lat, double lon,
                              String bbox, String tradeSignificance, String primaryRisks) {}

    private static final List<Chokepoint> CHOKEPOINTS = List.of(
            new Chokepoint("hormuz", "Strait of Hormuz", "NAVAREA IX", 26.5667, 56.2500,
                    "54.0,25.0,58.0,27.5", "~20% of global petroleum & LNG transit", "Naval interdiction, mines, GPS spoofing"),
            new Chokepoint("bab-el-mandeb", "Bab el-Mandeb Strait", "NAVAREA IX", 12.5833, 43.3333,
                    "42.0,11.5,45.0,14.5", "~12% of global seaborne trade & Red Sea corridor", "Anti-ship missiles, drones, piracy"),
            new Chokepoint("suez", "Suez Canal", "NAVAREA III", 30.5852, 32.2654,
                    "31.8,29.8,33.0,31.5", "~12% of global trade & Europe-Asia container link", "Canal blockages, Red Sea rerouting"),
            new Chokepoint("malacca", "Strait of Malacca", "NAVAREA XI", 2.5000, 101.2000,
                    "98.5,1.0,104.5,6.0", "~30% of global seaborne trade & East Asia oil lifeline", "Congestion, piracy, collision risk"),
            new Chokepoint("panama", "Panama Canal", "NAVAREA IV", 9.0800, -79.6800,
                    "-80.3,8.7,-79.3,9.5", "~5% of global maritime trade & US-Asia LNG/containers", "Drought/Gatun Lake water draft limits"),
            new Chokepoint("turkish-straits", "Bosporus & Dardanelles (Turkish Straits)", "NAVAREA III", 41.1194, 29.0750,
                    "26.0,40.0,29.5,41.4", "Primary Black Sea grain & oil export corridor", "Black Sea mines, naval transit restrictions"),
            new Chokepoint("taiwan-strait", "Taiwan Strait", "NAVAREA XI", 24.0000, 119.5000,
                    "117.5,22.0,122.0,26.0", "~50% of global container fleet & semiconductor supply", "Military exercises, exclusion zones"),
            new Chokepoint("danish-straits", "Danish Straits", "NAVAREA I", 55.6000, 11.0000,
                    "9.5,54.5,13.0,57.8", "Baltic Sea maritime exit & energy transit", "Shadow fleet, subsea cable/pipeline risk")
    );

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code MaritimeChokepointClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public MaritimeChokepointClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Lists strategic global maritime chokepoints with coordinates, bounding boxes, and risk profiles.
     *
     * @param filter optional chokepoint name or ID filter
     * @return structured JSON list of chokepoints
     */
    public String listChokepoints(String filter) {
        String q = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        try {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            ArrayNode arr = out.putArray("chokepoints");
            for (Chokepoint cp : CHOKEPOINTS) {
                if (!q.isEmpty() && !(cp.id + " " + cp.name + " " + cp.navArea).toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                ObjectNode n = arr.addObject();
                n.put("id", cp.id);
                n.put("name", cp.name);
                n.put("navArea", cp.navArea);
                n.put("lat", cp.lat);
                n.put("lon", cp.lon);
                n.put("bbox", cp.bbox);
                n.put("tradeSignificance", cp.tradeSignificance);
                n.put("primaryRisks", cp.primaryRisks);
            }
            out.put("count", arr.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            return errorJson("Failed to serialize chokepoints: " + e.getMessage());
        }
    }

    /**
     * Retrieves active NGA Maritime Safety Information (MSI) Broadcast Navigational Warnings,
     * optionally filtered by chokepoint ID, NAVAREA, or keyword.
     *
     * @param chokepointOrQuery optional chokepoint ID (e.g., {@code hormuz}, {@code bab-el-mandeb}) or keyword
     * @param navArea           optional NAVAREA filter (e.g., {@code IX}, {@code III}, {@code XI})
     * @param limit             maximum warnings to return (1..100, default 20)
     * @return structured JSON string of active maritime navigational warnings
     */
    public String getMaritimeDisruptions(String chokepointOrQuery, String navArea, Integer limit) {
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        String url = getProp(MaritimeChokepointPlugin.PROP_NGA_MSI_URL, DEFAULT_NGA_MSI_URL);

        String resolvedNavArea = navArea == null ? "" : navArea.trim().toUpperCase(Locale.ROOT);
        String resolvedQuery = chokepointOrQuery == null ? "" : chokepointOrQuery.trim().toLowerCase(Locale.ROOT);
        for (Chokepoint cp : CHOKEPOINTS) {
            if (!resolvedQuery.isEmpty() && (cp.id.equalsIgnoreCase(resolvedQuery) || cp.name.toLowerCase(Locale.ROOT).contains(resolvedQuery))) {
                if (resolvedNavArea.isEmpty()) {
                    resolvedNavArea = cp.navArea.replace("NAVAREA ", "");
                }
                resolvedQuery = cp.name.toLowerCase(Locale.ROOT);
                break;
            }
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-Maritime-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("NGA MSI API returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            JsonNode list = parsed.path("broadcast-warn");
            if (!list.isArray()) list = parsed;

            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("source", "NGA Maritime Safety Information");
            ArrayNode warnings = out.putArray("warnings");

            if (list.isArray()) {
                for (JsonNode w : list) {
                    String area = w.path("navArea").asText("").toUpperCase(Locale.ROOT);
                    String text = w.path("text").asText("");
                    String subregion = w.path("subregion").asText("");
                    if (!resolvedNavArea.isEmpty() && !area.contains(resolvedNavArea)) continue;
                    if (!resolvedQuery.isEmpty()) {
                        String blob = (text + " " + subregion + " " + area).toLowerCase(Locale.ROOT);
                        String token = resolvedQuery.split("\\s+")[0];
                        if (!blob.contains(resolvedQuery) && !blob.contains(token)) continue;
                    }
                    ObjectNode item = warnings.addObject();
                    item.put("msgYear", w.path("msgYear").asInt(0));
                    item.put("msgNumber", w.path("msgNumber").asInt(0));
                    item.put("navArea", w.path("navArea").asText(""));
                    item.put("subregion", subregion);
                    item.put("issueDate", w.path("issueDate").asText(""));
                    item.put("text", text.length() > 500 ? text.substring(0, 500) + "..." : text);
                    if (warnings.size() >= max) break;
                }
            }
            out.put("count", warnings.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[MaritimeChokepointClient] getMaritimeDisruptions failed: {}", e.getMessage());
            return errorJson("NGA MSI request failed: " + e.getMessage());
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
