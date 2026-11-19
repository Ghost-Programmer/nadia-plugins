package com.nadia.plugin.gdelt;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for the GDELT Project 2.0 APIs.
 */
@Extension
public class GdeltToolExtension implements NadiaToolExtension {

    private final GdeltClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link GdeltRuntime#getInstance()}.
     */
    public GdeltToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit {@link GdeltClient}.
     *
     * @param explicitClient explicit client for unit testing
     */
    public GdeltToolExtension(GdeltClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private GdeltClient client() {
        return explicitClient != null ? explicitClient : GdeltRuntime.getInstance().getClient();
    }

    /**
     * Searches global news coverage via GDELT DOC 2.0.
     *
     * @param query         search query terms or boolean expression
     * @param timespan      optional timespan (e.g., {@code 24h}, {@code 7d}, {@code 1m})
     * @param sourceCountry optional FIPS 2-letter source country code
     * @param sourceLang    optional source language name (e.g., {@code english})
     * @param theme         optional GDELT GKG theme (e.g., {@code CYBER_ATTACK}, {@code PROTEST})
     * @param sort          optional sort order ({@code DateDesc}, {@code HybridRel}, {@code ToneDesc}, {@code ToneAsc})
     * @param maxRecords    optional max articles (default 25, max 250)
     * @return JSON string of matching GDELT news articles
     */
    @NadiaTool(
            name = "gdeltSearchArticles",
            description = "Searches real-time global news articles from the GDELT Project DOC 2.0 API by keyword, GKG theme, source country, language, and timespan.",
            properties = {GdeltPlugin.PROP_DOC_API_URL}
    )
    public String gdeltSearchArticles(
            @NadiaToolParam(description = "Search query or boolean expression (e.g., 'ransomware infrastructure')", required = false) String query,
            @NadiaToolParam(description = "Time window such as '24h', '7d', or '1m' (default '7d')", required = false) String timespan,
            @NadiaToolParam(description = "Optional FIPS 2-letter source country code (e.g., US, UK, GM)", required = false) String sourceCountry,
            @NadiaToolParam(description = "Optional source language filter (e.g., english, spanish, french)", required = false) String sourceLang,
            @NadiaToolParam(description = "Optional GDELT GKG theme (e.g., CYBER_ATTACK, PROTEST, NATURAL_DISASTER)", required = false) String theme,
            @NadiaToolParam(description = "Sort order: 'DateDesc' (default), 'HybridRel', 'ToneDesc', or 'ToneAsc'", required = false) String sort,
            @NadiaToolParam(description = "Maximum number of articles to return (1-250, default 25)", required = false) Integer maxRecords) {
        return client().searchArticles(query, timespan, sourceCountry, sourceLang, theme, sort, maxRecords);
    }

    /**
     * Retrieves media coverage volume or tone timeline from GDELT DOC 2.0.
     *
     * @param query    search query expression
     * @param mode     {@code timelinevol} (default), {@code timelinetone}, or {@code timelinevolraw}
     * @param timespan optional timespan (default {@code 14d})
     * @return JSON string with timeline series
     */
    @NadiaTool(
            name = "gdeltGetTimeline",
            description = "Retrieves global news coverage volume or average sentiment tone over time from the GDELT Project DOC 2.0 API.",
            properties = {GdeltPlugin.PROP_DOC_API_URL}
    )
    public String gdeltGetTimeline(
            @NadiaToolParam(description = "Search query expression to track over time") String query,
            @NadiaToolParam(description = "Timeline mode: 'timelinevol' (default), 'timelinetone', or 'timelinevolraw'", required = false) String mode,
            @NadiaToolParam(description = "Time window such as '7d', '14d', '1m', or '3m' (default '14d')", required = false) String timespan) {
        return client().getTimeline(query, mode, timespan);
    }

    /**
     * Retrieves geocoded global event locations from GDELT GEO 2.0.
     *
     * @param query     search query expression
     * @param timespan  optional timespan (default {@code 7d})
     * @param maxPoints optional max GeoJSON points (default 50, max 250)
     * @return JSON string containing GeoJSON points
     */
    @NadiaTool(
            name = "gdeltSearchGeoEvents",
            description = "Retrieves geocoded global event locations and coordinates as GeoJSON from the GDELT Project GEO 2.0 API.",
            properties = {GdeltPlugin.PROP_GEO_API_URL}
    )
    public String gdeltSearchGeoEvents(
            @NadiaToolParam(description = "Search query expression for geocoded events") String query,
            @NadiaToolParam(description = "Time window such as '24h' or '7d' (default '7d')", required = false) String timespan,
            @NadiaToolParam(description = "Maximum number of geographic points to return (1-250, default 50)", required = false) Integer maxPoints) {
        return client().searchGeoEvents(query, timespan, maxPoints);
    }
}
