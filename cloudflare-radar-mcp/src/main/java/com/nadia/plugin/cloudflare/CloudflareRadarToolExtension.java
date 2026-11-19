package com.nadia.plugin.cloudflare;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for the Cloudflare Radar API v4.
 */
@Extension
public class CloudflareRadarToolExtension implements NadiaToolExtension {

    private final CloudflareRadarClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link CloudflareRadarRuntime#getInstance()}.
     */
    public CloudflareRadarToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit {@link CloudflareRadarClient}.
     *
     * @param explicitClient explicit client for unit testing
     */
    public CloudflareRadarToolExtension(CloudflareRadarClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private CloudflareRadarClient client() {
        return explicitClient != null ? explicitClient : CloudflareRadarRuntime.getInstance().getClient();
    }

    /**
     * Retrieves curated Internet outage annotations from Cloudflare Radar.
     *
     * @param dateRange optional time window (e.g., {@code 1d}, {@code 7d}, {@code 30d})
     * @param location  optional ISO-2 country code (e.g., {@code US}, {@code UA}, {@code IR})
     * @param asn       optional ASN filter
     * @param limit     optional max results (default 20, max 100)
     * @return JSON string of outage annotations
     */
    @NadiaTool(
            name = "cloudflareRadarGetOutages",
            description = "Retrieves observed global or regional Internet outages and shutdown annotations from the Cloudflare Radar Outage Center.",
            properties = {CloudflareRadarPlugin.PROP_API_TOKEN, CloudflareRadarPlugin.PROP_API_URL}
    )
    public String cloudflareRadarGetOutages(
            @NadiaToolParam(description = "Time window such as '1d', '7d', '14d', or '30d' (default '7d')", required = false) String dateRange,
            @NadiaToolParam(description = "Optional ISO 3166-1 alpha-2 country code (e.g., US, UA, DE)", required = false) String location,
            @NadiaToolParam(description = "Optional Autonomous System Number (ASN) filter", required = false) String asn,
            @NadiaToolParam(description = "Maximum number of outages to return (1-100, default 20)", required = false) Integer limit) {
        return client().getOutages(dateRange, location, asn, limit);
    }

    /**
     * Retrieves detected Internet traffic anomalies from Cloudflare Radar.
     *
     * @param dateRange optional time window (default {@code 7d})
     * @param location  optional ISO-2 country code
     * @param asn       optional ASN filter
     * @param status    optional anomaly status ({@code VERIFIED} or {@code UNVERIFIED})
     * @param limit     optional max results (default 20)
     * @return JSON string of traffic anomalies
     */
    @NadiaTool(
            name = "cloudflareRadarGetTrafficAnomalies",
            description = "Retrieves automated Internet traffic drop/spike anomalies detected by Cloudflare Radar across countries and ASNs.",
            properties = {CloudflareRadarPlugin.PROP_API_TOKEN, CloudflareRadarPlugin.PROP_API_URL}
    )
    public String cloudflareRadarGetTrafficAnomalies(
            @NadiaToolParam(description = "Time window such as '1d', '7d', or '30d' (default '7d')", required = false) String dateRange,
            @NadiaToolParam(description = "Optional ISO 3166-1 alpha-2 country code", required = false) String location,
            @NadiaToolParam(description = "Optional ASN filter", required = false) String asn,
            @NadiaToolParam(description = "Optional status filter: 'VERIFIED' or 'UNVERIFIED'", required = false) String status,
            @NadiaToolParam(description = "Maximum number of anomalies to return (1-100, default 20)", required = false) Integer limit) {
        return client().getTrafficAnomalies(dateRange, location, asn, status, limit);
    }

    /**
     * Retrieves top origin or target locations for Layer 7 HTTP/application attacks.
     *
     * @param direction {@code origin} or {@code target} (default {@code target})
     * @param dateRange optional time window (default {@code 7d})
     * @param limit     optional max locations (default 10)
     * @return JSON string of Layer 7 attack top locations
     */
    @NadiaTool(
            name = "cloudflareRadarGetLayer7Attacks",
            description = "Retrieves top origin or target countries for Layer 7 (HTTP/application-layer) DDoS and WAF-mitigated attacks from Cloudflare Radar.",
            properties = {CloudflareRadarPlugin.PROP_API_TOKEN, CloudflareRadarPlugin.PROP_API_URL}
    )
    public String cloudflareRadarGetLayer7Attacks(
            @NadiaToolParam(description = "Direction: 'target' (default) or 'origin'", required = false) String direction,
            @NadiaToolParam(description = "Time window such as '1d', '7d', or '30d' (default '7d')", required = false) String dateRange,
            @NadiaToolParam(description = "Maximum number of locations to return (1-100, default 10)", required = false) Integer limit) {
        return client().getLayer7Attacks(direction, dateRange, limit);
    }

    /**
     * Retrieves top origin or target locations for Layer 3/4 network-layer DDoS attacks.
     *
     * @param direction {@code origin} or {@code target} (default {@code target})
     * @param dateRange optional time window (default {@code 7d})
     * @param limit     optional max locations (default 10)
     * @return JSON string of Layer 3 attack top locations
     */
    @NadiaTool(
            name = "cloudflareRadarGetLayer3Attacks",
            description = "Retrieves top origin or target countries for Layer 3/4 network-layer DDoS attacks from Cloudflare Radar.",
            properties = {CloudflareRadarPlugin.PROP_API_TOKEN, CloudflareRadarPlugin.PROP_API_URL}
    )
    public String cloudflareRadarGetLayer3Attacks(
            @NadiaToolParam(description = "Direction: 'target' (default) or 'origin'", required = false) String direction,
            @NadiaToolParam(description = "Time window such as '1d', '7d', or '30d' (default '7d')", required = false) String dateRange,
            @NadiaToolParam(description = "Maximum number of locations to return (1-100, default 10)", required = false) Integer limit) {
        return client().getLayer3Attacks(direction, dateRange, limit);
    }

    /**
     * Retrieves BGP hijack or route-leak events from Cloudflare Radar.
     *
     * @param eventType {@code hijacks} (default) or {@code leaks}
     * @param dateRange optional time window (default {@code 7d})
     * @param asn       optional involved ASN filter
     * @param limit     optional max events (default 20)
     * @return JSON string of BGP routing security events
     */
    @NadiaTool(
            name = "cloudflareRadarGetBgpEvents",
            description = "Retrieves BGP hijack or route-leak security events from Cloudflare Radar.",
            properties = {CloudflareRadarPlugin.PROP_API_TOKEN, CloudflareRadarPlugin.PROP_API_URL}
    )
    public String cloudflareRadarGetBgpEvents(
            @NadiaToolParam(description = "Event type: 'hijacks' (default) or 'leaks'", required = false) String eventType,
            @NadiaToolParam(description = "Time window such as '1d', '7d', or '30d' (default '7d')", required = false) String dateRange,
            @NadiaToolParam(description = "Optional involved ASN filter", required = false) String asn,
            @NadiaToolParam(description = "Maximum number of events to return (1-100, default 20)", required = false) Integer limit) {
        return client().getBgpEvents(eventType, dateRange, asn, limit);
    }
}
