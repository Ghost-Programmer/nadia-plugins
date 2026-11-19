package com.nadia.plugin.macrotrade;

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
 * Client for querying FRED macroeconomic series, IMF DataMapper global economic indicators,
 * and WTO Timeseries trade &amp; tariff data.
 */
public class MacroTradeClient {

    private static final Logger logger = LoggerFactory.getLogger(MacroTradeClient.class);
    private static final String FRED_OBS_URL = "https://api.stlouisfed.org/fred/series/observations";
    private static final String IMF_DATAMAPPER_BASE = "https://www.imf.org/external/datamapper/api/v1";
    private static final String WTO_DATA_URL = "https://api.wto.org/timeseries/v1/data";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code MacroTradeClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public MacroTradeClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Retrieves observations for a FRED macroeconomic series (e.g., {@code CPIAUCSL}, {@code DFF}, {@code GDP}, {@code UNRATE}).
     *
     * @param seriesId         FRED series ID
     * @param observationStart optional ISO start date (YYYY-MM-DD)
     * @param limit            maximum observations to return (1..200, default 24)
     * @return structured JSON string with FRED observations
     */
    public String getFredSeries(String seriesId, String observationStart, Integer limit) {
        if (seriesId == null || seriesId.isBlank()) {
            return errorJson("seriesId parameter is required (e.g., CPIAUCSL, DFF, GDP, UNRATE).");
        }
        String key = getProp(MacroTradePlugin.PROP_FRED_API_KEY);
        if (key.isEmpty()) {
            return errorJson("FRED API key is not configured. Register free at https://fred.stlouisfed.org/docs/api/api_key.html and set "
                    + MacroTradePlugin.PROP_FRED_API_KEY + " in Settings -> Properties.");
        }
        int max = (limit == null || limit <= 0) ? 24 : Math.min(limit, 200);
        StringBuilder url = new StringBuilder(FRED_OBS_URL)
                .append("?series_id=").append(encode(seriesId.trim().toUpperCase(Locale.ROOT)))
                .append("&api_key=").append(encode(key))
                .append("&file_type=json&sort_order=desc&limit=").append(max);
        if (observationStart != null && !observationStart.isBlank()) {
            url.append("&observation_start=").append(encode(observationStart.trim()));
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-MacroTrade-MCP/1.0")
                    .GET()
                    .build();
            return sendJson(req, "FRED");
        } catch (Exception e) {
            logger.warn("[MacroTradeClient] getFredSeries failed: {}", e.getMessage());
            return errorJson("FRED request failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves global macroeconomic indicators from the IMF DataMapper API (e.g., {@code NGDP_RPCH}, {@code PCPIPCH}, {@code GGXWDG_NGDP}).
     *
     * @param indicator   IMF indicator code (e.g., {@code NGDP_RPCH} for real GDP growth)
     * @param countryIso3 optional ISO-3 country code (e.g., {@code USA}, {@code DEU}, {@code CHN})
     * @return structured JSON response
     */
    public String getImfIndicator(String indicator, String countryIso3) {
        if (indicator == null || indicator.isBlank()) {
            return errorJson("indicator parameter is required (e.g., NGDP_RPCH, PCPIPCH, GGXWDG_NGDP).");
        }
        StringBuilder url = new StringBuilder(IMF_DATAMAPPER_BASE)
                .append('/').append(encode(indicator.trim().toUpperCase(Locale.ROOT)));
        if (countryIso3 != null && !countryIso3.isBlank()) {
            url.append('/').append(encode(countryIso3.trim().toUpperCase(Locale.ROOT)));
        }
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-MacroTrade-MCP/1.0")
                    .GET();
            String key = getProp(MacroTradePlugin.PROP_IMF_API_KEY);
            if (!key.isEmpty()) {
                req.header("Ocp-Apim-Subscription-Key", key);
            }
            return sendJson(req.build(), "IMF");
        } catch (Exception e) {
            logger.warn("[MacroTradeClient] getImfIndicator failed: {}", e.getMessage());
            return errorJson("IMF request failed: " + e.getMessage());
        }
    }

    /**
     * Queries WTO Timeseries API for bilateral trade or applied tariff indicators.
     *
     * @param indicatorCode WTO indicator code (e.g., {@code TP_A_0010} for MFN simple average tariff, {@code ITS_MTV_AX} for exports)
     * @param reporterCode  WTO reporter numeric/ISO code (e.g., {@code 840} for USA, {@code 156} for China)
     * @param period        optional year or range (e.g., {@code 2022-2024})
     * @return structured JSON response
     */
    public String getWtoTradeData(String indicatorCode, String reporterCode, String period) {
        String key = getProp(MacroTradePlugin.PROP_WTO_API_KEY);
        if (key.isEmpty()) {
            return errorJson("WTO API key is not configured. Register at https://apiportal.wto.org/ and set "
                    + MacroTradePlugin.PROP_WTO_API_KEY + " in Settings -> Properties.");
        }
        String ind = (indicatorCode == null || indicatorCode.isBlank()) ? "TP_A_0010" : indicatorCode.trim();
        StringBuilder url = new StringBuilder(WTO_DATA_URL)
                .append("?i=").append(encode(ind))
                .append("&max=50");
        if (reporterCode != null && !reporterCode.isBlank()) {
            url.append("&r=").append(encode(reporterCode.trim()));
        }
        if (period != null && !period.isBlank()) {
            url.append("&ps=").append(encode(period.trim()));
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-MacroTrade-MCP/1.0")
                    .GET()
                    .build();
            return sendJson(req, "WTO");
        } catch (Exception e) {
            logger.warn("[MacroTradeClient] getWtoTradeData failed: {}", e.getMessage());
            return errorJson("WTO request failed: " + e.getMessage());
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
