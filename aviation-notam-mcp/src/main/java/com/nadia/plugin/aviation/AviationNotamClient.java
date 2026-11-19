package com.nadia.plugin.aviation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
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
 * Client for FAA NAS Airport Delays, FAA NOTAM API, and live military/state ADS-B flights.
 */
public class AviationNotamClient {

    private static final Logger logger = LoggerFactory.getLogger(AviationNotamClient.class);
    public static final String FAA_NAS_STATUS_URL = "https://nasstatus.faa.gov/api/airport-status-information";
    public static final String FAA_NOTAM_URL = "https://external-api.faa.gov/notamapi/v1/notams";
    public static final String DEFAULT_ADSB_MIL_URL = "https://api.adsb.lol/v2/mil";

    private final HttpClient httpClient;
    private final NadiaPluginContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs an {@code AviationNotamClient}.
     *
     * @param httpClient shared HTTP client
     * @param context    optional host {@link NadiaPluginContext}
     */
    public AviationNotamClient(HttpClient httpClient, NadiaPluginContext context) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.context = context;
    }

    /**
     * Retrieves live FAA National Airspace System (NAS) airport ground stops, delays, and closures.
     *
     * @param airportCode optional 3-letter FAA/IATA airport filter (e.g., {@code JFK}, {@code EWR}, {@code ORD})
     * @return structured JSON string with active FAA airport delays
     */
    public String getFaaAirportDelays(String airportCode) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FAA_NAS_STATUS_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/xml, text/xml")
                    .header("User-Agent", "Nadia-Aviation-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("FAA NAS Status returned HTTP " + resp.statusCode());
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(resp.body())));

            String filter = airportCode == null ? "" : airportCode.trim().toUpperCase(Locale.ROOT);
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("updateTime", textOf(doc, "Update_Time"));
            ArrayNode delays = out.putArray("delays");

            collectDelayElements(doc, "Ground_Delay", "Ground Delay", filter, delays);
            collectDelayElements(doc, "Program", "Ground Stop", filter, delays);
            collectDelayElements(doc, "Delay", "Arrival/Departure Delay", filter, delays);
            collectDelayElements(doc, "Airport", "Airport Closure", filter, delays);

            out.put("count", delays.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[AviationNotamClient] getFaaAirportDelays failed: {}", e.getMessage());
            return errorJson("FAA Airport Delays lookup failed: " + e.getMessage());
        }
    }

    /**
     * Searches official FAA NOTAMs for an ICAO airport or Flight Information Region (FIR).
     *
     * @param locationIcao ICAO airport or FIR code (e.g., {@code KJFK}, {@code EGLL})
     * @param limit        maximum NOTAMs to return (1..50, default 20)
     * @return structured JSON response
     */
    public String searchNotams(String locationIcao, Integer limit) {
        if (locationIcao == null || locationIcao.isBlank()) {
            return errorJson("locationIcao parameter is required (e.g., KJFK, KLAX).");
        }
        String clientId = getProp(AviationNotamPlugin.PROP_NOTAM_CLIENT_ID, "");
        String clientSecret = getProp(AviationNotamPlugin.PROP_NOTAM_CLIENT_SECRET, "");
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            return errorJson("FAA NOTAM credentials are not configured. Set " + AviationNotamPlugin.PROP_NOTAM_CLIENT_ID
                    + " and " + AviationNotamPlugin.PROP_NOTAM_CLIENT_SECRET + " in Settings -> Properties.");
        }
        int max = (limit == null || limit <= 0) ? 20 : Math.min(limit, 50);
        String url = FAA_NOTAM_URL + "?icaoLocation=" + URLEncoder.encode(locationIcao.trim().toUpperCase(Locale.ROOT), StandardCharsets.UTF_8)
                + "&pageSize=" + max;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("client_id", clientId)
                    .header("client_secret", clientSecret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-Aviation-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("FAA NOTAM API returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.set("notams", parsed.path("items"));
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[AviationNotamClient] searchNotams failed: {}", e.getMessage());
            return errorJson("FAA NOTAM request failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves live military and state aircraft ADS-B positions from the public adsb.lol military feed.
     *
     * @param callsignOrType optional filter matching callsign, aircraft type, or registration
     * @param limit          maximum aircraft to return (1..100, default 30)
     * @return structured JSON string with military aircraft positions
     */
    public String getMilitaryFlights(String callsignOrType, Integer limit) {
        int max = (limit == null || limit <= 0) ? 30 : Math.min(limit, 100);
        String url = getProp(AviationNotamPlugin.PROP_ADSB_MIL_URL, DEFAULT_ADSB_MIL_URL);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Nadia-Aviation-MCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return errorJson("Military ADS-B feed returned HTTP " + resp.statusCode());
            }
            JsonNode parsed = objectMapper.readTree(resp.body());
            JsonNode acList = parsed.path("ac");
            String filter = callsignOrType == null ? "" : callsignOrType.trim().toLowerCase(Locale.ROOT);

            ObjectNode out = objectMapper.createObjectNode();
            out.put("status", "OK");
            out.put("totalTracked", acList.isArray() ? acList.size() : 0);
            ArrayNode matched = out.putArray("aircraft");
            if (acList.isArray()) {
                for (JsonNode ac : acList) {
                    String flight = ac.path("flight").asText("").trim();
                    String type = ac.path("t").asText("").trim();
                    String reg = ac.path("r").asText("").trim();
                    if (!filter.isEmpty() && !(flight + " " + type + " " + reg).toLowerCase(Locale.ROOT).contains(filter)) {
                        continue;
                    }
                    ObjectNode item = matched.addObject();
                    item.put("hex", ac.path("hex").asText(""));
                    item.put("callsign", flight);
                    item.put("registration", reg);
                    item.put("type", type);
                    item.put("lat", ac.path("lat").asDouble(0.0));
                    item.put("lon", ac.path("lon").asDouble(0.0));
                    item.put("altitudeBaro", ac.path("alt_baro").asText(""));
                    item.put("groundSpeedKt", ac.path("gs").asDouble(0.0));
                    if (matched.size() >= max) break;
                }
            }
            out.put("returnedCount", matched.size());
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            logger.warn("[AviationNotamClient] getMilitaryFlights failed: {}", e.getMessage());
            return errorJson("Military ADS-B request failed: " + e.getMessage());
        }
    }

    private void collectDelayElements(Document doc, String tag, String category, String filter, ArrayNode target) {
        NodeList nodes = doc.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element el)) continue;
            String arpt = childText(el, "ARPT");
            if (arpt.isEmpty()) continue;
            if (!filter.isEmpty() && !filter.equalsIgnoreCase(arpt)) continue;
            ObjectNode item = target.addObject();
            item.put("airport", arpt);
            item.put("category", category);
            putIfNonEmpty(item, "reason", childText(el, "Reason"));
            putIfNonEmpty(item, "avgDelay", childText(el, "Avg"));
            putIfNonEmpty(item, "maxDelay", childText(el, "Max"));
            putIfNonEmpty(item, "endTime", childText(el, "End_Time"));
        }
    }

    private static String textOf(Document doc, String tag) {
        NodeList list = doc.getElementsByTagName(tag);
        return list.getLength() > 0 && list.item(0).getTextContent() != null ? list.item(0).getTextContent().trim() : "";
    }

    private static String childText(Element el, String tag) {
        NodeList list = el.getElementsByTagName(tag);
        return list.getLength() > 0 && list.item(0).getTextContent() != null ? list.item(0).getTextContent().trim() : "";
    }

    private static void putIfNonEmpty(ObjectNode node, String k, String v) {
        if (v != null && !v.isEmpty()) node.put(k, v);
    }

    private String getProp(String key, String defaultVal) {
        if (context == null) return defaultVal;
        String val = context.getProperty(key, defaultVal);
        return (val != null && !val.isBlank()) ? val.trim() : defaultVal;
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
