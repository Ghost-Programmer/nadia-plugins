package com.nadia.plugin.firms;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for NASA FIRMS active fires
 * and NASA EONET natural hazard events.
 */
@Extension
public class NasaFirmsToolExtension implements NadiaToolExtension {

    private final NasaFirmsClient explicitClient;

    /** Default PF4J constructor. */
    public NasaFirmsToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for testing
     */
    public NasaFirmsToolExtension(NasaFirmsClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private NasaFirmsClient client() {
        return explicitClient != null ? explicitClient : NasaFirmsPlugin.getClient();
    }

    /**
     * Queries NASA FIRMS satellite thermal anomalies and active wildfires.
     *
     * @param bbox     bounding box {@code west,south,east,north} or {@code world}
     * @param source   sensor name (e.g., {@code VIIRS_SNPP_NRT}, {@code MODIS_NRT})
     * @param dayRange lookback days (1-10)
     * @param limit    max rows to return (default 50)
     * @return JSON string of active fire detections
     */
    @NadiaTool(
            name = "firmsGetActiveFires",
            description = "Retrieves near-real-time satellite active fire and thermal anomaly detections from NASA FIRMS by bounding box and lookback window.",
            properties = {NasaFirmsPlugin.PROP_FIRMS_API_KEY}
    )
    public String firmsGetActiveFires(
            @NadiaToolParam(description = "Bounding box 'west,south,east,north' (e.g., '-125,32,-114,42') or 'world' (default)", required = false) String bbox,
            @NadiaToolParam(description = "Satellite sensor: 'VIIRS_SNPP_NRT' (default), 'VIIRS_NOAA20_NRT', or 'MODIS_NRT'", required = false) String source,
            @NadiaToolParam(description = "Number of lookback days (1-10, default 1)", required = false) Integer dayRange,
            @NadiaToolParam(description = "Maximum fire detections to return (1-200, default 50)", required = false) Integer limit) {
        return client().getActiveFires(bbox, source, dayRange, limit);
    }

    /**
     * Searches NASA EONET v3 natural hazard events (wildfires, volcanoes, severe storms, floods).
     *
     * @param category optional category slug
     * @param status   {@code open} (default) or {@code closed}
     * @param days     optional lookback days
     * @param limit    max events (default 20)
     * @return JSON string of natural hazard events
     */
    @NadiaTool(
            name = "eonetSearchNaturalEvents",
            description = "Searches NASA EONET v3 natural hazard events (wildfires, volcanoes, severe storms, floods, earthquakes, icebergs) without requiring an API key.",
            properties = {NasaFirmsPlugin.PROP_EONET_API_URL}
    )
    public String eonetSearchNaturalEvents(
            @NadiaToolParam(description = "Optional category slug (e.g., 'wildfires', 'volcanoes', 'severeStorms', 'floods')", required = false) String category,
            @NadiaToolParam(description = "Event status: 'open' (default) or 'closed'", required = false) String status,
            @NadiaToolParam(description = "Optional lookback window in days (e.g., 14, 30)", required = false) Integer days,
            @NadiaToolParam(description = "Maximum events to return (1-100, default 20)", required = false) Integer limit) {
        return client().searchNaturalEvents(category, status, days, limit);
    }
}
