package com.nadia.plugin.threatintel;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible threat intelligence tools for
 * NIST NVD, AbuseIPDB, AlienVault OTX, and abuse.ch URLhaus.
 */
@Extension
public class ThreatIntelToolExtension implements NadiaToolExtension {

    private final ThreatIntelClient explicitClient;

    /** Default PF4J constructor. */
    public ThreatIntelToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public ThreatIntelToolExtension(ThreatIntelClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private ThreatIntelClient client() {
        return explicitClient != null ? explicitClient : ThreatIntelPlugin.getClient();
    }

    /**
     * Queries NIST NVD CVE 2.0 by exact CVE ID or keyword.
     *
     * @param cveId   optional CVE ID (e.g., {@code CVE-2024-3400})
     * @param keyword optional keyword search
     * @param limit   max CVEs (default 10)
     * @return JSON string of NVD vulnerability data
     */
    @NadiaTool(
            name = "nvdGetCve",
            description = "Looks up CVSS scores, descriptions, and references for a CVE ID or keyword in the NIST National Vulnerability Database (NVD 2.0).",
            properties = {ThreatIntelPlugin.PROP_NVD_API_KEY}
    )
    public String nvdGetCve(
            @NadiaToolParam(description = "Exact CVE identifier (e.g., CVE-2024-3400)", required = false) String cveId,
            @NadiaToolParam(description = "Keyword search if cveId is omitted", required = false) String keyword,
            @NadiaToolParam(description = "Maximum CVEs to return (1-50, default 10)", required = false) Integer limit) {
        return client().getNvdCve(cveId, keyword, limit);
    }

    /**
     * Checks IP reputation via AbuseIPDB.
     *
     * @param ipAddress  IPv4/IPv6 address
     * @param maxAgeDays lookback window in days (default 90)
     * @return JSON string of AbuseIPDB reputation data
     */
    @NadiaTool(
            name = "abuseIpCheck",
            description = "Checks an IPv4 or IPv6 address against AbuseIPDB for abuse confidence score, ISP, country, and recent threat reports.",
            properties = {ThreatIntelPlugin.PROP_ABUSEIPDB_API_KEY}
    )
    public String abuseIpCheck(
            @NadiaToolParam(description = "IPv4 or IPv6 address to inspect") String ipAddress,
            @NadiaToolParam(description = "Lookback window in days (1-365, default 90)", required = false) Integer maxAgeDays) {
        return client().checkAbuseIp(ipAddress, maxAgeDays);
    }

    /**
     * Queries AlienVault OTX for threat intelligence pulses on an IOC.
     *
     * @param indicatorType {@code IPv4}, {@code IPv6}, {@code domain}, {@code hostname}, or {@code file}
     * @param indicator     IOC value
     * @return JSON string of OTX threat pulses
     */
    @NadiaTool(
            name = "otxGetIndicator",
            description = "Queries AlienVault Open Threat Exchange (OTX) for threat pulses and intelligence linked to an IP, domain, hostname, or file hash.",
            properties = {ThreatIntelPlugin.PROP_OTX_API_KEY}
    )
    public String otxGetIndicator(
            @NadiaToolParam(description = "Indicator type: 'IPv4' (default), 'IPv6', 'domain', 'hostname', or 'file'", required = false) String indicatorType,
            @NadiaToolParam(description = "Indicator value (IP, domain, or SHA256/MD5 hash)") String indicator) {
        return client().getOtxIndicator(indicatorType, indicator);
    }

    /**
     * Checks a URL or host against abuse.ch URLhaus malware feeds.
     *
     * @param target URL or hostname/IP
     * @return JSON string of URLhaus lookup results
     */
    @NadiaTool(
            name = "urlhausLookup",
            description = "Checks a URL, domain, or IP host against the abuse.ch URLhaus active malware distribution database.",
            properties = {ThreatIntelPlugin.PROP_URLHAUS_AUTH_KEY}
    )
    public String urlhausLookup(
            @NadiaToolParam(description = "Full URL (http/https) or hostname/IP to check in URLhaus") String target) {
        return client().lookupUrlhaus(target);
    }
}
