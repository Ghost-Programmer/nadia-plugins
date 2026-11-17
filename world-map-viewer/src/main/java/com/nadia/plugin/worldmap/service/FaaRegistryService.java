package com.nadia.plugin.worldmap.service;

import com.nadia.plugin.worldmap.model.FaaAircraftRecord;
import com.nadia.plugin.worldmap.util.IcaoNNumberConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for querying, parsing, and caching official aircraft registration
 * records from the
 * Federal Aviation Administration (FAA) Civil Aviation Registry.
 *
 * <p>
 * Integrates directly with the official FAA Civil Aviation Registry inquiry
 * portal
 * ({@code https://registry.faa.gov/aircraftinquiry/Search/NNumberInquiry}) by
 * managing session
 * anti-forgery verification tokens, simulating browser-compatible HTTP
 * negotiation, submitting
 * N-Number search forms, and parsing HTML table result structures into
 * structured {@link FaaAircraftRecord} models.
 *
 * <p>
 * <b>Lifecycle &amp; Thread Safety:</b>
 * Thread-safe plugin service using a {@link ConcurrentHashMap} record cache and
 * synchronized token refresh.
 */
public class FaaRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(FaaRegistryService.class);

    /** Official FAA N-Number inquiry portal URL. */
    public static final String INQUIRY_URL = "https://registry.faa.gov/aircraftinquiry/Search/NNumberInquiry";

    /** Official FAA N-Number query result form POST target URL. */
    public static final String RESULT_URL = "https://registry.faa.gov/aircraftinquiry/Search/NNumberResult";

    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern TOKEN_PATTERN = Pattern
            .compile("name=\"__RequestVerificationToken\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");

    private static final Pattern DATA_LABEL_PATTERN = Pattern
            .compile("<td[^>]*data-label=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</td>", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final Map<String, FaaAircraftRecord> cache = new ConcurrentHashMap<>();

    private volatile String cachedToken;
    private volatile Instant tokenTimestamp = Instant.EPOCH;
    private final Object tokenLock = new Object();

    /**
     * Constructs the {@link FaaRegistryService} with configurable network
     * connection timeout.
     *
     * @param timeoutSeconds connection and read timeout in seconds
     */
    public FaaRegistryService(int timeoutSeconds) {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .build();
    }

    /**
     * Synchronously queries the FAA Civil Aviation Registry for an aircraft by its
     * N-Number.
     *
     * @param nNumber the FAA N-number (e.g. {@code "N900AA"} or {@code "900AA"})
     * @return the populated {@link FaaAircraftRecord}, or an error record if the
     *         query fails
     */
    public FaaAircraftRecord queryNNumber(String nNumber) {
        if (nNumber == null || nNumber.trim().isEmpty()) {
            return FaaAircraftRecord.createErrorRecord(nNumber, "N-Number cannot be empty");
        }

        String normalized = nNumber.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("N")) {
            normalized = "N" + normalized;
        }

        FaaAircraftRecord cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }

        try {
            FaaAircraftRecord record = executeFaaQuery(normalized);
            if (record != null) {
                cache.put(normalized, record);
                return record;
            }
            FaaAircraftRecord notFound = new FaaAircraftRecord(normalized, false);
            cache.put(normalized, notFound);
            return notFound;
        } catch (Exception e) {
            logger.warn("[FaaRegistryService] Exception querying FAA registry for {}: {}", normalized, e.getMessage());
            return FaaAircraftRecord.createErrorRecord(normalized, "Query failed: " + e.getMessage());
        }
    }

    /**
     * Asynchronously queries the FAA Civil Aviation Registry for an aircraft by its
     * N-Number.
     *
     * @param nNumber the FAA N-number
     * @return a {@link CompletableFuture} containing the {@link FaaAircraftRecord}
     */
    public CompletableFuture<FaaAircraftRecord> queryNNumberAsync(String nNumber) {
        return CompletableFuture.supplyAsync(() -> queryNNumber(nNumber));
    }

    /**
     * Synchronously queries the FAA Civil Aviation Registry for an aircraft by its
     * 24-bit ICAO Mode S hex address.
     *
     * @param icaoHex the 6-character hexadecimal ICAO address (e.g.
     *                {@code "AC6F2D"})
     * @return the populated {@link FaaAircraftRecord}
     */
    public FaaAircraftRecord queryIcao(String icaoHex) {
        if (icaoHex == null || icaoHex.trim().isEmpty()) {
            return FaaAircraftRecord.createErrorRecord(null, "ICAO address cannot be empty");
        }

        String nNumber = IcaoNNumberConverter.icaoToNNumber(icaoHex);
        if (nNumber == null || nNumber.equals("N")) {
            FaaAircraftRecord foreign = new FaaAircraftRecord(null, false);
            foreign.setModeSCodeHex(icaoHex.trim().toUpperCase(Locale.ROOT));
            foreign.setErrorMessage("Non-US Mode S allocation; FAA Registry only tracks US N-Numbers");
            return foreign;
        }

        FaaAircraftRecord record = queryNNumber(nNumber);
        if (record.getModeSCodeHex() == null) {
            record.setModeSCodeHex(icaoHex.trim().toUpperCase(Locale.ROOT));
        }
        return record;
    }

    /**
     * Asynchronously queries the FAA Civil Aviation Registry for an aircraft by its
     * Mode S hex address.
     *
     * @param icaoHex the Mode S hexadecimal ICAO address
     * @return a {@link CompletableFuture} containing the {@link FaaAircraftRecord}
     */
    public CompletableFuture<FaaAircraftRecord> queryIcaoAsync(String icaoHex) {
        return CompletableFuture.supplyAsync(() -> queryIcao(icaoHex));
    }

    /**
     * Retrieves an unmodifiable snapshot of all currently cached FAA aircraft
     * records.
     *
     * @return map of N-numbers to cached records
     */
    public Map<String, FaaAircraftRecord> getCachedRecords() {
        return Map.copyOf(cache);
    }

    /**
     * Clears all in-memory cached FAA aircraft records and session tokens.
     */
    public void clearCache() {
        cache.clear();
        synchronized (tokenLock) {
            cachedToken = null;
            tokenTimestamp = Instant.EPOCH;
        }
    }

    private FaaAircraftRecord executeFaaQuery(String fullNNumber) throws Exception {
        String token = getOrCreateVerificationToken(false);
        String stripped = fullNNumber.startsWith("N") ? fullNNumber.substring(1) : fullNNumber;

        HttpResponse<String> response = postQuery(stripped, token);

        if (response.body() != null
                && (response.body().contains("input-error") || !response.body().contains("table"))) {
            if (response.body().contains("Data Updated each Federal Working Day")) {
                token = getOrCreateVerificationToken(true);
                response = postQuery(stripped, token);
            }
        }

        if (response.statusCode() != 200 || response.body() == null) {
            logger.warn("[FaaRegistryService] FAA returned status code {} for {}", response.statusCode(), fullNNumber);
            return null;
        }

        return parseFaaResultHtml(fullNNumber, response.body());
    }

    private HttpResponse<String> postQuery(String strippedNNumber, String token) throws Exception {
        String formBody = "NNumbertxt=" + URLEncoder.encode(strippedNNumber, StandardCharsets.UTF_8)
                + "&__RequestVerificationToken=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESULT_URL))
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://registry.faa.gov")
                .header("Referer", INQUIRY_URL)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String getOrCreateVerificationToken(boolean forceRefresh) throws Exception {
        synchronized (tokenLock) {
            if (!forceRefresh && cachedToken != null
                    && Duration.between(tokenTimestamp, Instant.now()).toMinutes() < 30) {
                return cachedToken;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(INQUIRY_URL))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                throw new IllegalStateException("Failed to load FAA Inquiry page: HTTP " + response.statusCode());
            }

            Matcher matcher = TOKEN_PATTERN.matcher(response.body());
            if (matcher.find()) {
                cachedToken = matcher.group(1);
                tokenTimestamp = Instant.now();
                return cachedToken;
            }

            throw new IllegalStateException("Could not find __RequestVerificationToken in FAA Inquiry page");
        }
    }

    /**
     * Parses the FAA HTML response page and populates an {@link FaaAircraftRecord}.
     *
     * @param fullNNumber the N-number queried
     * @param html        the raw HTML response body
     * @return populated record
     */
    public FaaAircraftRecord parseFaaResultHtml(String fullNNumber, String html) {
        if (html == null) {
            return FaaAircraftRecord.createErrorRecord(fullNNumber, "Empty HTML response from FAA");
        }

        if (!html.contains("devkit-table") && !html.contains("N-Number Inquiry Results")) {
            FaaAircraftRecord notFound = new FaaAircraftRecord(fullNNumber, false);
            notFound.setErrorMessage("No matching records found in FAA Registry");
            return notFound;
        }

        FaaAircraftRecord record = new FaaAircraftRecord(fullNNumber, true);

        Map<String, String> dataLabels = new ConcurrentHashMap<>();
        Matcher labelMatcher = DATA_LABEL_PATTERN.matcher(html);
        while (labelMatcher.find()) {
            String label = cleanText(labelMatcher.group(1));
            String value = cleanText(stripHtml(labelMatcher.group(2)));
            if (!label.isEmpty() && !value.isEmpty()) {
                dataLabels.put(label.toLowerCase(Locale.ROOT), value);
            }
        }

        record.setSerialNumber(findValue(dataLabels, "serial number"));
        record.setStatus(findValue(dataLabels, "status"));
        record.setManufacturer(findValue(dataLabels, "manufacturer name", "mfr name"));
        record.setModel(findValue(dataLabels, "model"));
        record.setTypeAircraft(findValue(dataLabels, "aircraft type", "type aircraft"));
        record.setTypeEngine(findValue(dataLabels, "engine type", "type engine"));
        record.setMfrYear(findValue(dataLabels, "mfr year", "year"));
        record.setModeSCodeHex(findValue(dataLabels, "mode s code (base 16 / hex)", "mode s code (hex)"));
        record.setModeSCodeOct(findValue(dataLabels, "mode s code (base 8 / oct)", "mode s code (oct)", "mode s code"));
        record.setCertificateIssueDate(findValue(dataLabels, "certificate issue date"));
        record.setExpirationDate(findValue(dataLabels, "expiration date"));
        record.setTypeRegistration(findValue(dataLabels, "type registration"));

        record.setOwnerName(findValue(dataLabels, "name", "reserving party name"));
        record.setOwnerStreet(findValue(dataLabels, "street"));
        record.setOwnerCity(findValue(dataLabels, "city"));
        record.setOwnerState(findValue(dataLabels, "state"));
        record.setOwnerZip(findValue(dataLabels, "zip code", "zip"));
        record.setOwnerCountry(findValue(dataLabels, "country"));

        record.setAirworthinessClassification(findValue(dataLabels, "airworthiness classification", "classification"));
        record.setEngineModel(findValue(dataLabels, "engine model"));

        if (record.getStatus() == null && html.contains("Reserved N-Number")) {
            record.setStatus("Reserved (" + findValue(dataLabels, "type reservation") + ")");
        }

        if (record.getModeSCodeHex() == null || record.getModeSCodeHex().isEmpty()) {
            String derivedHex = IcaoNNumberConverter.nNumberToIcao(fullNNumber);
            if (derivedHex != null) {
                record.setModeSCodeHex(derivedHex.toUpperCase(Locale.ROOT));
            }
        }

        return record;
    }

    private String findValue(Map<String, String> map, String... candidates) {
        for (String candidate : candidates) {
            String val = map.get(candidate.toLowerCase(Locale.ROOT));
            if (val != null && !val.trim().isEmpty() && !val.equalsIgnoreCase("None")) {
                return val.trim();
            }
        }
        return null;
    }

    private String cleanText(String text) {
        if (text == null)
            return "";
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripHtml(String html) {
        if (html == null)
            return "";
        return html.replaceAll("<[^>]*>", "");
    }
}
