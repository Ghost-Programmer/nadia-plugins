package com.nadia.plugin.ucdp;

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
 * Client for querying the Uppsala Conflict Data Program (UCDP) GED API and the
 * UNHCR Refugee Population Statistics API.
 */
public class UcdpDisplacementClient {

    private static final Logger logger = LoggerFactory.getLogger(UcdpDisplacementClient.class);
    public static final String DEFAULT_UCDP_URL = "https://ucdpapi.pcr.uu.se/api/gedevents/24.1";
    public static final String DEFAULT_UNHCR_URL = "https://api.unhcr.org/population/v1/population/";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code UcdpDisplacementClient} backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public UcdpDisplacementClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Queries UCDP Georeferenced Event Dataset (GED) armed conflict events.
     *
     * @param country   optional country name or Gleditsch-Ward country ID
     * @param startDate optional start date (YYYY-MM-DD)
     * @param endDate   optional end date (YYYY-MM-DD)
     * @param pageSize  maximum events per page (1..100, default 25)
     * @return structured JSON string with UCDP GED events
     */
    public String searchGedEvents(String country, String startDate, String endDate, Integer pageSize) {
        int max = (pageSize == null || pageSize <= 0) ? 25 : Math.min(pageSize, 100);
        String base = getProp(UcdpDisplacementPlugin.PROP_UCDP_API_URL, DEFAULT_UCDP_URL);
        StringBuilder url = new StringBuilder(base).append("?pagesize=").append(max);
        appendParam(url, "Country", country);
        appendParam(url, "StartDate", startDate);
        appendParam(url, "EndDate", endDate);

        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-UCDP-MCP/1.0")
                    .GET();
            String token = getProp(UcdpDisplacementPlugin.PROP_UCDP_TOKEN, "");
            if (!token.isEmpty()) {
                req.header("x-ucdp-access-token", token);
            }
            HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("UCDP API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("totalCount", parsed.path("TotalCount").asInt(0));
            out.set("events", parsed.path("Result"));
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[UcdpDisplacementClient] searchGedEvents failed: {}", e.getMessage());
            return errorJson("UCDP request failed: " + e.getMessage());
        }
    }

    /**
     * Queries UNHCR refugee, asylum-seeker, and internally displaced population statistics.
     *
     * @param cooIso3 optional country of origin ISO-3 code (e.g., {@code UKR}, {@code SDN}, {@code SYR})
     * @param coaIso3 optional country of asylum ISO-3 code (e.g., {@code DEU}, {@code POL})
     * @param year    optional year (e.g., {@code 2024})
     * @param limit   maximum records to return (1..100, default 25)
     * @return structured JSON string with UNHCR displacement figures
     */
    public String getDisplacementStats(String cooIso3, String coaIso3, Integer year, Integer limit) {
        int max = (limit == null || limit <= 0) ? 25 : Math.min(limit, 100);
        String base = getProp(UcdpDisplacementPlugin.PROP_UNHCR_API_URL, DEFAULT_UNHCR_URL);
        StringBuilder url = new StringBuilder(base).append("?limit=").append(max);
        if (cooIso3 != null && !cooIso3.isBlank()) {
            appendParam(url, "coo", cooIso3.trim().toUpperCase(Locale.ROOT));
        }
        if (coaIso3 != null && !coaIso3.isBlank()) {
            appendParam(url, "coa", coaIso3.trim().toUpperCase(Locale.ROOT));
        }
        if (year != null && year > 1950) {
            url.append("&year=").append(year);
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-UNHCR-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("UNHCR API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            JsonNode items = parsed.path("items");
            out.put("count", items.isArray() ? items.size() : 0);
            out.set("items", items);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[UcdpDisplacementClient] getDisplacementStats failed: {}", e.getMessage());
            return errorJson("UNHCR request failed: " + e.getMessage());
        }
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) return defaultVal;
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
    }

    private static void appendParam(StringBuilder sb, String key, String val) {
        if (val != null && !val.isBlank()) {
            sb.append('&').append(key).append('=').append(URLEncoder.encode(val.trim(), StandardCharsets.UTF_8));
        }
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
