package com.nadia.plugin.eurepoc;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

/**
 * PF4J {@link NadiaToolExtension} exposing MCP-compatible tools for querying the
 * European Repository of Cyber Incidents (EuRepoC).
 */
@Extension
public class EuRepoCToolExtension implements NadiaToolExtension {

    private final EuRepoCClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link EuRepoCRuntime#getInstance()}.
     */
    public EuRepoCToolExtension() {
        this(null);
    }

    /**
     * Test constructor accepting an explicit {@link EuRepoCClient}.
     *
     * @param explicitClient explicit client for unit testing
     */
    public EuRepoCToolExtension(EuRepoCClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private EuRepoCClient client() {
        return explicitClient != null ? explicitClient : EuRepoCRuntime.getInstance().getClient();
    }

    /**
     * Searches EuRepoC cyber incidents using optional filters.
     *
     * @param query            optional keyword search across incident fields
     * @param receiverCountry  optional target/receiver country name
     * @param initiatorCountry optional initiator/origin country name
     * @param incidentType     optional incident category/type
     * @param startDate        optional start date lower bound (YYYY-MM-DD)
     * @param endDate          optional start date upper bound (YYYY-MM-DD)
     * @param limit            optional max results (default 20, max 100)
     * @return JSON string of matching cyber incidents
     */
    @NadiaTool(
            name = "eurepocSearchIncidents",
            description = "Searches the European Repository of Cyber Incidents (EuRepoC) dataset by keyword, target/receiver country, initiator country, incident type, or date range.",
            properties = {EuRepoCPlugin.PROP_DATASET_URL, EuRepoCPlugin.PROP_API_URL, EuRepoCPlugin.PROP_API_KEY}
    )
    public String eurepocSearchIncidents(
            @NadiaToolParam(description = "Optional keyword search across incident title, description, actor, and metadata", required = false) String query,
            @NadiaToolParam(description = "Optional target/receiver country filter (e.g., Germany, Ukraine, United States)", required = false) String receiverCountry,
            @NadiaToolParam(description = "Optional initiator/origin country filter (e.g., Russia, China, Iran)", required = false) String initiatorCountry,
            @NadiaToolParam(description = "Optional incident type filter (e.g., Disruption, Data theft, Ransomware)", required = false) String incidentType,
            @NadiaToolParam(description = "Optional start date lower bound in YYYY-MM-DD format", required = false) String startDate,
            @NadiaToolParam(description = "Optional start date upper bound in YYYY-MM-DD format", required = false) String endDate,
            @NadiaToolParam(description = "Maximum number of incidents to return (1-100, default 20)", required = false) Integer limit) {
        return client().searchIncidents(query, receiverCountry, initiatorCountry, incidentType, startDate, endDate, limit);
    }

    /**
     * Retrieves full details for a specific EuRepoC incident ID.
     *
     * @param incidentId EuRepoC incident identifier
     * @return JSON string with full incident fields
     */
    @NadiaTool(
            name = "eurepocGetIncident",
            description = "Retrieves full details of a specific cyber incident from EuRepoC by its incident ID.",
            properties = {EuRepoCPlugin.PROP_DATASET_URL, EuRepoCPlugin.PROP_API_URL, EuRepoCPlugin.PROP_API_KEY}
    )
    public String eurepocGetIncident(
            @NadiaToolParam(description = "The EuRepoC incident ID to retrieve") String incidentId) {
        return client().getIncidentById(incidentId);
    }

    /**
     * Returns aggregate statistics over the EuRepoC cyber incident repository.
     *
     * @return JSON summary with total count, top target countries, top initiators, and top incident types
     */
    @NadiaTool(
            name = "eurepocGetSummary",
            description = "Returns high-level statistics from EuRepoC including total incidents, top targeted countries, top initiator countries, and top incident types.",
            properties = {EuRepoCPlugin.PROP_DATASET_URL}
    )
    public String eurepocGetSummary() {
        return client().getDatasetSummary();
    }
}
