package com.nadia.plugin.polymarket;

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
import java.util.Locale;
import java.util.Objects;

/**
 * Client for retrieving geopolitical and macroeconomic prediction market odds from
 * Polymarket's Gamma API and Metaculus's public forecasting API.
 */
public class PolymarketClient {

    private static final Logger logger = LoggerFactory.getLogger(PolymarketClient.class);
    public static final String DEFAULT_POLYMARKET_URL = "https://gamma-api.polymarket.com";
    public static final String DEFAULT_METACULUS_URL = "https://www.metaculus.com/api2";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code PolymarketClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public PolymarketClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Searches active Polymarket prediction markets and returns current implied probabilities and volume.
     *
     * @param query      optional keyword filter across market question/description
     * @param activeOnly when {@code true} (default), returns only open/active markets
     * @param limit      maximum markets to return (1..50, default 15)
     * @return structured JSON string with matching markets
     */
    public String searchMarkets(String query, Boolean activeOnly, Integer limit) {
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 50);
        boolean active = activeOnly == null || activeOnly;
        String base = getProp(PolymarketPlugin.PROP_POLYMARKET_API_URL, DEFAULT_POLYMARKET_URL);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        int fetchLimit = (query != null && !query.isBlank()) ? Math.min(200, max * 5) : max;
        String url = base + "/markets?closed=" + (!active) + "&order=volume24hr&ascending=false&limit=" + fetchLimit;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-Polymarket-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("Polymarket API returned HTTP " + resp.statusCode());
            }
            JsonNode arr = objectMapper.readTree(resp.body());
            String qLower = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            ArrayNode markets = out.putArray("markets");
            if (arr.isArray()) {
                for (JsonNode m : arr) {
                    String question = m.path("question").asText("");
                    String desc = m.path("description").asText("");
                    if (!qLower.isEmpty() && !(question + " " + desc).toLowerCase(Locale.ROOT).contains(qLower)) {
                        continue;
                    }
                    ObjectNode item = markets.addObject();
                    item.put("id", m.path("id").asText(""));
                    item.put("slug", m.path("slug").asText(""));
                    item.put("question", question);
                    item.put("outcomes", m.path("outcomes").asText(""));
                    item.put("outcomePrices", m.path("outcomePrices").asText(""));
                    item.put("volume24hr", m.path("volume24hr").asDouble(0.0));
                    item.put("endDate", m.path("endDate").asText(""));
                    if (markets.size() >= max) break;
                }
            }
            out.put("count", markets.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[PolymarketClient] searchMarkets failed: {}", e.getMessage());
            return errorJson("Polymarket request failed: " + e.getMessage());
        }
    }

    /**
     * Searches Metaculus community forecasting questions.
     *
     * @param query  optional keyword search
     * @param status optional status ({@code open} default, {@code closed}, {@code resolved})
     * @param limit  maximum questions to return (1..50, default 15)
     * @return structured JSON string with Metaculus questions
     */
    public String searchMetaculus(String query, String status, Integer limit) {
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 50);
        String base = getProp(PolymarketPlugin.PROP_METACULUS_API_URL, DEFAULT_METACULUS_URL);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        StringBuilder url = new StringBuilder(base).append("/questions/?limit=").append(max);
        url.append("&status=").append(encode(status == null || status.isBlank() ? "open" : status.trim().toLowerCase(Locale.ROOT)));
        if (query != null && !query.isBlank()) {
            url.append("&search=").append(encode(query.trim()));
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-Metaculus-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("Metaculus API returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            JsonNode results = parsed.path("results");
            out.put("count", results.isArray() ? results.size() : 0);
            out.set("questions", results);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[PolymarketClient] searchMetaculus failed: {}", e.getMessage());
            return errorJson("Metaculus request failed: " + e.getMessage());
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
