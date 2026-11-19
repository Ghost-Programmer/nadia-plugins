package com.nadia.plugin.radhealth;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible environmental radiation and
 * WHO public-health outbreak alert tools.
 */
@Extension
public class RadiationHealthToolExtension implements NadiaToolExtension {

    private final RadiationHealthClient explicitClient;

    /** Default PF4J constructor. */
    public RadiationHealthToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public RadiationHealthToolExtension(RadiationHealthClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private RadiationHealthClient client() {
        return explicitClient != null ? explicitClient : RadiationHealthPlugin.getClient();
    }

    /**
     * Retrieves environmental radiation readings near a location from Safecast.
     *
     * @param latitude   latitude in decimal degrees
     * @param longitude  longitude in decimal degrees
     * @param distanceKm optional search radius in km (default 100)
     * @param limit      max measurements (default 25)
     * @return JSON string of radiation readings
     */
    @NadiaTool(
            name = "radiationGetReadings",
            description = "Retrieves environmental ionizing radiation measurements (CPM / uSv/h) near a latitude/longitude coordinate via Safecast (no API key required).",
            properties = {RadiationHealthPlugin.PROP_SAFECAST_API_URL}
    )
    public String radiationGetReadings(
            @NadiaToolParam(description = "Latitude in decimal degrees (e.g., 37.421)") Double latitude,
            @NadiaToolParam(description = "Longitude in decimal degrees (e.g., 141.032)") Double longitude,
            @NadiaToolParam(description = "Search radius in kilometers (default 100, max 1000)", required = false) Double distanceKm,
            @NadiaToolParam(description = "Maximum measurements to return (1-100, default 25)", required = false) Integer limit) {
        return client().getRadiationReadings(latitude, longitude, distanceKm, limit);
    }

    /**
     * Retrieves recent WHO Disease Outbreak News (DON) epidemic and public health alerts.
     *
     * @param query optional disease or country filter
     * @param limit max alerts (default 15)
     * @return JSON string of WHO outbreak alerts
     */
    @NadiaTool(
            name = "whoGetOutbreakNews",
            description = "Retrieves official World Health Organization (WHO) Disease Outbreak News (DON) epidemic and public health alerts (no API key required).",
            properties = {RadiationHealthPlugin.PROP_WHO_DON_API_URL}
    )
    public String whoGetOutbreakNews(
            @NadiaToolParam(description = "Optional keyword filter (e.g., 'mpox', 'cholera', 'avian influenza', country name)", required = false) String query,
            @NadiaToolParam(description = "Maximum alerts to return (1-50, default 15)", required = false) Integer limit) {
        return client().getWhoOutbreakNews(query, limit);
    }
}
