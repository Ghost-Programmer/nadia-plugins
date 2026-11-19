package com.nadia.plugin.threatintel;

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
 * Client for querying NIST NVD CVE 2.0, AbuseIPDB v2, AlienVault OTX v1, and abuse.ch URLhaus.
 */
public class ThreatIntelClient {

    private static final Logger logger = LoggerFactory.getLogger(ThreatIntelClient.class);
    private static final String NVD_BASE = "https://services.nvd.nist.gov/rest/json/cves/2.0";
    private static final String ABUSEIPDB_URL = "https://api.abuseipdb.com/api/v2/check";
    private static final String OTX_BASE = "https://otx.alienvault.com/api/v1/indicators";
    private static final String URLHAUS_HOST_URL = "https://urlhaus-api.abuse.ch/v1/host/";
    private static final String URLHAUS_URL_URL = "https://urlhaus-api.abuse.ch/v1/url/";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a {@code ThreatIntelClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public ThreatIntelClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Looks up a CVE or searches CVEs by keyword in the NIST National Vulnerability Database (NVD).
     *
     * @param cveId   optional exact CVE ID (e.g., {@code CVE-2024-3400})
     * @param keyword optional keyword search if {@code cveId} is omitted
     * @param limit   max CVEs to return (1..50, default 10)
     * @return structured JSON string with NVD CVE records
     */
    public String getNvdCve(String cveId, String keyword, Integer limit) {
        int max = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        StringBuilder url = new StringBuilder(NVD_BASE).append("?resultsPerPage=").append(max);
        if (cveId != null && !cveId.isBlank()) {
            url.append("&cveId=").append(encode(cveId.trim().toUpperCase(Locale.ROOT)));
        } else if (keyword != null && !keyword.isBlank()) {
            url.append("&keywordSearch=").append(encode(keyword.trim()));
        } else {
            return errorJson("Provide either cveId or keyword for NVD lookup.");
        }

        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ThreatIntel-MCP/1.0")
                    .GET();
            String key = getProp(ThreatIntelPlugin.PROP_NVD_API_KEY);
            if (!key.isEmpty()) {
                req.header("apiKey", key);
            }
            return sendAndWrap(req.build(), "NVD");
        } catch (Exception e) {
            logger.warn("[ThreatIntelClient] getNvdCve failed: {}", e.getMessage());
            return errorJson("NVD lookup failed: " + e.getMessage());
        }
    }

    /**
     * Checks IP reputation and abuse confidence score via AbuseIPDB v2.
     *
     * @param ipAddress IPv4 or IPv6 address
     * @param maxAgeDays lookback window in days (1..365, default 90)
     * @return structured JSON response
     */
    public String checkAbuseIp(String ipAddress, Integer maxAgeDays) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return errorJson("ipAddress parameter is required.");
        }
        String key = getProp(ThreatIntelPlugin.PROP_ABUSEIPDB_API_KEY);
        if (key.isEmpty()) {
            return errorJson("AbuseIPDB API key is not configured. Set " + ThreatIntelPlugin.PROP_ABUSEIPDB_API_KEY + " in Settings -> Properties.");
        }
        int days = (maxAgeDays == null || maxAgeDays <= 0) ? 90 : Math.min(maxAgeDays, 365);
        String url = ABUSEIPDB_URL + "?ipAddress=" + encode(ipAddress.trim()) + "&maxAgeInDays=" + days + "&verbose";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Key", key)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ThreatIntel-MCP/1.0")
                    .GET()
                    .build();
            return sendAndWrap(req, "AbuseIPDB");
        } catch (Exception e) {
            logger.warn("[ThreatIntelClient] checkAbuseIp failed: {}", e.getMessage());
            return errorJson("AbuseIPDB request failed: " + e.getMessage());
        }
    }

    /**
     * Queries AlienVault OTX for threat intelligence pulses associated with an indicator.
     *
     * @param indicatorType {@code IPv4}, {@code IPv6}, {@code domain}, {@code hostname}, or {@code file}
     * @param indicator     indicator value (IP, domain, or SHA256/MD5 hash)
     * @return structured JSON response
     */
    public String getOtxIndicator(String indicatorType, String indicator) {
        if (indicator == null || indicator.isBlank()) {
            return errorJson("indicator parameter is required.");
        }
        String key = getProp(ThreatIntelPlugin.PROP_OTX_API_KEY);
        if (key.isEmpty()) {
            return errorJson("AlienVault OTX API key is not configured. Set " + ThreatIntelPlugin.PROP_OTX_API_KEY + " in Settings -> Properties.");
        }
        String type = normalizeOtxType(indicatorType);
        String url = OTX_BASE + "/" + type + "/" + encode(indicator.trim()) + "/general";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-OTX-API-KEY", key)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ThreatIntel-MCP/1.0")
                    .GET()
                    .build();
            return sendAndWrap(req, "AlienVault OTX");
        } catch (Exception e) {
            logger.warn("[ThreatIntelClient] getOtxIndicator failed: {}", e.getMessage());
            return errorJson("AlienVault OTX request failed: " + e.getMessage());
        }
    }

    /**
     * Queries abuse.ch URLhaus for known malware distribution URLs or hosts.
     *
     * @param target URL or hostname/IP to check
     * @return structured JSON response
     */
    public String lookupUrlhaus(String target) {
        if (target == null || target.isBlank()) {
            return errorJson("target (URL or host) parameter is required.");
        }
        String clean = target.trim();
        boolean isUrl = clean.startsWith("http://") || clean.startsWith("https://");
        String endpoint = isUrl ? URLHAUS_URL_URL : URLHAUS_HOST_URL;
        String formBody = (isUrl ? "url=" : "host=") + encode(clean);
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-ThreatIntel-MCP/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));
            String authKey = getProp(ThreatIntelPlugin.PROP_URLHAUS_AUTH_KEY);
            if (!authKey.isEmpty()) {
                req.header("Auth-Key", authKey);
            }
            return sendAndWrap(req.build(), "URLhaus");
        } catch (Exception e) {
            logger.warn("[ThreatIntelClient] lookupUrlhaus failed: {}", e.getMessage());
            return errorJson("URLhaus request failed: " + e.getMessage());
        }
    }

    private String sendAndWrap(HttpRequest req, String source) throws Exception {
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

    private static String normalizeOtxType(String raw) {
        if (raw == null || raw.isBlank()) return "IPv4";
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "ipv6" -> "IPv6";
            case "domain" -> "domain";
            case "hostname", "host" -> "hostname";
            case "file", "hash", "sha256", "md5" -> "file";
            default -> "IPv4";
        };
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
