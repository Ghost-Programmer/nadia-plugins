package com.nadia.plugin.aviation;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for FAA Airport Delays,
 * NOTAM search, and live military ADS-B flight tracking.
 */
@Extension
public class AviationNotamToolExtension implements NadiaToolExtension {

    private final AviationNotamClient explicitClient;

    /** Default PF4J constructor. */
    public AviationNotamToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit client.
     *
     * @param explicitClient explicit client for unit testing
     */
    public AviationNotamToolExtension(AviationNotamClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private AviationNotamClient client() {
        return explicitClient != null ? explicitClient : AviationNotamPlugin.getClient();
    }

    /**
     * Retrieves live FAA NAS airport ground delays, ground stops, and closures.
     *
     * @param airportCode optional 3-letter airport code filter (e.g., JFK, ORD)
     * @return JSON string of active FAA delays
     */
    @NadiaTool(
            name = "faaGetAirportDelays",
            description = "Retrieves real-time FAA National Airspace System (NAS) airport ground stops, arrival/departure delays, and closures (no API key required)."
    )
    public String faaGetAirportDelays(
            @NadiaToolParam(description = "Optional 3-letter FAA/IATA airport code (e.g., JFK, EWR, ORD, LAX)", required = false) String airportCode) {
        return client().getFaaAirportDelays(airportCode);
    }

    /**
     * Searches official FAA NOTAMs for an ICAO airport or FIR.
     *
     * @param locationIcao ICAO airport or FIR code
     * @param limit        max NOTAMs (default 20)
     * @return JSON string of active NOTAMs
     */
    @NadiaTool(
            name = "notamSearchByAirportOrFir",
            description = "Searches official FAA Notices to Air Missions (NOTAMs) for an ICAO airport or Flight Information Region.",
            properties = {AviationNotamPlugin.PROP_NOTAM_CLIENT_ID, AviationNotamPlugin.PROP_NOTAM_CLIENT_SECRET}
    )
    public String notamSearchByAirportOrFir(
            @NadiaToolParam(description = "ICAO airport or FIR code (e.g., KJFK, KLAX, EGLL)") String locationIcao,
            @NadiaToolParam(description = "Maximum NOTAMs to return (1-50, default 20)", required = false) Integer limit) {
        return client().searchNotams(locationIcao, limit);
    }

    /**
     * Retrieves live military and state aircraft ADS-B tracks.
     *
     * @param callsignOrType optional filter on callsign, aircraft type, or registration
     * @param limit          max aircraft (default 30)
     * @return JSON string of live military ADS-B aircraft
     */
    @NadiaTool(
            name = "adsbGetMilitaryFlights",
            description = "Retrieves live military and state aircraft ADS-B transponder positions globally (no API key required).",
            properties = {AviationNotamPlugin.PROP_ADSB_MIL_URL}
    )
    public String adsbGetMilitaryFlights(
            @NadiaToolParam(description = "Optional filter matching callsign, aircraft type (e.g., C17, KC135, P8), or registration", required = false) String callsignOrType,
            @NadiaToolParam(description = "Maximum aircraft to return (1-100, default 30)", required = false) Integer limit) {
        return client().getMilitaryFlights(callsignOrType, limit);
    }
}
