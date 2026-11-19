package com.nadia.plugin.cisa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Client for querying the official CISA Known Exploited Vulnerabilities (KEV) JSON catalog
 * and CISA Cybersecurity Advisories / ICS Advisories / Alerts RSS feeds.
 */
public class CisaKevClient {

    private static final Logger logger = LoggerFactory.getLogger(CisaKevClient.class);
    public static final String DEFAULT_KEV_URL =
            "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json";
    public static final String FEED_ALL = "https://www.cisa.gov/cybersecurity-advisories/all.xml";
    public static final String FEED_CYBERSECURITY = "https://www.cisa.gov/cybersecurity-advisories/cybersecurity-advisories.xml";
    public static final String FEED_ICS = "https://www.cisa.gov/cybersecurity-advisories/ics-advisories.xml";
    public static final String FEED_ALERTS = "https://www.cisa.gov/cybersecurity-advisories/alerts.xml";

    private static final long KEV_CACHE_TTL_MS = Duration.ofMinutes(15).toMillis();
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile JsonNode cachedKevCatalog;
    private volatile long kevCacheTimestampMs;

    /**
     * Creates a CISA KEV &amp; Advisories client backed by the given {@link HttpClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public CisaKevClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Searches the CISA Known Exploited Vulnerabilities (KEV) catalog.
     *
     * @param query          optional keyword matching CVE ID, vendor, product, name, or description
     * @param vendor         optional vendorProject filter
     * @param product        optional product filter
     * @param ransomwareOnly when {@code true}, returns only CVEs with known ransomware campaign use
     * @param addedSince     optional ISO date lower bound (YYYY-MM-DD) on {@code dateAdded}
     * @param limit          maximum results to return (1..100, default 20)
     * @return structured JSON string with matching vulnerabilities
     */
    public String searchKev(String query,
                            String vendor,
                            String product,
                            Boolean ransomwareOnly,
                            String addedSince,
                            Integer limit) {
        int maxResults = clampLimit(limit);
        try {
            JsonNode catalog = loadKevCatalog();
            JsonNode vulns = catalog.path("vulnerabilities");

            String qLower = norm(query);
            String vendorLower = norm(vendor);
            String productLower = norm(product);
            String sinceNorm = norm(addedSince);
            boolean onlyRansomware = Boolean.TRUE.equals(ransomwareOnly);

            List<JsonNode> matches = new ArrayList<>();
            if (vulns.isArray()) {
                for (JsonNode v : vulns) {
                    if (!matchesKev(v, qLower, vendorLower, productLower, onlyRansomware, sinceNorm)) {
                        continue;
                    }
                    matches.add(v);
                }
            }
            // Sort newest dateAdded first
            matches.sort((a, b) -> b.path("dateAdded").asText("").compareTo(a.path("dateAdded").asText("")));

            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("catalogVersion", catalog.path("catalogVersion").asText(""));
            root.put("dateReleased", catalog.path("dateReleased").asText(""));
            root.put("totalCatalogCount", catalog.path("count").asInt(vulns.size()));
            root.put("totalMatches", matches.size());
            int returnCount = Math.min(maxResults, matches.size());
            root.put("returnedCount", returnCount);
            ArrayNode arr = root.putArray("vulnerabilities");
            for (int i = 0; i < returnCount; i++) {
                arr.add(matches.get(i));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[CisaKevClient] searchKev failed: {}", e.getMessage());
            return errorJson("Failed to search CISA KEV catalog: " + e.getMessage());
        }
    }

    /**
     * Looks up a single CVE identifier in the CISA KEV catalog.
     *
     * @param cveId CVE identifier (e.g., {@code CVE-2024-3400})
     * @return structured JSON string indicating whether the CVE is in KEV and its full record
     */
    public String getKevByCve(String cveId) {
        if (cveId == null || cveId.isBlank()) {
            return errorJson("cveId parameter is required (e.g., CVE-2024-3400).");
        }
        String target = cveId.trim().toUpperCase(Locale.ROOT);
        try {
            JsonNode catalog = loadKevCatalog();
            JsonNode vulns = catalog.path("vulnerabilities");
            if (vulns.isArray()) {
                for (JsonNode v : vulns) {
                    if (target.equalsIgnoreCase(v.path("cveID").asText(""))) {
                        ObjectNode root = objectMapper.createObjectNode();
                        root.put("status", "OK");
                        root.put("inKev", true);
                        root.put("catalogVersion", catalog.path("catalogVersion").asText(""));
                        root.set("vulnerability", v);
                        return objectMapper.writeValueAsString(root);
                    }
                }
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("inKev", false);
            root.put("cveID", target);
            root.put("catalogVersion", catalog.path("catalogVersion").asText(""));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[CisaKevClient] getKevByCve failed: {}", e.getMessage());
            return errorJson("Failed to check CISA KEV for " + target + ": " + e.getMessage());
        }
    }

    /**
     * Produces a summary of the CISA KEV catalog including total count, ransomware count,
     * top affected vendors, and the 10 most recently added vulnerabilities.
     *
     * @return structured JSON summary string
     */
    public String getKevSummary() {
        try {
            JsonNode catalog = loadKevCatalog();
            JsonNode vulns = catalog.path("vulnerabilities");
            int ransomwareCount = 0;
            Map<String, Integer> vendorCounts = new LinkedHashMap<>();
            List<JsonNode> all = new ArrayList<>();

            if (vulns.isArray()) {
                for (JsonNode v : vulns) {
                    all.add(v);
                    if ("Known".equalsIgnoreCase(v.path("knownRansomwareCampaignUse").asText(""))) {
                        ransomwareCount++;
                    }
                    String vendor = v.path("vendorProject").asText("").trim();
                    if (!vendor.isEmpty()) {
                        vendorCounts.merge(vendor, 1, Integer::sum);
                    }
                }
            }
            all.sort((a, b) -> b.path("dateAdded").asText("").compareTo(a.path("dateAdded").asText("")));

            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("catalogVersion", catalog.path("catalogVersion").asText(""));
            root.put("dateReleased", catalog.path("dateReleased").asText(""));
            root.put("totalCount", all.size());
            root.put("knownRansomwareCount", ransomwareCount);

            ArrayNode topVendors = root.putArray("topVendors");
            vendorCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .limit(10)
                    .forEach(e -> {
                        ObjectNode vn = topVendors.addObject();
                        vn.put("vendor", e.getKey());
                        vn.put("count", e.getValue());
                    });

            ArrayNode recent = root.putArray("mostRecent");
            for (int i = 0; i < Math.min(10, all.size()); i++) {
                JsonNode v = all.get(i);
                ObjectNode item = recent.addObject();
                item.put("cveID", v.path("cveID").asText(""));
                item.put("vendorProject", v.path("vendorProject").asText(""));
                item.put("product", v.path("product").asText(""));
                item.put("vulnerabilityName", v.path("vulnerabilityName").asText(""));
                item.put("dateAdded", v.path("dateAdded").asText(""));
                item.put("dueDate", v.path("dueDate").asText(""));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[CisaKevClient] getKevSummary failed: {}", e.getMessage());
            return errorJson("Failed to summarize CISA KEV catalog: " + e.getMessage());
        }
    }

    /**
     * Fetches and filters CISA Cybersecurity Advisories / ICS Advisories / Alerts RSS XML feeds.
     *
     * @param feedType feed category: {@code all} (default), {@code cybersecurity}, {@code ics}, or {@code alerts}
     * @param query    optional keyword filter across title and description
     * @param limit    maximum items to return (1..100, default 20)
     * @return structured JSON string with matching advisories
     */
    public String getAdvisories(String feedType, String query, Integer limit) {
        int maxResults = clampLimit(limit);
        try {
            String feedUrl = resolveFeedUrl(feedType);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(feedUrl))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .header("User-Agent", "Nadia-CISA-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("CISA Advisories feed returned HTTP " + resp.statusCode());
            }

            List<ObjectNode> items = parseRssFeed(resp.body(), norm(query), maxResults);
            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("feedUrl", feedUrl);
            root.put("returnedCount", items.size());
            ArrayNode arr = root.putArray("advisories");
            for (ObjectNode item : items) {
                arr.add(item);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[CisaKevClient] getAdvisories failed: {}", e.getMessage());
            return errorJson("Failed to fetch CISA advisories: " + e.getMessage());
        }
    }

    private synchronized JsonNode loadKevCatalog() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedKevCatalog != null && (now - kevCacheTimestampMs) < KEV_CACHE_TTL_MS) {
            return cachedKevCatalog;
        }
        String url = getProp(CisaKevPlugin.PROP_KEV_URL, DEFAULT_KEV_URL);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "Nadia-CISA-MCP/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("CISA KEV feed returned HTTP " + resp.statusCode());
        }
        JsonNode parsed = objectMapper.readTree(resp.body());
        this.cachedKevCatalog = parsed;
        this.kevCacheTimestampMs = now;
        return parsed;
    }

