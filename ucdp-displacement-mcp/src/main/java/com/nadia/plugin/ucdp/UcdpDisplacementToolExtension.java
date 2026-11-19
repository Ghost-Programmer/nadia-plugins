package com.nadia.plugin.ucdp;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for UCDP armed conflict events
 * and UNHCR forced displacement statistics.
 */
@Extension
public class UcdpDisplacementToolExtension implements NadiaToolExtension {

    private final UcdpDisplacementClient explicitClient;

    /** Default PF4J constructor. */
    public UcdpDisplacementToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public UcdpDisplacementToolExtension(UcdpDisplacementClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private UcdpDisplacementClient client() {
        return explicitClient != null ? explicitClient : UcdpDisplacementPlugin.getClient();
    }

    /**
     * Searches UCDP Georeferenced Event Dataset (GED) armed conflict events.
     *
     * @param country   optional country name or code
     * @param startDate optional ISO start date (YYYY-MM-DD)
     * @param endDate   optional ISO end date (YYYY-MM-DD)
     * @param pageSize  optional max records (default 25, max 100)
     * @return JSON string of UCDP GED conflict events
     */
    @NadiaTool(
            name = "ucdpSearchGedEvents",
            description = "Searches Uppsala Conflict Data Program (UCDP) Georeferenced Event Dataset (GED) armed conflict events by country and date range.",
            properties = {UcdpDisplacementPlugin.PROP_UCDP_TOKEN, UcdpDisplacementPlugin.PROP_UCDP_API_URL}
    )
    public String ucdpSearchGedEvents(
            @NadiaToolParam(description = "Optional country name or UCDP country code", required = false) String country,
            @NadiaToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String startDate,
            @NadiaToolParam(description = "Optional end date in YYYY-MM-DD format", required = false) String endDate,
            @NadiaToolParam(description = "Maximum events to return (1-100, default 25)", required = false) Integer pageSize) {
        return client().searchGedEvents(country, startDate, endDate, pageSize);
    }

    /**
     * Retrieves UNHCR refugee, asylum-seeker, and internally displaced population statistics.
     *
     * @param cooIso3 optional country of origin ISO-3 code (e.g., UKR, SDN)
     * @param coaIso3 optional country of asylum ISO-3 code (e.g., DEU, POL)
     * @param year    optional year (e.g., 2024)
     * @param limit   optional max rows (default 25, max 100)
     * @return JSON string of UNHCR population statistics
     */
    @NadiaTool(
            name = "unhcrGetDisplacementStats",
            description = "Retrieves UNHCR refugee, asylum-seeker, and internally displaced person (IDP) statistics by origin country, asylum country, and year.",
            properties = {UcdpDisplacementPlugin.PROP_UNHCR_API_URL}
    )
    public String unhcrGetDisplacementStats(
            @NadiaToolParam(description = "Optional ISO-3 country of origin code (e.g., UKR, SDN, SYR)", required = false) String cooIso3,
            @NadiaToolParam(description = "Optional ISO-3 country of asylum code (e.g., DEU, POL, TCD)", required = false) String coaIso3,
            @NadiaToolParam(description = "Optional statistics year (e.g., 2024)", required = false) Integer year,
            @NadiaToolParam(description = "Maximum records to return (1-100, default 25)", required = false) Integer limit) {
        return client().getDisplacementStats(cooIso3, coaIso3, year, limit);
    }
}
