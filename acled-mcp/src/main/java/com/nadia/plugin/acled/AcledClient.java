package com.nadia.plugin.acled;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Client for querying the ACLED (Armed Conflict Location &amp; Event Data) API and ACLED CAST
 * conflict forecasting endpoint.
 *
 * <p>Supports both modern OAuth2 Bearer token authentication ({@code email} + {@code password} via
 * {@code https://acleddata.com/oauth/token} with cached token auto-refresh) and legacy
 * {@code email} + {@code apiKey} query parameter authentication.</p>
 */
public class AcledClient {

    private static final Logger logger = LoggerFactory.getLogger(AcledClient.class);
    public static final String DEFAULT_API_URL = "https://acleddata.com/api/acled/read";
    public static final String DEFAULT_CAST_URL = "https://acleddata.com/api/cast/read";
    public static final String OAUTH_TOKEN_URL = "https://acleddata.com/oauth/token";
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 500;

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedAccessToken;
    private volatile long tokenExpiryMs;

    /**
     * Constructs an ACLED client backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public AcledClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Searches ACLED conflict, protest, and political violence events.
     *
     * @param country      optional country name (e.g., {@code Ukraine}, {@code Sudan})
     * @param region       optional ACLED region name
     * @param eventType    optional event type (e.g., {@code Battles}, {@code Protests}, {@code Violence against civilians})
     * @param subEventType optional sub-event type
     * @param actor        optional actor name filter (matched with ACLED LIKE syntax)
     * @param startDate    optional ISO start date (YYYY-MM-DD)
     * @param endDate      optional ISO end date (YYYY-MM-DD)
     * @param limit        maximum events to return (1..500, default 25)
     * @return structured JSON string with matching conflict events
     */
    public String searchEvents(String country,
                               String region,
                               String eventType,
                               String subEventType,
                               String actor,
                               String startDate,
                               String endDate,
                               Integer limit) {
        int max = clampLimit(limit, DEFAULT_LIMIT);
        String baseUrl = getProp(AcledPlugin.PROP_API_URL, DEFAULT_API_URL);
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?_format=json&limit=").append(max);
        appendParam(url, "country", country);
        appendParam(url, "region", region);
        appendParam(url, "event_type", eventType);
        appendParam(url, "sub_event_type", subEventType);
        if (actor != null && !actor.isBlank()) {
            appendParam(url, "actor1", actor.trim());
            appendParam(url, "actor1_where", "LIKE");
        }
        applyDateRange(url, startDate, endDate);

        try {
            JsonNode parsed = sendAuthenticatedGet(url.toString());
            if (parsed.has("status") && "ERROR".equals(parsed.path("status").asText())) {
                return objectMapper.writeValueAsString(parsed);
            }
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            JsonNode data = parsed.path("data");
            out.put("count", data.isArray() ? data.size() : parsed.path("count").asInt(0));
            out.set("events", data);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[AcledClient] searchEvents failed: {}", e.getMessage());
            return errorJson("ACLED searchEvents failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves and aggregates conflict statistics (event count, total fatalities, breakdown by
     * event type, and top actors) for a country and optional date window.
     *
     * @param country   required country name
     * @param startDate optional ISO start date (YYYY-MM-DD)
     * @param endDate   optional ISO end date (YYYY-MM-DD)
     * @param limit     maximum events to sample for aggregation (default 250, max 500)
     * @return structured JSON summary string
     */
    public String getCountrySummary(String country, String startDate, String endDate, Integer limit) {
        if (country == null || country.isBlank()) {
            return errorJson("country parameter is required for ACLED country summary.");
        }
        int max = clampLimit(limit, 250);
        String baseUrl = getProp(AcledPlugin.PROP_API_URL, DEFAULT_API_URL);
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?_format=json&limit=").append(max);
        appendParam(url, "fields", "event_date|event_type|sub_event_type|actor1|fatalities|admin1");
        appendParam(url, "country", country);
        applyDateRange(url, startDate, endDate);

        try {
            JsonNode parsed = sendAuthenticatedGet(url.toString());
            if (parsed.has("status") && "ERROR".equals(parsed.path("status").asText())) {
                return objectMapper.writeValueAsString(parsed);
            }
            JsonNode data = parsed.path("data");
            int totalEvents = 0;
            int totalFatalities = 0;
            Map<String, Integer> byType = new LinkedHashMap<>();
            Map<String, Integer> byActor = new LinkedHashMap<>();
            Map<String, Integer> byAdmin1 = new LinkedHashMap<>();

            if (data.isArray()) {
                for (JsonNode ev : data) {
                    totalEvents++;
                    totalFatalities += parseIntSafe(ev.path("fatalities").asText("0"));
                    incrementCount(byType, ev.path("event_type").asText(""));
                    incrementCount(byActor, ev.path("actor1").asText(""));
                    incrementCount(byAdmin1, ev.path("admin1").asText(""));
                }
            }

            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("country", country.trim());
            out.put("sampledEvents", totalEvents);
            out.put("totalFatalities", totalFatalities);
            out.set("eventTypes", topCounts(byType, 10));
            out.set("topActors", topCounts(byActor, 10));
            out.set("topAdminRegions", topCounts(byAdmin1, 10));
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[AcledClient] getCountrySummary failed: {}", e.getMessage());
            return errorJson("ACLED getCountrySummary failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves ACLED Conflict Alert System (CAST) predicted conflict event forecasts.
     *
     * @param country optional country filter
     * @param admin1  optional first-level administrative region filter
     * @param limit   maximum forecast rows (1..500, default 25)
     * @return structured JSON string with CAST forecast records
     */
    public String getCastForecast(String country, String admin1, Integer limit) {
        int max = clampLimit(limit, DEFAULT_LIMIT);
        StringBuilder url = new StringBuilder(DEFAULT_CAST_URL)
                .append("?_format=json&limit=").append(max);
        appendParam(url, "country", country);
        appendParam(url, "admin1", admin1);

        try {
            JsonNode parsed = sendAuthenticatedGet(url.toString());
            if (parsed.has("status") && "ERROR".equals(parsed.path("status").asText())) {
                return objectMapper.writeValueAsString(parsed);
            }
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            JsonNode data = parsed.path("data");
            out.put("count", data.isArray() ? data.size() : 0);
            out.set("forecasts", data);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[AcledClient] getCastForecast failed: {}", e.getMessage());
            return errorJson("ACLED CAST forecast failed: " + e.getMessage());
        }
    }

    private JsonNode sendAuthenticatedGet(String baseUrlWithParams) throws IOException, InterruptedException {
        String email = getProp(AcledPlugin.PROP_EMAIL, "");
        String password = getProp(AcledPlugin.PROP_PASSWORD, "");
        String apiKey = getProp(AcledPlugin.PROP_API_KEY, "");

        if (email.isEmpty() || (password.isEmpty() && apiKey.isEmpty())) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("status", "ERROR");
            err.put("message", "ACLED credentials are not configured. Set " + AcledPlugin.PROP_EMAIL
                    + " and " + AcledPlugin.PROP_PASSWORD + " (OAuth) or " + AcledPlugin.PROP_API_KEY
                    + " (legacy key) in Settings -> Properties.");
            return err;
        }

        HttpRequest.Builder reqBuilder;
        if (!password.isEmpty()) {
            String token = getOrFetchOAuthToken(email, password);
            reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrlWithParams))
                    .header("Authorization", "Bearer " + token);
        } else {
            String withLegacyAuth = baseUrlWithParams
                    + "&email=" + encode(email)
                    + "&key=" + encode(apiKey);
            reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(withLegacyAuth));
        }

        HttpRequest req = reqBuilder
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "Nadia-ACLED-MCP/1.0")
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 401 && !password.isEmpty()) {
            // Invalidate cached OAuth token and retry once
            cachedAccessToken = null;
            tokenExpiryMs = 0L;
            String refreshedToken = getOrFetchOAuthToken(email, password);
            HttpRequest retryReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrlWithParams))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + refreshedToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ACLED-MCP/1.0")
                    .GET()
                    .build();
            resp = httpClient.send(retryReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("status", "ERROR");
            err.put("message", "ACLED API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 350));
            return err;
        }
        return objectMapper.readTree(resp.body());
    }

    private synchronized String getOrFetchOAuthToken(String email, String password) throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < tokenExpiryMs) {
            return cachedAccessToken;
        }

        String formBody = "grant_type=password&client_id=acled"
                + "&username=" + encode(email)
                + "&password=" + encode(password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(OAUTH_TOKEN_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "Nadia-ACLED-MCP/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("ACLED OAuth token request failed with HTTP " + resp.statusCode());
        }
        JsonNode json = objectMapper.readTree(resp.body());
        String accessToken = json.path("access_token").asText("");
        if (accessToken.isEmpty()) {
            throw new IOException("ACLED OAuth response did not include access_token");
        }
        long expiresInSec = json.path("expires_in").asLong(3600L);
        this.cachedAccessToken = accessToken;
        this.tokenExpiryMs = now + Math.max(60L, expiresInSec - 60L) * 1000L;
        return accessToken;
    }

    private static void applyDateRange(StringBuilder url, String startDate, String endDate) {
        boolean hasStart = startDate != null && !startDate.isBlank();
        boolean hasEnd = endDate != null && !endDate.isBlank();
        if (hasStart && hasEnd) {
            appendParam(url, "event_date", startDate.trim() + "|" + endDate.trim());
            appendParam(url, "event_date_where", "BETWEEN");
        } else if (hasStart) {
            appendParam(url, "event_date", startDate.trim());
            appendParam(url, "event_date_where", ">=");
        } else if (hasEnd) {
            appendParam(url, "event_date", endDate.trim());
            appendParam(url, "event_date_where", "<=");
        }
    }

    private ArrayNode topCounts(Map<String, Integer> counts, int max) {
        ArrayNode arr = objectMapper.createArrayNode();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(max)
                .forEach(e -> {
                    ObjectNode item = arr.addObject();
                    item.put("name", e.getKey());
                    item.put("count", e.getValue());
                });
        return arr;
    }

    private static void incrementCount(Map<String, Integer> map, String key) {
        if (key != null && !key.isBlank()) {
            map.merge(key.trim(), 1, Integer::sum);
        }
    }

    private static int parseIntSafe(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 0;
        }
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

    private static int clampLimit(Integer limit, int defaultVal) {
        if (limit == null || limit <= 0) {
            return defaultVal;
        }
        return Math.min(limit, MAX_LIMIT);
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
