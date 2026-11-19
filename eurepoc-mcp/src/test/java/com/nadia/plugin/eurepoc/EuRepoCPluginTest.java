package com.nadia.plugin.eurepoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EuRepoCPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void searchesAndSummarizesLocalCsvDataset(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("eurepoc.csv");
        Files.writeString(csv, """
                ID,name,start_date,incident_type,receiver_country,initiator_country,description
                1001,"Power Grid Attack",2024-03-10,Disruption,Ukraine,Russia,"Targeting electrical substation, winter outage"
                1002,"Hospital Ransomware",2024-05-15,Ransomware,Germany,Unknown,"Encrypted hospital records"
                1003,"Ministry Espionage",2023-11-02,"Data theft",France,China,"Long-term diplomatic exfiltration"
                """, StandardCharsets.UTF_8);

        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(EuRepoCPlugin.PROP_API_URL), anyString())).thenReturn("");
        when(ctx.getProperty(eq(EuRepoCPlugin.PROP_DATASET_URL), anyString())).thenReturn(csv.toString());

        EuRepoCClient client = new EuRepoCClient(mock(HttpClient.class), ctx);
        EuRepoCToolExtension tools = new EuRepoCToolExtension(client);

        JsonNode search = mapper.readTree(tools.eurepocSearchIncidents("substation", "Ukraine", "Russia", null, "2024-01-01", "2024-12-31", 10));
        assertEquals("OK", search.path("status").asText());
        assertEquals(1, search.path("returnedCount").asInt());
        assertEquals("1001", search.path("incidents").get(0).path("id").asText());

        JsonNode detail = mapper.readTree(tools.eurepocGetIncident("1002"));
        assertEquals("OK", detail.path("status").asText());
        assertEquals("Hospital Ransomware", detail.path("incident").path("name").asText());

        JsonNode summary = mapper.readTree(tools.eurepocGetSummary());
        assertEquals("OK", summary.path("status").asText());
        assertEquals(3, summary.path("totalIncidents").asInt());
        assertTrue(summary.path("topReceiverCountries").size() >= 3);
    }
}
