package com.nadia.plugin.reliefweb;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client for querying the UN OCHA ReliefWeb v2 API for humanitarian situation reports,
 * disaster emergencies, and country crisis profiles.
 */
public class ReliefWebClient {

    private static final Logger logger = LoggerFactory.getLogger(ReliefWebClient.class);
    public static final String DEFAULT_API_URL = "https://api.reliefweb.int/v2";
    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_LIMIT = 100;

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a ReliefWeb client backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public ReliefWebClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Searches UN OCHA ReliefWeb humanitarian reports.
     *
     * @param query    optional full-text search query
     * @param country  optional country name or ISO3 filter
     * @param disaster optional disaster name or GLIDE code filter
     * @param limit    maximum reports to return (1..100, default 15)
     * @return structured JSON string with matching reports
     */
    public String searchReports(String query, String country, String disaster, Integer limit) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("limit", clampLimit(limit));
        payload.put("preset", "latest");

        if (query != null && !query.isBlank()) {
            ObjectNode q = payload.putObject("query");
            q.put("value", query.trim());
        }

        List<ObjectNode> conditions = new ArrayList<>();
        if (country != null && !country.isBlank()) {
            ObjectNode c = objectMapper.createObjectNode();
            c.put("field", "country.name");
            c.put("value", country.trim());
            conditions.add(c);
        }
        if (disaster != null && !disaster.isBlank()) {
            ObjectNode d = objectMapper.createObjectNode();
            d.put("field", "disaster.name");
            d.put("value", disaster.trim());
            conditions.add(d);
        }
        applyFilterConditions(payload, conditions);

        ObjectNode fields = payload.putObject("fields");
        ArrayNode include = fields.putArray("include");
        include.add("title").add("url").add("date.created").add("source.name")
                .add("country.name").add("disaster.name").add("format.name");

