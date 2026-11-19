package com.nadia.plugin.macrotrade;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible macroeconomic and international trade
 * tools for FRED, IMF, and WTO.
 */
@Extension
public class MacroTradeToolExtension implements NadiaToolExtension {

    private final MacroTradeClient explicitClient;

    /** Default PF4J constructor. */
    public MacroTradeToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public MacroTradeToolExtension(MacroTradeClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private MacroTradeClient client() {
        return explicitClient != null ? explicitClient : MacroTradePlugin.getClient();
    }

    /**
     * Retrieves observations for a FRED economic data series.
     *
     * @param seriesId         FRED series identifier (e.g., {@code CPIAUCSL}, {@code DFF}, {@code UNRATE})
     * @param observationStart optional ISO start date (YYYY-MM-DD)
     * @param limit            max observations (default 24)
     * @return JSON string of FRED observations
     */
    @NadiaTool(
            name = "fredGetSeries",
            description = "Retrieves Federal Reserve Economic Data (FRED) time-series observations (CPI, GDP, Fed Funds rate, yields, unemployment).",
            properties = {MacroTradePlugin.PROP_FRED_API_KEY}
    )
    public String fredGetSeries(
            @NadiaToolParam(description = "FRED series ID (e.g., 'CPIAUCSL', 'DFF', 'UNRATE', 'DGS10', 'GDP')") String seriesId,
            @NadiaToolParam(description = "Optional start date in YYYY-MM-DD format", required = false) String observationStart,
            @NadiaToolParam(description = "Maximum observations to return (1-200, default 24)", required = false) Integer limit) {
        return client().getFredSeries(seriesId, observationStart, limit);
    }

    /**
     * Retrieves IMF global macroeconomic indicators by indicator code and ISO-3 country.
     *
     * @param indicator   IMF indicator code (e.g., {@code NGDP_RPCH}, {@code PCPIPCH}, {@code GGXWDG_NGDP})
     * @param countryIso3 optional ISO-3 country code (e.g., {@code USA}, {@code DEU})
     * @return JSON string of IMF indicator values
     */
    @NadiaTool(
            name = "imfGetIndicator",
            description = "Retrieves International Monetary Fund (IMF) macroeconomic indicators (GDP growth, inflation, public debt-to-GDP) by country.",
            properties = {MacroTradePlugin.PROP_IMF_API_KEY}
    )
    public String imfGetIndicator(
            @NadiaToolParam(description = "IMF indicator code (e.g., 'NGDP_RPCH' GDP growth, 'PCPIPCH' inflation, 'GGXWDG_NGDP' debt/GDP)") String indicator,
            @NadiaToolParam(description = "Optional ISO-3 country code (e.g., USA, DEU, CHN, JPN)", required = false) String countryIso3) {
        return client().getImfIndicator(indicator, countryIso3);
    }

    /**
     * Queries WTO Timeseries API for tariffs and merchandise trade flows.
     *
     * @param indicatorCode WTO indicator code (default {@code TP_A_0010})
     * @param reporterCode  optional WTO reporter code (e.g., {@code 840} for USA)
     * @param period        optional year or range (e.g., {@code 2022-2024})
     * @return JSON string of WTO trade/tariff data
     */
    @NadiaTool(
            name = "wtoGetTariffs",
            description = "Queries World Trade Organization (WTO) Timeseries data for applied MFN tariffs and bilateral trade flows.",
            properties = {MacroTradePlugin.PROP_WTO_API_KEY}
    )
    public String wtoGetTariffs(
            @NadiaToolParam(description = "WTO indicator code (default 'TP_A_0010' for MFN average tariff)", required = false) String indicatorCode,
            @NadiaToolParam(description = "Optional WTO reporter numeric code (e.g., '840' USA, '156' China, '276' Germany)", required = false) String reporterCode,
            @NadiaToolParam(description = "Optional year or range (e.g., '2022-2024')", required = false) String period) {
        return client().getWtoTradeData(indicatorCode, reporterCode, period);
    }
}
