package com.nadia.plugin.reliefweb;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for the UN OCHA ReliefWeb API v2.
 */
@Extension
public class ReliefWebToolExtension implements NadiaToolExtension {

    private final ReliefWebClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link ReliefWebRuntime#getInstance()}.
     */
    public ReliefWebToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit {@link ReliefWebClient}.
     *
     * @param explicitClient explicit client for unit testing
     */
    public ReliefWebToolExtension(ReliefWebClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private ReliefWebClient client() {
        return explicitClient != null ? explicitClient : ReliefWebRuntime.getInstance().getClient();
    }

    /**
     * Searches ReliefWeb humanitarian situation reports and updates.
     *
     * @param query    optional full-text search query
     * @param country  optional country name filter
     * @param disaster optional disaster name filter
     * @param limit    optional max reports (default 15, max 100)
     * @return JSON string of matching reports
     */
    @NadiaTool(
            name = "reliefwebSearchReports",
            description = "Searches UN OCHA ReliefWeb humanitarian situation reports, press releases, and analyses by keyword, country, or disaster.",
            properties = {ReliefWebPlugin.PROP_APPNAME, ReliefWebPlugin.PROP_API_URL}
    )
    public String reliefwebSearchReports(
            @NadiaToolParam(description = "Optional full-text search query (e.g., 'cholera outbreak', 'displacement')", required = false) String query,
            @NadiaToolParam(description = "Optional country name filter (e.g., Sudan, Ukraine, Haiti)", required = false) String country,
            @NadiaToolParam(description = "Optional disaster name filter", required = false) String disaster,
            @NadiaToolParam(description = "Maximum number of reports to return (1-100, default 15)", required = false) Integer limit) {
        return client().searchReports(query, country, disaster, limit);
    }

    /**
     * Retrieves full details of a specific ReliefWeb report by ID.
     *
     * @param reportId ReliefWeb report ID
     * @return JSON string with full report content
     */
    @NadiaTool(
            name = "reliefwebGetReport",
            description = "Retrieves the full content and metadata of a specific UN OCHA ReliefWeb report by its numeric ID.",
            properties = {ReliefWebPlugin.PROP_APPNAME, ReliefWebPlugin.PROP_API_URL}
    )
    public String reliefwebGetReport(
            @NadiaToolParam(description = "The numeric ReliefWeb report ID") String reportId) {
        return client().getReportById(reportId);
    }

    /**
     * Searches ReliefWeb disaster emergencies.
     *
     * @param query   optional keyword query
     * @param country optional country name filter
     * @param status  optional status filter ({@code current}, {@code alert}, {@code past})
     * @param limit   optional max disasters (default 15, max 100)
     * @return JSON string of matching disasters
     */
    @NadiaTool(
            name = "reliefwebSearchDisasters",
            description = "Searches UN OCHA ReliefWeb emergency disasters (earthquakes, floods, droughts, epidemics) by keyword, country, or status.",
            properties = {ReliefWebPlugin.PROP_APPNAME, ReliefWebPlugin.PROP_API_URL}
    )
    public String reliefwebSearchDisasters(
            @NadiaToolParam(description = "Optional keyword search (e.g., 'earthquake', 'cyclone')", required = false) String query,
            @NadiaToolParam(description = "Optional country name filter", required = false) String country,
            @NadiaToolParam(description = "Optional status filter: 'current', 'alert', or 'past'", required = false) String status,
            @NadiaToolParam(description = "Maximum number of disasters to return (1-100, default 15)", required = false) Integer limit) {
        return client().searchDisasters(query, country, status, limit);
    }

    /**
     * Retrieves full details of a specific ReliefWeb disaster by ID.
     *
     * @param disasterId ReliefWeb disaster ID
     * @return JSON string with full disaster details
     */
    @NadiaTool(
            name = "reliefwebGetDisaster",
            description = "Retrieves full details and GLIDE metadata for a specific UN OCHA ReliefWeb disaster by its numeric ID.",
            properties = {ReliefWebPlugin.PROP_APPNAME, ReliefWebPlugin.PROP_API_URL}
    )
    public String reliefwebGetDisaster(
            @NadiaToolParam(description = "The numeric ReliefWeb disaster ID") String disasterId) {
        return client().getDisasterById(disasterId);
    }

    /**
     * Searches ReliefWeb country crisis profiles.
     *
     * @param query  optional country search term
     * @param status optional status filter ({@code ongoing}, {@code normal})
     * @param limit  optional max countries (default 15, max 100)
     * @return JSON string of country crisis profiles
     */
    @NadiaTool(
            name = "reliefwebSearchCountries",
            description = "Searches UN OCHA ReliefWeb country profiles and ongoing humanitarian crisis statuses.",
            properties = {ReliefWebPlugin.PROP_APPNAME, ReliefWebPlugin.PROP_API_URL}
    )
    public String reliefwebSearchCountries(
            @NadiaToolParam(description = "Optional country name or ISO3 query", required = false) String query,
            @NadiaToolParam(description = "Optional humanitarian status filter: 'ongoing' or 'normal'", required = false) String status,
            @NadiaToolParam(description = "Maximum number of countries to return (1-100, default 15)", required = false) Integer limit) {
        return client().searchCountries(query, status, limit);
    }
}
