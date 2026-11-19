package com.nadia.plugin.energy;

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
 * Client for querying U.S. EIA v2 energy series, GIE AGSI+ European gas storage levels,
 * and ENTSO-E European power grid load data.
 */
public class EnergyGridClient {

    private static final Logger logger = LoggerFactory.getLogger(EnergyGridClient.class);
    private static final String EIA_BASE = "https://api.eia.gov/v2";
    private static final String GIE_AGSI_URL = "https://agsi.gie.eu/api";
    private static final String ENTSOE_URL = "https://web-api.tp.entsoe.eu/api";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs an {@code EnergyGridClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public EnergyGridClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Queries a U.S. EIA v2 energy data route (e.g., {@code petroleum/pri/spt/data} for WTI/Brent spot prices).
     *
     * @param route     EIA v2 route path (default {@code petroleum/pri/spt/data})
     * @param frequency optional frequency ({@code daily}, {@code weekly}, {@code monthly})
     * @param length    maximum data rows (1..100, default 20)
     * @return structured JSON response
     */
    public String getEiaSeries(String route, String frequency, Integer length) {
        String key = getProp(EnergyGridPlugin.PROP_EIA_API_KEY);
        if (key.isEmpty()) {
            return errorJson("EIA API key is not configured. Register free at https://www.eia.gov/opendata/ and set "
                    + EnergyGridPlugin.PROP_EIA_API_KEY + " in Settings -> Properties.");
        }
        String cleanRoute = (route == null || route.isBlank()) ? "petroleum/pri/spt/data" : route.trim();
        if (cleanRoute.startsWith("/")) cleanRoute = cleanRoute.substring(1);
        int max = (length == null || length <= 0) ? 20 : Math.min(length, 100);
        StringBuilder url = new StringBuilder(EIA_BASE).append('/').append(cleanRoute)
                .append("?api_key=").append(encode(key))
                .append("&data[0]=value&sort[0][column]=period&sort[0][direction]=desc&length=").append(max);
        if (frequency != null && !frequency.isBlank()) {
            url.append("&frequency=").append(encode(frequency.trim().toLowerCase(Locale.ROOT)));
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-EnergyGrid-MCP/1.0")
                    .GET()
                    .build();
            return sendJson(req, "EIA");
        } catch (Exception e) {
            logger.warn("[EnergyGridClient] getEiaSeries failed: {}", e.getMessage());
            return errorJson("EIA request failed: " + e.getMessage());
        }
    }

    /**
     * Queries European Union natural gas storage fill levels from GIE AGSI+.
     *
     * @param country optional ISO-2 country code (e.g., {@code DE}, {@code FR}, {@code EU} for EU-wide)
     * @param size    maximum daily rows (1..100, default 10)
     * @return structured JSON response
     */
    public String getGieEuGasStorage(String country, Integer size) {
        String key = getProp(EnergyGridPlugin.PROP_GIE_API_KEY);
        if (key.isEmpty()) {
            return errorJson("GIE AGSI+ API key is not configured. Register at https://agsi.gie.eu/ and set "
                    + EnergyGridPlugin.PROP_GIE_API_KEY + " in Settings -> Properties.");
        }
        int max = (size == null || size <= 0) ? 10 : Math.min(size, 100);
        StringBuilder url = new StringBuilder(GIE_AGSI_URL).append("?size=").append(max);
        if (country != null && !country.isBlank()) {
            String cc = country.trim().toUpperCase(Locale.ROOT);
            if ("EU".equals(cc)) {
                url.append("&type=eu");
            } else {
                url.append("&country=").append(encode(cc));
            }
        } else {
            url.append("&type=eu");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("x-key", key)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-EnergyGrid-MCP/1.0")
                    .GET()
                    .build();
            return sendJson(req, "GIE AGSI+");
        } catch (Exception e) {
            logger.warn("[EnergyGridClient] getGieEuGasStorage failed: {}", e.getMessage());
            return errorJson("GIE AGSI+ request failed: " + e.getMessage());
        }
    }

    /**
     * Queries European electrical grid actual total load (documentType A65) from ENTSO-E.
     *
     * @param biddingZoneEic ENTSO-E EIC code (e.g., {@code 10Y1001A1001A83F} for Germany)
     * @param periodStart    UTC period start in {@code yyyyMMddHHmm} format
     * @param periodEnd      UTC period end in {@code yyyyMMddHHmm} format
     * @return structured JSON response containing summary/XML payload
     */
    public String getEntsoeGridLoad(String biddingZoneEic, String periodStart, String periodEnd) {
        String token = getProp(EnergyGridPlugin.PROP_ENTSOE_TOKEN);
        if (token.isEmpty()) {
            return errorJson("ENTSO-E security token is not configured. Set " + EnergyGridPlugin.PROP_ENTSOE_TOKEN + " in Settings -> Properties.");
        }
        if (biddingZoneEic == null || biddingZoneEic.isBlank() || periodStart == null || periodStart.isBlank() || periodEnd == null || periodEnd.isBlank()) {
            return errorJson("biddingZoneEic, periodStart (yyyyMMddHHmm), and periodEnd (yyyyMMddHHmm) are required.");
        }
        String url = ENTSOE_URL + "?securityToken=" + encode(token)
                + "&documentType=A65&processType=A16"
                + "&outBiddingZone_Domain=" + encode(biddingZoneEic.trim())
                + "&periodStart=" + encode(periodStart.trim())
                + "&periodEnd=" + encode(periodEnd.trim());
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/xml")
                    .header("User-Agent", "Nadia-EnergyGrid-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("ENTSO-E API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("source", "ENTSO-E");
            out.put("biddingZoneEic", biddingZoneEic.trim());
            out.put("xml", truncate(resp.body(), 6000));
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[EnergyGridClient] getEntsoeGridLoad failed: {}", e.getMessage());
            return errorJson("ENTSO-E request failed: " + e.getMessage());
        }
    }

    private String sendJson(HttpRequest req, String source) throws Exception {
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return errorJson(source + " returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }
        JsonNode parsed = objectMapper.readTree(resp.body());
        ObjectNode out = objectMapper.createObjectNode();
        out.put("status", "OK");
        out.put("source", source);
        out.set("data", parsed);
        return objectMapper.writeValueAsString(out);
    }

    private String getProp(String key) {
        if (context == null) return "";
        String val = context.getProperty(key, "");
        return val != null ? val.trim() : "";
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
