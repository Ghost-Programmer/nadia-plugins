package com.nadia.plugin.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Client for global sanctions screening via OpenSanctions API (when configured) with automatic
 * fallback to the official keyless U.S. Treasury OFAC SDN CSV list (cached for 6 hours).
 */
public class SanctionsClient {

    private static final Logger logger = LoggerFactory.getLogger(SanctionsClient.class);
    public static final String DEFAULT_OPENSANCTIONS_URL = "https://api.opensanctions.org";
    public static final String DEFAULT_OFAC_SDN_URL = "https://www.treasury.gov/ofac/downloads/sdn.csv";
    private static final long OFAC_CACHE_TTL_MS = Duration.ofHours(6).toMillis();

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile List<String[]> cachedOfacRows;
    private volatile long ofacCacheTimestampMs;

    /**
     * Constructs a {@code SanctionsClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public SanctionsClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Screens a person, company, vessel, aircraft, or crypto address against OpenSanctions or OFAC SDN.
     *
     * @param query   required entity name, IMO number, tail number, or identifier
     * @param schema  optional entity schema ({@code Person}, {@code Company}, {@code Vessel}, {@code Airplane}, {@code Thing})
     * @param country optional ISO-2 country filter
     * @param limit   max matches (1..100, default 20)
     * @return structured JSON screening result
     */
    public String searchEntity(String query, String schema, String country, Integer limit) {
        if (query == null || query.isBlank()) {
            return errorJson("query parameter is required for sanctions screening.");
        }
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        String apiKey = getProp(SanctionsPlugin.PROP_OPENSANCTIONS_API_KEY, "");
        if (!apiKey.isEmpty()) {
            return searchOpenSanctions(query.trim(), schema, country, max, apiKey);
        }
        return searchOfacSdn(query.trim(), schema, country, max);
    }

    /**
     * Screens a maritime vessel or aircraft by name, IMO/MMSI, or tail/registration number.
     *
     * @param identifier vessel name, IMO, MMSI, or aircraft registration
     * @param assetType  {@code vessel} (default) or {@code aircraft}
     * @param limit      max matches (default 15)
     * @return structured JSON screening result
     */
    public String checkVesselOrAircraft(String identifier, String assetType, Integer limit) {
        String schema = "aircraft".equalsIgnoreCase(assetType != null ? assetType.trim() : "") ? "Airplane" : "Vessel";
        return searchEntity(identifier, schema, null, limit != null ? limit : 15);
    }

    private String searchOpenSanctions(String query, String schema, String country, int limit, String apiKey) {
        String base = getProp(SanctionsPlugin.PROP_OPENSANCTIONS_API_URL, DEFAULT_OPENSANCTIONS_URL);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        StringBuilder url = new StringBuilder(base)
                .append("/search/sanctions?q=").append(encode(query))
                .append("&limit=").append(limit);
        if (schema != null && !schema.isBlank()) {
            url.append("&schema=").append(encode(schema.trim()));
        }
        if (country != null && !country.isBlank()) {
            url.append("&countries=").append(encode(country.trim().toLowerCase(Locale.ROOT)));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "ApiKey " + apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-Sanctions-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("OpenSanctions API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("source", "OpenSanctions");
            out.put("total", parsed.path("total").path("value").asInt(parsed.path("results").size()));
            out.set("results", parsed.path("results"));
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[SanctionsClient] OpenSanctions query failed: {}", e.getMessage());
            return errorJson("OpenSanctions request failed: " + e.getMessage());
        }
    }

    private String searchOfacSdn(String query, String schema, String country, int limit) {
        try {
            List<String[]> rows = loadOfacSdn();
            String qLower = query.toLowerCase(Locale.ROOT);
            String schemaLower = schema != null ? schema.trim().toLowerCase(Locale.ROOT) : "";
            String countryLower = country != null ? country.trim().toLowerCase(Locale.ROOT) : "";

            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("source", "OFAC SDN (keyless fallback)");
            ArrayNode matches = out.putArray("results");
            int totalMatches = 0;

            for (String[] cols : rows) {
                String entNum = col(cols, 0);
                String name = col(cols, 1);
                String sdnType = col(cols, 2);
                String program = col(cols, 3);
                String remarks = col(cols, 11);

                String blob = (name + " " + sdnType + " " + program + " " + remarks).toLowerCase(Locale.ROOT);
                if (!blob.contains(qLower)) continue;
                if (!schemaLower.isEmpty()) {
                    if (schemaLower.equals("vessel") && !sdnType.toLowerCase(Locale.ROOT).contains("vessel")) continue;
                    if (schemaLower.equals("airplane") && !sdnType.toLowerCase(Locale.ROOT).contains("aircraft")) continue;
                    if (schemaLower.equals("person") && !sdnType.toLowerCase(Locale.ROOT).contains("individual")) continue;
                }
                if (!countryLower.isEmpty() && !blob.contains(countryLower)) continue;

                totalMatches++;
                if (matches.size() < limit) {
                    ObjectNode item = matches.addObject();
                    item.put("id", "ofac-" + entNum);
                    item.put("name", name);
                    item.put("type", sdnType);
                    item.put("program", program);
                    item.put("remarks", truncate(remarks, 300));
                }
            }
            out.put("totalMatches", totalMatches);
            out.put("returnedCount", matches.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[SanctionsClient] OFAC SDN query failed: {}", e.getMessage());
            return errorJson("OFAC SDN screening failed: " + e.getMessage());
        }
    }

    private synchronized List<String[]> loadOfacSdn() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedOfacRows != null && (now - ofacCacheTimestampMs) < OFAC_CACHE_TTL_MS) {
            return cachedOfacRows;
        }
        String url = getProp(SanctionsPlugin.PROP_OFAC_SDN_URL, DEFAULT_OFAC_SDN_URL);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "Nadia-Sanctions-MCP/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("OFAC SDN download returned HTTP " + resp.statusCode());
        }
        List<String[]> parsed = new ArrayList<>();
        for (String line : resp.body().split("\\r?\\n")) {
            if (line.isBlank()) continue;
            parsed.add(parseCsvLine(line));
        }
        this.cachedOfacRows = parsed;
        this.ofacCacheTimestampMs = now;
        return parsed;
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }

    private static String col(String[] cols, int idx) {
        if (idx >= cols.length) return "";
        String v = cols[idx];
        return "-0-".equals(v) ? "" : v;
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
