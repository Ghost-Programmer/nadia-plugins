package com.nadia.plugin.ucdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UcdpDisplacementPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void queriesUcdpAndUnhcr() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> ucdpResp = mock(HttpResponse.class);
        when(ucdpResp.statusCode()).thenReturn(200);
        when(ucdpResp.body()).thenReturn("{\"TotalCount\":1,\"Result\":[{\"id\":99,\"country\":\"Sudan\",\"best\":12}]}");

        HttpResponse<String> unhcrResp = mock(HttpResponse.class);
        when(unhcrResp.statusCode()).thenReturn(200);
        when(unhcrResp.body()).thenReturn("{\"items\":[{\"year\":2024,\"coo_iso\":\"SDN\",\"refugees\":1500000}]}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("unhcr") ? unhcrResp : ucdpResp;
        });

        UcdpDisplacementToolExtension tools = new UcdpDisplacementToolExtension(new UcdpDisplacementClient(httpClient, null));
        JsonNode ged = mapper.readTree(tools.ucdpSearchGedEvents("Sudan", "2024-01-01", "2024-12-31", 10));
        assertEquals("OK", ged.path("status").asText());
        assertEquals(1, ged.path("totalCount").asInt());

        JsonNode unhcr = mapper.readTree(tools.unhcrGetDisplacementStats("SDN", null, 2024, 10));
        assertEquals("OK", unhcr.path("status").asText());
        assertEquals(1500000, unhcr.path("items").get(0).path("refugees").asInt());
    }
}
