package com.nadia.plugin.eurepoc;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Client for retrieving and querying cyber incident records from the European Repository of
 * Cyber Incidents (EuRepoC).
 *
 * <p>Supports three modes seamlessly:
 * <ol>
 *   <li>Dedicated EuRepoC REST API when {@code nadia.prop.eurepoc.apiUrl} is configured.</li>
 *   <li>Direct JSON or CSV dataset loading from {@code nadia.prop.eurepoc.datasetUrl} (HTTP(S) URL or local path).</li>
 *   <li>Automatic discovery of the latest EuRepoC global dataset release on Zenodo when no custom URL is set.</li>
 * </ol>
 * Loaded datasets are cached in memory for 6 hours to keep queries fast.</p>
 */
public class EuRepoCClient {

    private static final Logger logger = LoggerFactory.getLogger(EuRepoCClient.class);
    static final String ZENODO_DISCOVERY_URL =
            "https://zenodo.org/api/records?q=eurepoc&sort=mostrecent&size=5";
    private static final long CACHE_TTL_MS = Duration.ofHours(6).toMillis();
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String zenodoDiscoveryUrl;

    private volatile List<Map<String, String>> cachedIncidents;
    private volatile String cachedSource;
    private volatile long cacheTimestampMs;

    /**
     * Creates a client backed by the given {@link HttpClient} and optional {@link NadiaPluginContext}.
     *
     * @param httpClient shared HTTP client
     * @param context    host plugin context (may be {@code null} in standalone tests)
     */
    public EuRepoCClient(HttpClient httpClient, NadiaPluginContext context) {
        this(httpClient, context, ZENODO_DISCOVERY_URL);
    }

    /**
     * Package-private constructor allowing custom Zenodo discovery URL for testing.
     */
    EuRepoCClient(HttpClient httpClient, NadiaPluginContext context, String zenodoDiscoveryUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
        this.zenodoDiscoveryUrl = zenodoDiscoveryUrl;
    }

