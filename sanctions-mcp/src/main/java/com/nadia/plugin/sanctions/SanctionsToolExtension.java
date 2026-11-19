package com.nadia.plugin.sanctions;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible sanctions screening tools
 * backed by OpenSanctions and the U.S. Treasury OFAC SDN list.
 */
@Extension
public class SanctionsToolExtension implements NadiaToolExtension {

    private final SanctionsClient explicitClient;

    /** Default PF4J constructor. */
    public SanctionsToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public SanctionsToolExtension(SanctionsClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private SanctionsClient client() {
        return explicitClient != null ? explicitClient : SanctionsPlugin.getClient();
    }

    /**
     * Screens an individual, organization, or identifier against OpenSanctions or OFAC SDN lists.
     *
     * @param query   name or identifier to screen
     * @param schema  optional entity type ({@code Person}, {@code Company}, {@code Vessel}, {@code Airplane})
     * @param country optional ISO-2 country filter
     * @param limit   max matches (default 20)
     * @return JSON screening result
     */
    @NadiaTool(
            name = "sanctionsSearchEntity",
            description = "Screens a person, organization, or identifier against OpenSanctions (when configured) or the official U.S. Treasury OFAC SDN sanctions list.",
            properties = {SanctionsPlugin.PROP_OPENSANCTIONS_API_KEY, SanctionsPlugin.PROP_OPENSANCTIONS_API_URL, SanctionsPlugin.PROP_OFAC_SDN_URL}
    )
    public String sanctionsSearchEntity(
            @NadiaToolParam(description = "Entity name, alias, or identifier to screen") String query,
            @NadiaToolParam(description = "Optional entity type: 'Person', 'Company', 'Vessel', or 'Airplane'", required = false) String schema,
            @NadiaToolParam(description = "Optional country name or ISO-2 code filter", required = false) String country,
            @NadiaToolParam(description = "Maximum matches to return (1-100, default 20)", required = false) Integer limit) {
        return client().searchEntity(query, schema, country, limit);
    }

    /**
     * Screens a maritime vessel or aircraft against sanctions lists by name, IMO/MMSI, or tail number.
     *
     * @param identifier vessel name, IMO, MMSI, or aircraft registration
     * @param assetType  {@code vessel} (default) or {@code aircraft}
     * @param limit      max matches (default 15)
     * @return JSON screening result
     */
    @NadiaTool(
            name = "sanctionsCheckVesselOrAircraft",
            description = "Screens a maritime vessel (name/IMO/MMSI) or aircraft (tail/registration number) against sanctions lists.",
            properties = {SanctionsPlugin.PROP_OPENSANCTIONS_API_KEY, SanctionsPlugin.PROP_OFAC_SDN_URL}
    )
    public String sanctionsCheckVesselOrAircraft(
            @NadiaToolParam(description = "Vessel name/IMO/MMSI or aircraft tail number") String identifier,
            @NadiaToolParam(description = "Asset type: 'vessel' (default) or 'aircraft'", required = false) String assetType,
            @NadiaToolParam(description = "Maximum matches to return (1-100, default 15)", required = false) Integer limit) {
        return client().checkVesselOrAircraft(identifier, assetType, limit);
    }
}