        return executePost("/reports", payload);
    }

    /**
     * Retrieves full details (including body text) for a single ReliefWeb report by ID.
     *
     * @param reportId ReliefWeb report numeric ID
     * @return structured JSON string with full report record
     */
    public String getReportById(String reportId) {
        if (reportId == null || reportId.isBlank()) {
            return errorJson("reportId parameter is required.");
        }
        return executeGet("/reports/" + URLEncoder.encode(reportId.trim(), StandardCharsets.UTF_8));
    }

    /**
     * Searches ReliefWeb disaster records (emergencies, earthquakes, floods, conflicts, epidemics).
     *
     * @param query   optional full-text search query
     * @param country optional country name filter
     * @param status  optional status filter ({@code current}, {@code alert}, {@code past})
     * @param limit   maximum disasters to return (1..100, default 15)
     * @return structured JSON string with matching disasters
     */
    public String searchDisasters(String query, String country, String status, Integer limit) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("limit", clampLimit(limit));
        payload.put("preset", "latest");

        if (query != null && !query.isBlank()) {
            ObjectNode q = payload.putObject("query");
            q.put("value", query.trim());
        }

        List<ObjectNode> conditions = new ArrayList<>();
        if (country != null && !country.isBlank()) {
            ObjectNode c = objectMapper.createObjectNode();
            c.put("field", "country.name");
            c.put("value", country.trim());
            conditions.add(c);
        }
        if (status != null && !status.isBlank()) {
            ObjectNode s = objectMapper.createObjectNode();
            s.put("field", "status");
            s.put("value", status.trim().toLowerCase());
            conditions.add(s);
        }
        applyFilterConditions(payload, conditions);

        ObjectNode fields = payload.putObject("fields");
        ArrayNode include = fields.putArray("include");
        include.add("name").add("glide").add("status").add("url")
                .add("date.event").add("country.name").add("type.name");

        return executePost("/disasters", payload);
    }

    /**
     * Retrieves full details for a single ReliefWeb disaster by ID.
     *
     * @param disasterId ReliefWeb disaster numeric ID
     * @return structured JSON string with full disaster details
     */
    public String getDisasterById(String disasterId) {
        if (disasterId == null || disasterId.isBlank()) {
            return errorJson("disasterId parameter is required.");
        }
        return executeGet("/disasters/" + URLEncoder.encode(disasterId.trim(), StandardCharsets.UTF_8));
    }

    /**
     * Searches ReliefWeb country crisis profiles.
     *
     * @param query  optional country name or ISO code search
     * @param status optional humanitarian status filter ({@code ongoing}, {@code normal})
     * @param limit  maximum countries to return (1..100, default 15)
     * @return structured JSON string with matching country profiles
     */
    public String searchCountries(String query, String status, Integer limit) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("limit", clampLimit(limit));

        if (query != null && !query.isBlank()) {
            ObjectNode q = payload.putObject("query");
            q.put("value", query.trim());
        }

        List<ObjectNode> conditions = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            ObjectNode s = objectMapper.createObjectNode();
            s.put("field", "status");
            s.put("value", status.trim().toLowerCase());
            conditions.add(s);
        }
        applyFilterConditions(payload, conditions);

        ObjectNode fields = payload.putObject("fields");
        ArrayNode include = fields.putArray("include");
        include.add("name").add("iso3").add("status").add("url").add("description");

        return executePost("/countries", payload);
    }

    private void applyFilterConditions(ObjectNode payload, List<ObjectNode> conditions) {
        if (conditions.size() == 1) {
            payload.set("filter", conditions.get(0));
        } else if (conditions.size() > 1) {
            ObjectNode filter = payload.putObject("filter");
            filter.put("operator", "AND");
            ArrayNode arr = filter.putArray("conditions");
            for (ObjectNode cond : conditions) {
                arr.add(cond);
            }
        }
    }

    private String executePost(String endpointPath, ObjectNode body) {
        String appname = getProp(ReliefWebPlugin.PROP_APPNAME, "");
        if (appname.isEmpty()) {
            return errorJson("ReliefWeb appname is not configured. Request a free appname from ReliefWeb and set "
                    + ReliefWebPlugin.PROP_APPNAME + " in Settings -> Properties.");
        }
        String url = buildUrl(endpointPath, appname);
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ReliefWeb-MCP/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            return sendAndFormat(req);
        } catch (Exception e) {
            logger.warn("[ReliefWebClient] POST {} failed: {}", endpointPath, e.getMessage());
            return errorJson("ReliefWeb request failed: " + e.getMessage());
        }
    }

    private String executeGet(String endpointPath) {
        String appname = getProp(ReliefWebPlugin.PROP_APPNAME, "");
        if (appname.isEmpty()) {
            return errorJson("ReliefWeb appname is not configured. Request a free appname from ReliefWeb and set "
                    + ReliefWebPlugin.PROP_APPNAME + " in Settings -> Properties.");
        }
        String url = buildUrl(endpointPath, appname);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ReliefWeb-MCP/1.0")
                    .GET()
                    .build();
            return sendAndFormat(req);
        } catch (Exception e) {
            logger.warn("[ReliefWebClient] GET {} failed: {}", endpointPath, e.getMessage());
            return errorJson("ReliefWeb request failed: " + e.getMessage());
        }
    }

    private String sendAndFormat(HttpRequest req) throws Exception {
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return errorJson("ReliefWeb API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 400));
        }
        JsonNode parsed = objectMapper.readTree(resp.body());
        ObjectNode out = objectMapper.createObjectNode();
        out.put("status", "OK");
        out.put("totalCount", parsed.path("totalCount").asInt(parsed.path("count").asInt(0)));
        out.put("count", parsed.path("count").asInt(0));
        out.set("data", parsed.path("data"));
        return objectMapper.writeValueAsString(out);
    }

    private String buildUrl(String endpointPath, String appname) {
        String base = getProp(ReliefWebPlugin.PROP_API_URL, DEFAULT_API_URL);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + endpointPath + "?appname=" + URLEncoder.encode(appname, StandardCharsets.UTF_8);
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) {
            return defaultVal;
        }
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
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
