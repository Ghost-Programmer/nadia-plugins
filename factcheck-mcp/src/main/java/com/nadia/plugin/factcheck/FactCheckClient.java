package com.nadia.plugin.factcheck;

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
import java.util.Objects;

/**
 * Client for querying the Google Fact Check Tools API v1alpha1 for published fact-checks.
 */
public class FactCheckClient {

    private static final Logger logger = LoggerFactory.getLogger(FactCheckClient.class);
    private static final String API_URL = "https://factchecktools.googleapis.com/v1alpha1/claims:search";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code FactCheckClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public FactCheckClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Searches published fact-checks matching a claim or keyword.
     *
     * @param query        claim text or keywords to verify
     * @param languageCode optional BCP-47 language code (e.g., {@code en}, {@code es})
     * @param maxAgeDays   optional maximum age in days
     * @param pageSize     maximum claims to return (1..50, default 10)
     * @return structured JSON response with published claim reviews
     */
    public String searchClaims(String query, String languageCode, Integer maxAgeDays, Integer pageSize) {
        if (query == null || query.isBlank()) {
            return errorJson("query parameter is required.");
        }
        String key = context != null ? context.getProperty(FactCheckPlugin.PROP_API_KEY, "").trim() : "";
        if (key.isEmpty()) {
            return errorJson("Google Fact Check API key is not configured. Enable Fact Check Tools API in Google Cloud and set "
                    + FactCheckPlugin.PROP_API_KEY + " in Settings -> Properties.");
        }
        int max = (pageSize == null || pageSize <= 0) ? 10 : Math.min(pageSize, 50);
        StringBuilder url = new StringBuilder(API_URL)
                .append("?key=").append(encode(key))
                .append("&query=").append(encode(query.trim()))
                .append("&pageSize=").append(max);
        if (languageCode != null && !languageCode.isBlank()) {
            url.append("&languageCode=").append(encode(languageCode.trim()));
        }
        if (maxAgeDays != null && maxAgeDays > 0) {
            url.append("&maxAgeDays=").append(maxAgeDays);
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-FactCheck-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("Google Fact Check API returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            JsonNode claims = parsed.path("claims");
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("count", claims.isArray() ? claims.size() : 0);
            out.set("claims", claims);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[FactCheckClient] searchClaims failed: {}", e.getMessage());
            return errorJson("Fact Check request failed: " + e.getMessage());
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
