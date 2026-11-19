package com.nadia.plugin.maritime;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible strategic maritime chokepoint
 * and NGA Maritime Safety Information (MSI) disruption alert tools.
 */
@Extension
public class MaritimeChokepointToolExtension implements NadiaToolExtension {

    private final MaritimeChokepointClient explicitClient;

    /** Default PF4J constructor. */
    public MaritimeChokepointToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public MaritimeChokepointToolExtension(MaritimeChokepointClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private MaritimeChokepointClient client() {
        return explicitClient != null ? explicitClient : MaritimeChokepointPlugin.getClient();
    }

    /**
     * Lists strategic global maritime chokepoints with bounding boxes, NAVAREAs, and risk profiles.
     *
     * @param filter optional chokepoint name or ID filter
     * @return JSON list of chokepoints
     */
    @NadiaTool(
            name = "maritimeListChokepoints",
            description = "Lists strategic global maritime chokepoints (Hormuz, Bab el-Mandeb, Suez, Malacca, Panama, Turkish Straits, Taiwan Strait, Danish Straits) with coordinates, bounding boxes, and trade risk profiles."
    )
    public String maritimeListChokepoints(
            @NadiaToolParam(description = "Optional chokepoint ID or name filter (e.g., 'hormuz', 'suez', 'malacca')", required = false) String filter) {
        return client().listChokepoints(filter);
    }

    /**
     * Retrieves active NGA Maritime Safety Information (MSI) navigational and security warnings.
     *
     * @param chokepointOrQuery optional chokepoint ID or keyword filter
     * @param navArea           optional NAVAREA filter (e.g., {@code IX}, {@code III})
     * @param limit             max warnings (default 20)
     * @return JSON string of active maritime warnings
     */
    @NadiaTool(
            name = "maritimeGetDisruptions",
            description = "Retrieves active NGA Maritime Safety Information (MSI) broadcast navigational warnings, naval hazard notices, and maritime disruptions (no API key required).",
            properties = {MaritimeChokepointPlugin.PROP_NGA_MSI_URL, MaritimeChokepointPlugin.PROP_AISSTREAM_API_KEY}
    )
    public String maritimeGetDisruptions(
            @NadiaToolParam(description = "Optional chokepoint ID ('hormuz', 'bab-el-mandeb', 'suez', 'malacca') or keyword", required = false) String chokepointOrQuery,
            @NadiaToolParam(description = "Optional NAVAREA code (e.g., 'IX', 'III', 'XI', 'IV')", required = false) String navArea,
            @NadiaToolParam(description = "Maximum warnings to return (1-100, default 20)", required = false) Integer limit) {
        return client().getMaritimeDisruptions(chokepointOrQuery, navArea, limit);
    }
}
