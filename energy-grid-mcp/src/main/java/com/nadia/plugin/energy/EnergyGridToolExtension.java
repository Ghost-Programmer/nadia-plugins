package com.nadia.plugin.energy;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible energy and grid telemetry tools
 * for U.S. EIA, GIE AGSI+ EU gas storage, and ENTSO-E European grid load.
 */
@Extension
public class EnergyGridToolExtension implements NadiaToolExtension {

    private final EnergyGridClient explicitClient;

    /** Default PF4J constructor. */
    public EnergyGridToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for testing
     */
    public EnergyGridToolExtension(EnergyGridClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private EnergyGridClient client() {
        return explicitClient != null ? explicitClient : EnergyGridPlugin.getClient();
    }

    /**
     * Queries U.S. EIA v2 oil, natural gas, or electricity data series.
     *
     * @param route     EIA v2 route (default {@code petroleum/pri/spt/data})
     * @param frequency optional frequency ({@code daily}, {@code weekly}, {@code monthly})
     * @param length    max rows (default 20)
     * @return JSON string of EIA series observations
     */
    @NadiaTool(
            name = "eiaGetEnergySeries",
            description = "Queries U.S. Energy Information Administration (EIA) v2 data for crude oil prices, natural gas storage, petroleum inventories, and electricity generation.",
            properties = {EnergyGridPlugin.PROP_EIA_API_KEY}
    )
    public String eiaGetEnergySeries(
            @NadiaToolParam(description = "EIA v2 route path (default 'petroleum/pri/spt/data' for WTI/Brent spot prices)", required = false) String route,
            @NadiaToolParam(description = "Optional frequency: 'daily', 'weekly', or 'monthly'", required = false) String frequency,
            @NadiaToolParam(description = "Maximum data rows to return (1-100, default 20)", required = false) Integer length) {
        return client().getEiaSeries(route, frequency, length);
    }

    /**
     * Retrieves European Union natural gas storage fill levels from GIE AGSI+.
     *
     * @param country optional ISO-2 country code or {@code EU}
     * @param size    max daily rows (default 10)
     * @return JSON string of EU gas storage fill statistics
     */
    @NadiaTool(
            name = "gieGetEuGasStorage",
            description = "Retrieves European Union natural gas storage fill percentages, injection, and withdrawal rates from GIE AGSI+.",
            properties = {EnergyGridPlugin.PROP_GIE_API_KEY}
    )
    public String gieGetEuGasStorage(
            @NadiaToolParam(description = "ISO-2 European country code (e.g., DE, FR, IT) or 'EU' for aggregate (default 'EU')", required = false) String country,
            @NadiaToolParam(description = "Maximum daily records to return (1-100, default 10)", required = false) Integer size) {
        return client().getGieEuGasStorage(country, size);
    }

    /**
     * Queries European electrical grid actual total load from ENTSO-E.
     *
     * @param biddingZoneEic ENTSO-E EIC code
     * @param periodStart    UTC start ({@code yyyyMMddHHmm})
     * @param periodEnd      UTC end ({@code yyyyMMddHHmm})
     * @return JSON string with ENTSO-E grid load XML payload
     */
    @NadiaTool(
            name = "entsoeGetGridLoad",
            description = "Queries European electrical power grid actual total load from the ENTSO-E Transparency Platform.",
            properties = {EnergyGridPlugin.PROP_ENTSOE_TOKEN}
    )
    public String entsoeGetGridLoad(
            @NadiaToolParam(description = "ENTSO-E bidding zone EIC code (e.g., 10Y1001A1001A83F for Germany)") String biddingZoneEic,
            @NadiaToolParam(description = "UTC start timestamp in yyyyMMddHHmm format") String periodStart,
            @NadiaToolParam(description = "UTC end timestamp in yyyyMMddHHmm format") String periodEnd) {
        return client().getEntsoeGridLoad(biddingZoneEic, periodStart, periodEnd);
    }
}
