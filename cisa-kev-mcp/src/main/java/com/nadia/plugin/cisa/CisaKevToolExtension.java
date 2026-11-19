package com.nadia.plugin.cisa;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for CISA Known Exploited
 * Vulnerabilities (KEV) and Cybersecurity Advisories.
 */
@Extension
public class CisaKevToolExtension implements NadiaToolExtension {

    private final CisaKevClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link CisaKevRuntime#getInstance()}.
     */
    public CisaKevToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit {@link CisaKevClient}.
     *
     * @param explicitClient explicit client for unit testing
     */
    public CisaKevToolExtension(CisaKevClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private CisaKevClient client() {
        return explicitClient != null ? explicitClient : CisaKevRuntime.getInstance().getClient();
    }

    /**
     * Searches the CISA Known Exploited Vulnerabilities (KEV) catalog.
     *
     * @param query          optional keyword filter
     * @param vendor         optional vendor name filter
     * @param product        optional product name filter
     * @param ransomwareOnly optional filter for CVEs known to be used in ransomware campaigns
     * @param addedSince     optional ISO date lower bound (YYYY-MM-DD)
     * @param limit          optional max results (default 20, max 100)
     * @return JSON string of matching KEV entries
     */
    @NadiaTool(
            name = "cisaSearchKev",
            description = "Searches the official CISA Known Exploited Vulnerabilities (KEV) catalog by keyword, vendor, product, ransomware campaign use, or date added.",
            properties = {CisaKevPlugin.PROP_KEV_URL}
    )
    public String cisaSearchKev(
            @NadiaToolParam(description = "Optional keyword search across CVE ID, vendor, product, name, and description", required = false) String query,
            @NadiaToolParam(description = "Optional vendor filter (e.g., Microsoft, Cisco, Ivanti, Fortinet)", required = false) String vendor,
            @NadiaToolParam(description = "Optional product filter (e.g., Exchange, PAN-OS, Chrome)", required = false) String product,
            @NadiaToolParam(description = "Set true to return only vulnerabilities with known ransomware campaign use", required = false) Boolean ransomwareOnly,
            @NadiaToolParam(description = "Optional lower bound on dateAdded in YYYY-MM-DD format", required = false) String addedSince,
            @NadiaToolParam(description = "Maximum number of KEV entries to return (1-100, default 20)", required = false) Integer limit) {
        return client().searchKev(query, vendor, product, ransomwareOnly, addedSince, limit);
    }

    /**
     * Checks whether a specific CVE ID is listed in the CISA KEV catalog and returns its details.
     *
     * @param cveId CVE identifier (e.g., {@code CVE-2024-3400})
     * @return JSON string with lookup result
     */
    @NadiaTool(
            name = "cisaGetKevByCve",
            description = "Checks whether a specific CVE identifier (e.g., CVE-2024-3400) is in the CISA KEV catalog and retrieves its remediation due date and details.",
            properties = {CisaKevPlugin.PROP_KEV_URL}
    )
    public String cisaGetKevByCve(
            @NadiaToolParam(description = "CVE identifier to look up (e.g., CVE-2024-3400)") String cveId) {
        return client().getKevByCve(cveId);
    }

    /**
     * Returns catalog-level statistics and the 10 most recently added CISA KEV entries.
     *
     * @return JSON summary of the CISA KEV catalog
     */
    @NadiaTool(
            name = "cisaGetKevSummary",
            description = "Returns CISA KEV catalog version, total count, ransomware-associated count, top affected vendors, and the 10 most recently added KEV entries.",
            properties = {CisaKevPlugin.PROP_KEV_URL}
    )
    public String cisaGetKevSummary() {
        return client().getKevSummary();
    }

    /**
     * Retrieves recent CISA Cybersecurity Advisories, ICS Advisories, or Alerts.
     *
     * @param feedType optional feed selector: {@code all}, {@code cybersecurity}, {@code ics}, or {@code alerts}
     * @param query    optional keyword filter
     * @param limit    optional max advisories to return (default 20, max 100)
     * @return JSON string of matching CISA advisories
     */
    @NadiaTool(
            name = "cisaGetAdvisories",
            description = "Retrieves and filters recent CISA Cybersecurity Advisories, ICS Advisories, and Alerts from CISA's official XML/RSS feeds.",
            properties = {CisaKevPlugin.PROP_ADVISORIES_FEED_URL}
    )
    public String cisaGetAdvisories(
            @NadiaToolParam(description = "Feed category: 'all' (default), 'cybersecurity', 'ics', or 'alerts'", required = false) String feedType,
            @NadiaToolParam(description = "Optional keyword filter across advisory title and summary", required = false) String query,
            @NadiaToolParam(description = "Maximum number of advisories to return (1-100, default 20)", required = false) Integer limit) {
        return client().getAdvisories(feedType, query, limit);
    }
}
