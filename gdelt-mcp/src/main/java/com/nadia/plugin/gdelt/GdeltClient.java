package com.nadia.plugin.gdelt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Locale;
import java.util.Objects;

/**
 * Client for querying the GDELT Project 2.0 DOC and GEO APIs for real-time global news articles,
 * media volume/tone timelines, and geocoded event locations.
 */
public class GdeltClient {

    private static final Logger logger = LoggerFactory.getLogger(GdeltClient.class);
    public static final String DEFAULT_DOC_API_URL = "https://api.gdeltproject.org/api/v2/doc/doc";
    public static final String DEFAULT_GEO_API_URL = "https://api.gdeltproject.org/api/v2/geo/geo";
    private static final int DEFAULT_MAX_RECORDS = 25;
    private static final int MAX_RECORDS = 250;

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a GDELT client backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public GdeltClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Searches global news articles via the GDELT DOC 2.0 API ({@code mode=artlist}).
     *
     * @param query         required search query expression
     * @param timespan      optional timespan (e.g., {@code 24h}, {@code 7d}, {@code 1m})
     * @param sourceCountry optional FIPS 2-letter source country filter (e.g., {@code US}, {@code UK}, {@code GM})
     * @param sourceLang    optional source language filter (e.g., {@code english}, {@code spanish})
     * @param theme         optional GDELT GKG theme (e.g., {@code CYBER_ATTACK}, {@code PROTEST})
     * @param sort          optional sort order ({@code DateDesc}, {@code HybridRel}, {@code ToneDesc}, {@code ToneAsc})
     * @param maxRecords    maximum articles to return (1..250, default 25)
     * @return structured JSON string with matching articles
     */
    public String searchArticles(String query,
                                 String timespan,
                                 String sourceCountry,
                                 String sourceLang,
                                 String theme,
                                 String sort,
                                 Integer maxRecords) {
        String builtQuery = composeQuery(query, sourceCountry, sourceLang, theme);
        if (builtQuery.isEmpty()) {
            return errorJson("Provide at least a query or theme to search GDELT articles.");
        }
        int max = clamp(maxRecords, DEFAULT_MAX_RECORDS, MAX_RECORDS);
        String baseUrl = getProp(GdeltPlugin.PROP_DOC_API_URL, DEFAULT_DOC_API_URL);
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?query=").append(encode(builtQuery))
                .append("&mode=artlist&format=json&maxrecords=").append(max);
        appendParam(url, "timespan", defaultIfBlank(timespan, "7d"));
        appendParam(url, "sort", defaultIfBlank(sort, "DateDesc"));
        return executeGet(url.toString(), "articles");
    }

    /**
     * Retrieves media coverage volume or tone timeline via the GDELT DOC 2.0 API.
     *
     * @param query    required search query expression
     * @param mode     timeline mode: {@code timelinevol} (default), {@code timelinetone}, or {@code timelinevolraw}
     * @param timespan optional timespan (e.g., {@code 24h}, {@code 7d}, {@code 1m}, {@code 3m})
     * @return structured JSON string with timeline series
     */
    public String getTimeline(String query, String mode, String timespan) {
        if (query == null || query.isBlank()) {
            return errorJson("query parameter is required for GDELT timeline analysis.");
        }
        String normalizedMode = normalizeTimelineMode(mode);
        String baseUrl = getProp(GdeltPlugin.PROP_DOC_API_URL, DEFAULT_DOC_API_URL);
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?query=").append(encode(query.trim()))
                .append("&mode=").append(normalizedMode)
                .append("&format=json");
        appendParam(url, "timespan", defaultIfBlank(timespan, "14d"));
        return executeGet(url.toString(), "timeline");
    }

    /**
     * Searches geocoded global event and news locations via the GDELT GEO 2.0 API.
     *
     * @param query     required search query expression
     * @param timespan  optional timespan (e.g., {@code 24h}, {@code 7d}, default {@code 7d})
     * @param maxPoints maximum geographic points to return (1..250, default 50)
     * @return structured JSON string with GeoJSON feature collection
     */
    public String searchGeoEvents(String query, String timespan, Integer maxPoints) {
        if (query == null || query.isBlank()) {
            return errorJson("query parameter is required for GDELT GEO search.");
        }
        int max = clamp(maxPoints, 50, MAX_RECORDS);
        String baseUrl = getProp(GdeltPlugin.PROP_GEO_API_URL, DEFAULT_GEO_API_URL);
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?query=").append(encode(query.trim()))
                .append("&mode=pointdata&format=geojson&maxpoints=").append(max);
        appendParam(url, "timespan", defaultIfBlank(timespan, "7d"));
        return executeGet(url.toString(), "geojson");
    }

    private String executeGet(String url, String payloadKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-GDELT-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("GDELT API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            String body = resp.body() != null ? resp.body().trim() : "";
            if (body.isEmpty()) {
                ObjectNode empty = objectMapper.createObjectNode();
                empty.put("status", "OK");
                empty.putArray(payloadKey);
                return objectMapper.writeValueAsString(empty);
            }
            if (!body.startsWith("{") && !body.startsWith("[")) {
                return errorJson("GDELT API returned non-JSON response: " + truncate(body, 300));
            }
            JsonNode parsed = objectMapper.readTree(body);
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            if (parsed.isObject() && parsed.has(payloadKey)) {
                out.set(payloadKey, parsed.get(payloadKey));
            } else {
                out.set(payloadKey, parsed);
            }
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[GdeltClient] GET {} failed: {}", url, e.getMessage());
            return errorJson("GDELT request failed: " + e.getMessage());
        }
    }

    private static String composeQuery(String query, String sourceCountry, String sourceLang, String theme) {
        StringBuilder sb = new StringBuilder();
        if (query != null && !query.isBlank()) {
            sb.append(query.trim());
        }
        if (theme != null && !theme.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("theme:").append(theme.trim().toUpperCase(Locale.ROOT));
        }
        if (sourceCountry != null && !sourceCountry.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("sourcecountry:").append(sourceCountry.trim().toUpperCase(Locale.ROOT));
        }
        if (sourceLang != null && !sourceLang.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("sourcelang:").append(sourceLang.trim().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String normalizeTimelineMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "timelinevol";
        }
        String m = mode.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "timelinetone", "tone" -> "timelinetone";
            case "timelinevolraw", "volraw", "raw" -> "timelinevolraw";
            default -> "timelinevol";
        };
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) {
            return defaultVal;
        }
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
    }

    private static void appendParam(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append('&').append(key).append('=').append(encode(value.trim()));
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String defaultIfBlank(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val.trim();
    }

    private static int clamp(Integer val, int defaultVal, int max) {
        if (val == null || val <= 0) {
            return defaultVal;
        }
        return Math.min(val, max);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s != null ? s : "";
        }
        return s.substring(0, maxLen) + "...";
    }

    private String errorJson(String message) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("status", "ERROR");
            node.put("message", message);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"Serialization error\"}";
        }
    }
}