    /**
     * Searches EuRepoC cyber incidents by keyword, receiver/target country, initiator country,
     * incident type, and date range.
     *
     * @param query            optional free-text keyword matched across all incident fields
     * @param receiverCountry  optional target/receiver country filter
     * @param initiatorCountry optional initiator/origin country filter
     * @param incidentType     optional incident type/category filter (e.g., Ransomware, Disruption)
     * @param startDate        optional ISO date lower bound (YYYY-MM-DD)
     * @param endDate          optional ISO date upper bound (YYYY-MM-DD)
     * @param limit            maximum number of incidents to return (1..100, default 20)
     * @return structured JSON string with matching incidents
     */
    public String searchIncidents(String query,
                                  String receiverCountry,
                                  String initiatorCountry,
                                  String incidentType,
                                  String startDate,
                                  String endDate,
                                  Integer limit) {
        int maxResults = clampLimit(limit);
        try {
            String apiUrl = getProp(EuRepoCPlugin.PROP_API_URL);
            if (!apiUrl.isEmpty()) {
                return queryDedicatedApi(apiUrl, query, receiverCountry, initiatorCountry,
                        incidentType, startDate, endDate, maxResults);
            }

            List<Map<String, String>> all = loadDataset();
            String qLower = norm(query);
            String recLower = norm(receiverCountry);
            String initLower = norm(initiatorCountry);
            String typeLower = norm(incidentType);
            String startNorm = norm(startDate);
            String endNorm = norm(endDate);

            List<Map<String, String>> matched = new ArrayList<>();
            int totalMatches = 0;
            for (Map<String, String> row : all) {
                if (!matchesFilters(row, qLower, recLower, initLower, typeLower, startNorm, endNorm)) {
                    continue;
                }
                totalMatches++;
                if (matched.size() < maxResults) {
                    matched.add(row);
                }
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("source", cachedSource != null ? cachedSource : "EuRepoC");
            root.put("totalDatasetIncidents", all.size());
            root.put("totalMatches", totalMatches);
            root.put("returnedCount", matched.size());
            ArrayNode arr = root.putArray("incidents");
            for (Map<String, String> row : matched) {
                arr.add(toIncidentNode(row));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[EuRepoCClient] searchIncidents failed: {}", e.getMessage());
            return errorJson("Failed to search EuRepoC incidents: " + e.getMessage());
        }
    }

    /**
     * Retrieves a single EuRepoC incident record by its incident ID.
     *
     * @param incidentId EuRepoC incident identifier
     * @return structured JSON string with full incident details
     */
    public String getIncidentById(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            return errorJson("incidentId parameter is required.");
        }
        String targetId = incidentId.trim();
        try {
            String apiUrl = getProp(EuRepoCPlugin.PROP_API_URL);
            if (!apiUrl.isEmpty()) {
                String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
                String url = base + "/incidents/" + encode(targetId);
                return sendAuthorizedGet(url);
            }

            List<Map<String, String>> all = loadDataset();
            for (Map<String, String> row : all) {
                String id = firstNonBlank(row, "ID", "id", "incident_id", "Incident_ID", "eurepoc_id");
                if (targetId.equalsIgnoreCase(id)) {
                    ObjectNode root = objectMapper.createObjectNode();
                    root.put("status", "OK");
                    root.put("source", cachedSource != null ? cachedSource : "EuRepoC");
                    ObjectNode full = root.putObject("incident");
                    for (Map.Entry<String, String> entry : row.entrySet()) {
                        if (entry.getValue() != null && !entry.getValue().isBlank()) {
                            full.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return objectMapper.writeValueAsString(root);
                }
            }
            return errorJson("No EuRepoC incident found with ID: " + targetId);
        } catch (Exception e) {
            logger.warn("[EuRepoCClient] getIncidentById failed: {}", e.getMessage());
            return errorJson("Failed to retrieve EuRepoC incident: " + e.getMessage());
        }
    }

    /**
     * Computes aggregate statistics over the loaded EuRepoC cyber incident dataset.
     *
     * @return structured JSON summary including top receiver countries, initiators, and incident types
     */
    public String getDatasetSummary() {
        try {
            List<Map<String, String>> all = loadDataset();
            Map<String, Integer> receivers = new LinkedHashMap<>();
            Map<String, Integer> initiators = new LinkedHashMap<>();
            Map<String, Integer> types = new LinkedHashMap<>();

            for (Map<String, String> row : all) {
                incrementCount(receivers, firstNonBlank(row, "receiver_country", "Receiver_country", "target_country", "country"));
                incrementCount(initiators, firstNonBlank(row, "initiator_country", "Initiator_country", "source_country"));
                incrementCount(types, firstNonBlank(row, "incident_type", "Incident_type", "type", "category"));
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "OK");
            root.put("source", cachedSource != null ? cachedSource : "EuRepoC");
            root.put("totalIncidents", all.size());
            root.set("topReceiverCountries", topCounts(receivers, 10));
            root.set("topInitiatorCountries", topCounts(initiators, 10));
            root.set("topIncidentTypes", topCounts(types, 10));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("[EuRepoCClient] getDatasetSummary failed: {}", e.getMessage());
            return errorJson("Failed to summarize EuRepoC dataset: " + e.getMessage());
        }
    }

    private String queryDedicatedApi(String apiUrl,
                                     String query,
                                     String receiverCountry,
                                     String initiatorCountry,
                                     String incidentType,
                                     String startDate,
                                     String endDate,
                                     int limit) throws IOException, InterruptedException {
        String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        StringBuilder sb = new StringBuilder(base).append("/incidents?limit=").append(limit);
        appendParam(sb, "q", query);
        appendParam(sb, "receiver_country", receiverCountry);
        appendParam(sb, "initiator_country", initiatorCountry);
        appendParam(sb, "incident_type", incidentType);
        appendParam(sb, "start_date", startDate);
        appendParam(sb, "end_date", endDate);
        return sendAuthorizedGet(sb.toString());
    }

    private String sendAuthorizedGet(String url) throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "Nadia-EuRepoC-MCP/1.0")
                .GET();
        String apiKey = getProp(EuRepoCPlugin.PROP_API_KEY);
        if (!apiKey.isEmpty()) {
            req.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return errorJson("EuRepoC API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }
        return resp.body();
    }

    private synchronized List<Map<String, String>> loadDataset() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedIncidents != null && (now - cacheTimestampMs) < CACHE_TTL_MS) {
            return cachedIncidents;
        }

        String datasetUrl = getProp(EuRepoCPlugin.PROP_DATASET_URL);
        if (datasetUrl.isEmpty()) {
            datasetUrl = discoverLatestZenodoDatasetUrl();
        }

        String rawContent;
        boolean isJson;
        if (datasetUrl.startsWith("http://") || datasetUrl.startsWith("https://")) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(datasetUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("User-Agent", "Nadia-EuRepoC-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IOException("Dataset download failed with HTTP " + resp.statusCode() + " from " + datasetUrl);
            }
            rawContent = resp.body();
            String ct = resp.headers().firstValue("Content-Type").orElse("");
            isJson = ct.contains("json") || datasetUrl.toLowerCase(Locale.ROOT).endsWith(".json")
                    || rawContent.trim().startsWith("[") || rawContent.trim().startsWith("{");
        } else {
            Path localPath = Path.of(datasetUrl);
            if (!Files.isRegularFile(localPath)) {
                throw new IOException("Configured EuRepoC dataset file does not exist: " + datasetUrl);
            }
            rawContent = Files.readString(localPath, StandardCharsets.UTF_8);
            isJson = datasetUrl.toLowerCase(Locale.ROOT).endsWith(".json")
                    || rawContent.trim().startsWith("[") || rawContent.trim().startsWith("{");
        }

        List<Map<String, String>> parsed = isJson ? parseJsonDataset(rawContent) : parseCsvDataset(rawContent);
        this.cachedIncidents = parsed;
        this.cachedSource = datasetUrl;
        this.cacheTimestampMs = now;
        return parsed;
    }

    private String discoverLatestZenodoDatasetUrl() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(zenodoDiscoveryUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "Nadia-EuRepoC-MCP/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Zenodo discovery returned HTTP " + resp.statusCode()
                    + ". Set " + EuRepoCPlugin.PROP_DATASET_URL + " to a direct EuRepoC JSON/CSV URL or local file.");
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode hits = root.path("hits").path("hits");
        if (hits.isArray()) {
            String csvFallback = null;
            for (JsonNode hit : hits) {
                JsonNode files = hit.path("files");
                if (files.isArray()) {
                    for (JsonNode file : files) {
                        String key = file.path("key").asText("").toLowerCase(Locale.ROOT);
                        String selfLink = file.path("links").path("self").asText("");
                        if (selfLink.isEmpty()) {
                            continue;
                        }
                        if (key.endsWith(".json")) {
                            return selfLink;
                        }
                        if (key.endsWith(".csv") && csvFallback == null) {
                            csvFallback = selfLink;
                        }
                    }
                }
            }
            if (csvFallback != null) {
                return csvFallback;
            }
        }
        throw new IOException("Could not find a JSON or CSV EuRepoC dataset on Zenodo. Configure "
                + EuRepoCPlugin.PROP_DATASET_URL + " in Settings -> Properties.");
    }

