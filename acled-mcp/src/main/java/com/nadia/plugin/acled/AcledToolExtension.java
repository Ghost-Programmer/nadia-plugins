package com.nadia.plugin.acled;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for ACLED
 * (Armed Conflict Location &amp; Event Data) and CAST conflict forecasting.
 */
@Extension
public class AcledToolExtension implements NadiaToolExtension {

    private final AcledClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link AcledRuntime#getInstance()}.
     */
    public AcledToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit {@link AcledClient}.
     *
     * @param explicitClient explicit client for unit testing
     */
    public AcledToolExtension(AcledClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private AcledClient client() {
        return explicitClient != null ? explicitClient : AcledRuntime.getInstance().getClient();
    }

    /**
     * Searches ACLED armed conflict, protest, and political violence events.
     *
     * @param country      optional country filter
     * @param region       optional ACLED region filter
     * @param eventType    optional event type (e.g., Battles, Protests, Riots)
     * @param subEventType optional sub-event type
     * @param actor        optional primary actor name filter
     * @param startDate    optional ISO start date (YYYY-MM-DD)
     * @param endDate      optional ISO end date (YYYY-MM-DD)
     * @param limit        optional max events (default 25, max 500)
     * @return JSON string of matching ACLED events
     */
    @NadiaTool(
            name = "acledSearchEvents",
            description = "Searches ACLED (Armed Conflict Location & Event Data) political violence, battle, protest, and fatality events by country, region, event type, actor, and date range.",
            properties = {AcledPlugin.PROP_EMAIL, AcledPlugin.PROP_PASSWORD, AcledPlugin.PROP_API_KEY, AcledPlugin.PROP_API_URL}
    )
    public String acledSearchEvents(
            @NadiaToolParam(description = "Optional country name filter (e.g., Ukraine, Sudan, Myanmar)", required = false) String country,
            @NadiaToolParam(description = "Optional ACLED region name filter", required = false) String region,
            @NadiaToolParam(description = "Optional event type (e.g., 'Battles', 'Protests', 'Explosions/Remote violence', 'Violence against civilians')", required = false) String eventType,
            @NadiaToolParam(description = "Optional sub-event type filter", required = false) String subEventType,
            @NadiaToolParam(description = "Optional actor name substring filter", required = false) String actor,
            @NadiaToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String startDate,
            @NadiaToolParam(description = "Optional end date in YYYY-MM-DD format", required = false) String endDate,
            @NadiaToolParam(description = "Maximum number of events to return (1-500, default 25)", required = false) Integer limit) {
        return client().searchEvents(country, region, eventType, subEventType, actor, startDate, endDate, limit);
    }

    /**
     * Aggregates ACLED conflict statistics and fatalities for a specific country.
     *
     * @param country   target country name
     * @param startDate optional start date (YYYY-MM-DD)
     * @param endDate   optional end date (YYYY-MM-DD)
     * @param limit     optional sample size (default 250, max 500)
     * @return JSON summary with event counts, fatalities, event types, actors, and admin regions
     */
    @NadiaTool(
            name = "acledGetCountrySummary",
            description = "Aggregates ACLED conflict event counts, total fatalities, event type breakdowns, top actors, and affected administrative regions for a country and date window.",
            properties = {AcledPlugin.PROP_EMAIL, AcledPlugin.PROP_PASSWORD, AcledPlugin.PROP_API_KEY, AcledPlugin.PROP_API_URL}
    )
    public String acledGetCountrySummary(
            @NadiaToolParam(description = "Country name to summarize (e.g., Sudan, Ukraine)") String country,
            @NadiaToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String startDate,
            @NadiaToolParam(description = "Optional end date in YYYY-MM-DD format", required = false) String endDate,
            @NadiaToolParam(description = "Maximum events to aggregate (1-500, default 250)", required = false) Integer limit) {
        return client().getCountrySummary(country, startDate, endDate, limit);
    }

    /**
     * Retrieves ACLED Conflict Alert System (CAST) predicted conflict forecasts.
     *
     * @param country optional country filter
     * @param admin1  optional first-level administrative region filter
     * @param limit   optional max forecast rows (default 25, max 500)
     * @return JSON string of CAST forecast entries
     */
    @NadiaTool(
            name = "acledGetCastForecast",
            description = "Retrieves ACLED Conflict Alert System (CAST) predicted conflict levels by country and administrative region.",
            properties = {AcledPlugin.PROP_EMAIL, AcledPlugin.PROP_PASSWORD, AcledPlugin.PROP_API_KEY}
    )
    public String acledGetCastForecast(
            @NadiaToolParam(description = "Optional country name filter", required = false) String country,
            @NadiaToolParam(description = "Optional first-level administrative region (admin1) filter", required = false) String admin1,
            @NadiaToolParam(description = "Maximum number of forecast rows to return (1-500, default 25)", required = false) Integer limit) {
        return client().getCastForecast(country, admin1, limit);
    }
}
