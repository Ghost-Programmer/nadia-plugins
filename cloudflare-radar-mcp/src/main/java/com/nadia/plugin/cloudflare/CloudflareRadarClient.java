package com.nadia.plugin.cloudflare;

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
 * Client for querying the Cloudflare Radar v4 API for Internet outages, traffic anomalies,
 * Layer 7 / Layer 3 DDoS attack telemetry, and BGP hijack/route-leak events.
 */
public class CloudflareRadarClient {

    private static final Logger logger = LoggerFactory.getLogger(CloudflareRadarClient.class);
    public static final String DEFAULT_API_URL = "https://api.cloudflare.com/client/v4/radar";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a Cloudflare Radar client backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public CloudflareRadarClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Retrieves curated Internet outage annotations from the Cloudflare Radar Outage Center.
     *
     * @param dateRange optional date range (e.g., {@code 1d}, {@code 7d}, {@code 30d})
     * @param location  optional ISO 3166-1 alpha-2 country code (e.g., {@code US}, {@code UA})
     * @param asn       optional Autonomous System Number filter
     * @param limit     maximum items to return (1..100, default 20)
     * @return JSON response string
     */
    public String getOutages(String dateRange, String location, String asn, Integer limit) {
        StringBuilder path = new StringBuilder("/annotations/outages?format=json&limit=")
                .append(clampLimit(limit));
        appendParam(path, "dateRange", defaultIfBlank(dateRange, "7d"));
        appendParam(path, "location", upper(location));
        appendParam(path, "asn", asn);
        return executeGet(path.toString());
    }

    /**
     * Retrieves detected Internet traffic anomalies (sudden drops or spikes).
     *
     * @param dateRange optional date range (e.g., {@code 1d}, {@code 7d}, {@code 30d})
     * @param location  optional ISO 3166-1 alpha-2 country code
     * @param asn       optional Autonomous System Number filter
     * @param status    optional anomaly status ({@code VERIFIED} or {@code UNVERIFIED})
     * @param limit     maximum items to return (1..100, default 20)
     * @return JSON response string
     */
    public String getTrafficAnomalies(String dateRange, String location, String asn, String status, Integer limit) {
        StringBuilder path = new StringBuilder("/traffic_anomalies?format=json&limit=")
                .append(clampLimit(limit));
        appendParam(path, "dateRange", defaultIfBlank(dateRange, "7d"));
        appendParam(path, "location", upper(location));
        appendParam(path, "asn", asn);
        appendParam(path, "status", upper(status));
        return executeGet(path.toString());
    }

    /**
     * Retrieves top origin or target locations for Layer 7 (HTTP/application-layer) attacks.
     *
     * @param direction {@code origin} or {@code target} (default {@code target})
     * @param dateRange optional date range (e.g., {@code 1d}, {@code 7d}, {@code 30d})
     * @param limit     maximum locations to return (1..100, default 10)
     * @return JSON response string
     */
    public String getLayer7Attacks(String direction, String dateRange, Integer limit) {
        String dir = "origin".equalsIgnoreCase(direction != null ? direction.trim() : "") ? "origin" : "target";
        int max = limit == null || limit <= 0 ? 10 : Math.min(limit, MAX_LIMIT);
        StringBuilder path = new StringBuilder("/attacks/layer7/top/locations/")
                .append(dir)
                .append("?format=json&limit=").append(max);
        appendParam(path, "dateRange", defaultIfBlank(dateRange, "7d"));
        return executeGet(path.toString());
    }

    /**
     * Retrieves top origin or target locations for Layer 3/4 (network-layer) DDoS attacks.
     *
     * @param direction {@code origin} or {@code target} (default {@code target})
     * @param dateRange optional date range (e.g., {@code 1d}, {@code 7d}, {@code 30d})
     * @param limit     maximum locations to return (1..100, default 10)
     * @return JSON response string
     */
    public String getLayer3Attacks(String direction, String dateRange, Integer limit) {
        String dir = "origin".equalsIgnoreCase(direction != null ? direction.trim() : "") ? "origin" : "target";
        int max = limit == null || limit <= 0 ? 10 : Math.min(limit, MAX_LIMIT);
        StringBuilder path = new StringBuilder("/attacks/layer3/top/locations/")
                .append(dir)
                .append("?format=json&limit=").append(max);
        appendParam(path, "dateRange", defaultIfBlank(dateRange, "7d"));
        return executeGet(path.toString());
    }

    /**
     * Retrieves BGP routing security events (hijacks or route leaks).
     *
     * @param eventType {@code hijacks} (default) or {@code leaks}
     * @param dateRange optional date range (e.g., {@code 1d}, {@code 7d}, {@code 30d})
     * @param asn       optional involved ASN filter
     * @param limit     maximum events to return (1..100, default 20)
     * @return JSON response string
     */
    public String getBgpEvents(String eventType, String dateRange, String asn, Integer limit) {
        String type = "leaks".equalsIgnoreCase(eventType != null ? eventType.trim() : "") ? "leaks" : "hijacks";
        StringBuilder path = new StringBuilder("/bgp/").append(type).append("/events?format=json&per_page=")
                .append(clampLimit(limit));
        appendParam(path, "dateRange", defaultIfBlank(dateRange, "7d"));
        appendParam(path, "involvedAsn", asn);
        return executeGet(path.toString());
    }

    private String executeGet(String relativePath) {
        String token = getProp(CloudflareRadarPlugin.PROP_API_TOKEN, "");
        if (token.isEmpty()) {
            return errorJson("Cloudflare Radar API token is not configured. Set "
                    + CloudflareRadarPlugin.PROP_API_TOKEN + " in Settings -> Properties.");
        }
        String baseUrl = getProp(CloudflareRadarPlugin.PROP_API_URL, DEFAULT_API_URL);
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = baseUrl + relativePath;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-CloudflareRadar-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("Cloudflare Radar API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 400));
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", parsed.path("success").asBoolean(true) ? "OK" : "ERROR");
            out.set("result", parsed.path("result"));
            if (parsed.has("errors") && parsed.path("errors").size() > 0) {
                out.set("errors", parsed.path("errors"));
            }
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[CloudflareRadarClient] GET {} failed: {}", relativePath, e.getMessage());
            return errorJson("Cloudflare Radar request failed: " + e.getMessage());
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
            sb.append('&').append(key).append('=').append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }

    private static String defaultIfBlank(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val.trim();
    }

    private static String upper(String val) {
        return (val == null || val.isBlank()) ? "" : val.trim().toUpperCase(Locale.ROOT);
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
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