    List<Map<String, String>> parseJsonDataset(String rawJson) throws IOException {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode array = root.isArray() ? root : root.path("incidents");
        if (!array.isArray()) {
            array = root.path("data");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode node : array) {
                if (!node.isObject()) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                node.fields().forEachRemaining(e -> {
                    JsonNode v = e.getValue();
                    row.put(e.getKey(), v.isValueNode() ? v.asText("") : v.toString());
                });
                rows.add(row);
            }
        }
        return rows;
    }

    List<Map<String, String>> parseCsvDataset(String rawCsv) {
        List<List<String>> table = parseRfc4180Csv(rawCsv);
        List<Map<String, String>> rows = new ArrayList<>();
        if (table.size() < 2) {
            return rows;
        }
        List<String> headers = table.get(0);
        for (int i = 1; i < table.size(); i++) {
            List<String> values = table.get(i);
            if (values.isEmpty() || (values.size() == 1 && values.get(0).isBlank())) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c).trim();
                if (c == 0 && key.startsWith("\uFEFF")) {
                    key = key.substring(1);
                }
                String val = c < values.size() ? values.get(c).trim() : "";
                row.put(key, val);
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> parseRfc4180Csv(String input) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        int len = input.length();
        for (int i = 0; i < len; i++) {
            char ch = input.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                } else if (ch == '\r') {
                    if (i + 1 < len && input.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else if (ch == '\n') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else {
                    field.append(ch);
                }
            }
        }
        if (field.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }
        return rows;
    }

    private boolean matchesFilters(Map<String, String> row,
                                   String query,
                                   String receiverCountry,
                                   String initiatorCountry,
                                   String incidentType,
                                   String startDate,
                                   String endDate) {
        if (!receiverCountry.isEmpty()) {
            String rec = norm(firstNonBlank(row, "receiver_country", "Receiver_country", "target_country", "country"));
            if (!rec.contains(receiverCountry)) {
                return false;
            }
        }
        if (!initiatorCountry.isEmpty()) {
            String init = norm(firstNonBlank(row, "initiator_country", "Initiator_country", "source_country"));
            if (!init.contains(initiatorCountry)) {
                return false;
            }
        }
        if (!incidentType.isEmpty()) {
            String type = norm(firstNonBlank(row, "incident_type", "Incident_type", "type", "category"));
            if (!type.contains(incidentType)) {
                return false;
            }
        }
        if (!startDate.isEmpty() || !endDate.isEmpty()) {
            String date = norm(firstNonBlank(row, "start_date", "Start_date", "date", "inclusion_date"));
            String isoPrefix = date.length() >= 10 ? date.substring(0, 10) : date;
            if (!startDate.isEmpty() && (isoPrefix.isEmpty() || isoPrefix.compareTo(startDate) < 0)) {
                return false;
            }
            if (!endDate.isEmpty() && (isoPrefix.isEmpty() || isoPrefix.compareTo(endDate) > 0)) {
                return false;
            }
        }
        if (!query.isEmpty()) {
            for (String val : row.values()) {
                if (val != null && val.toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private ObjectNode toIncidentNode(Map<String, String> row) {
        ObjectNode node = objectMapper.createObjectNode();
        putIfPresent(node, "id", firstNonBlank(row, "ID", "id", "incident_id", "Incident_ID", "eurepoc_id"));
        putIfPresent(node, "name", firstNonBlank(row, "name", "Name", "title", "incident_name"));
        putIfPresent(node, "startDate", firstNonBlank(row, "start_date", "Start_date", "date"));
        putIfPresent(node, "endDate", firstNonBlank(row, "end_date", "End_date"));
        putIfPresent(node, "incidentType", firstNonBlank(row, "incident_type", "Incident_type", "type"));
        putIfPresent(node, "receiverCountry", firstNonBlank(row, "receiver_country", "Receiver_country", "target_country"));
        putIfPresent(node, "receiverCategory", firstNonBlank(row, "receiver_category", "Receiver_category", "target_sector"));
        putIfPresent(node, "initiatorCountry", firstNonBlank(row, "initiator_country", "Initiator_country", "source_country"));
        putIfPresent(node, "initiatorName", firstNonBlank(row, "initiator_name", "Initiator_name", "threat_actor"));
        putIfPresent(node, "description", truncate(firstNonBlank(row, "description", "Description", "summary"), 400));
        return node;
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

    private static String firstNonBlank(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String val = row.get(key);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return "";
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private String getProp(String key) {
        if (context == null) {
            return "";
        }
        String val = context.getProperty(key, "");
        return val != null ? val.trim() : "";
    }

    private static void appendParam(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append('&').append(key).append('=').append(encode(value.trim()));
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