    private List<ObjectNode> parseRssFeed(String xml, String queryLower, int maxResults) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        NodeList itemNodes = doc.getElementsByTagName("item");
        List<ObjectNode> results = new ArrayList<>();
        for (int i = 0; i < itemNodes.getLength() && results.size() < maxResults; i++) {
            Node n = itemNodes.item(i);
            if (!(n instanceof Element el)) {
                continue;
            }
            String title = childText(el, "title");
            String link = childText(el, "link");
            String pubDate = childText(el, "pubDate");
            String rawDesc = childText(el, "description");
            String cleanDesc = cleanHtml(rawDesc);

            if (!queryLower.isEmpty()) {
                boolean hit = title.toLowerCase(Locale.ROOT).contains(queryLower)
                        || cleanDesc.toLowerCase(Locale.ROOT).contains(queryLower)
                        || link.toLowerCase(Locale.ROOT).contains(queryLower);
                if (!hit) {
                    continue;
                }
            }

            ObjectNode node = objectMapper.createObjectNode();
            node.put("title", title);
            node.put("link", link);
            node.put("pubDate", pubDate);
            node.put("summary", truncate(cleanDesc, 450));
            results.add(node);
        }
        return results;
    }

    private String resolveFeedUrl(String feedType) {
        String custom = getProp(CisaKevPlugin.PROP_ADVISORIES_FEED_URL, "");
        if (!custom.isEmpty() && (feedType == null || feedType.isBlank())) {
            return custom;
        }
        String type = norm(feedType);
        return switch (type) {
            case "cybersecurity", "advisories" -> FEED_CYBERSECURITY;
            case "ics", "ics-advisories" -> FEED_ICS;
            case "alerts", "alert" -> FEED_ALERTS;
            default -> !custom.isEmpty() ? custom : FEED_ALL;
        };
    }

    private boolean matchesKev(JsonNode v,
                               String query,
                               String vendor,
                               String product,
                               boolean ransomwareOnly,
                               String addedSince) {
        if (ransomwareOnly && !"Known".equalsIgnoreCase(v.path("knownRansomwareCampaignUse").asText(""))) {
            return false;
        }
        if (!vendor.isEmpty() && !v.path("vendorProject").asText("").toLowerCase(Locale.ROOT).contains(vendor)) {
            return false;
        }
        if (!product.isEmpty() && !v.path("product").asText("").toLowerCase(Locale.ROOT).contains(product)) {
            return false;
        }
        if (!addedSince.isEmpty()) {
            String dateAdded = v.path("dateAdded").asText("");
            if (dateAdded.isEmpty() || dateAdded.compareTo(addedSince) < 0) {
                return false;
            }
        }
        if (!query.isEmpty()) {
            String blob = (v.path("cveID").asText("") + " "
                    + v.path("vendorProject").asText("") + " "
                    + v.path("product").asText("") + " "
                    + v.path("vulnerabilityName").asText("") + " "
                    + v.path("shortDescription").asText("")).toLowerCase(Locale.ROOT);
            return blob.contains(query);
        }
        return true;
    }

    private static String childText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return "";
        }
        String txt = list.item(0).getTextContent();
        return txt != null ? txt.trim() : "";
    }

    private static String cleanHtml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String noTags = HTML_TAG_PATTERN.matcher(raw).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(noTags).replaceAll(" ").trim();
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) {
            return defaultVal;
        }
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
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
