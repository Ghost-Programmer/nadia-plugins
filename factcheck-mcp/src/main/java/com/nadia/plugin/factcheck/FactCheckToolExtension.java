package com.nadia.plugin.factcheck;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible claim verification tools
 * via the Google Fact Check Tools API.
 */
@Extension
public class FactCheckToolExtension implements NadiaToolExtension {

    private final FactCheckClient explicitClient;

    /** Default PF4J constructor. */
    public FactCheckToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for testing
     */
    public FactCheckToolExtension(FactCheckClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private FactCheckClient client() {
        return explicitClient != null ? explicitClient : FactCheckPlugin.getClient();
    }

    /**
     * Searches published fact-checks for a breaking claim or statement.
     *
     * @param query        claim text or keywords
     * @param languageCode optional BCP-47 language code (e.g., {@code en})
     * @param maxAgeDays   optional max age in days
     * @param pageSize     max results (default 10)
     * @return JSON string of matching fact-check reviews
     */
    @NadiaTool(
            name = "factCheckSearchClaims",
            description = "Searches Google Fact Check Tools API for published independent fact-checks and ratings on breaking headlines or claims.",
            properties = {FactCheckPlugin.PROP_API_KEY}
    )
    public String factCheckSearchClaims(
            @NadiaToolParam(description = "Claim statement or keywords to fact-check") String query,
            @NadiaToolParam(description = "Optional BCP-47 language code (e.g., 'en', 'es', 'fr')", required = false) String languageCode,
            @NadiaToolParam(description = "Optional maximum age of fact-checks in days", required = false) Integer maxAgeDays,
            @NadiaToolParam(description = "Maximum fact-check claims to return (1-50, default 10)", required = false) Integer pageSize) {
        return client().searchClaims(query, languageCode, maxAgeDays, pageSize);
    }
}
