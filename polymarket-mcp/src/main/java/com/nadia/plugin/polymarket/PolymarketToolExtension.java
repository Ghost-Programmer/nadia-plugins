package com.nadia.plugin.polymarket;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible prediction market tools
 * for Polymarket and Metaculus.
 */
@Extension
public class PolymarketToolExtension implements NadiaToolExtension {

    private final PolymarketClient explicitClient;

    /** Default PF4J constructor. */
    public PolymarketToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public PolymarketToolExtension(PolymarketClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private PolymarketClient client() {
        return explicitClient != null ? explicitClient : PolymarketPlugin.getClient();
    }

    /**
     * Searches Polymarket prediction markets for geopolitical, economic, and current-event odds.
     *
     * @param query      optional keyword filter
     * @param activeOnly optional filter for active/open markets (default {@code true})
     * @param limit      max markets to return (default 15, max 50)
     * @return JSON string of markets with implied outcome probabilities
     */
    @NadiaTool(
            name = "polymarketSearchMarkets",
            description = "Searches Polymarket prediction markets for live crowd-implied probabilities and 24h volume on geopolitical, economic, and tech events.",
            properties = {PolymarketPlugin.PROP_POLYMARKET_API_URL}
    )
    public String polymarketSearchMarkets(
            @NadiaToolParam(description = "Optional keyword filter (e.g., 'Fed', 'ceasefire', 'tariff', 'recession')", required = false) String query,
            @NadiaToolParam(description = "Set true (default) to return only active open markets", required = false) Boolean activeOnly,
            @NadiaToolParam(description = "Maximum markets to return (1-50, default 15)", required = false) Integer limit) {
        return client().searchMarkets(query, activeOnly, limit);
    }

    /**
     * Searches Metaculus community forecasting questions.
     *
     * @param query  optional keyword filter
     * @param status {@code open} (default), {@code closed}, or {@code resolved}
     * @param limit  max questions to return (default 15, max 50)
     * @return JSON string of Metaculus forecasting questions
     */
    @NadiaTool(
            name = "metaculusSearchQuestions",
            description = "Searches Metaculus superforecasting community questions and consensus probability forecasts.",
            properties = {PolymarketPlugin.PROP_METACULUS_API_URL}
    )
    public String metaculusSearchQuestions(
            @NadiaToolParam(description = "Optional search query", required = false) String query,
            @NadiaToolParam(description = "Status: 'open' (default), 'closed', or 'resolved'", required = false) String status,
            @NadiaToolParam(description = "Maximum questions to return (1-50, default 15)", required = false) Integer limit) {
        return client().searchMetaculus(query, status, limit);
    }
}
