package com.nadia.plugin.radhealth;

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

class RadiationHealthPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void queriesSafecastAndWhoDon() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> safeResp = mock(HttpResponse.class);
        when(safeResp.statusCode()).thenReturn(200);
        when(safeResp.body()).thenReturn("[{\"id\":1,\"value\":24.0,\"unit\":\"cpm\"}]");

        HttpResponse<String> whoResp = mock(HttpResponse.class);
        when(whoResp.statusCode()).thenReturn(200);
        when(whoResp.body()).thenReturn("{\"value\":[{\"DonId\":\"2026-DON500\",\"Title\":\"Marburg Virus Disease - Region\",\"Summary\":\"Outbreak investigation ongoing.\"}]}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("who.int") ? whoResp : safeResp;
        });

        RadiationHealthToolExtension tools = new RadiationHealthToolExtension(new RadiationHealthClient(httpClient, null));
        JsonNode rad = mapper.readTree(tools.radiationGetReadings(37.4, 141.0, 50.0, 10));
        assertEquals("OK", rad.path("status").asText());
        assertEquals(1, rad.path("count").asInt());

        JsonNode who = mapper.readTree(tools.whoGetOutbreakNews("Marburg", 5));
        assertEquals("OK", who.path("status").asText());
        assertEquals("2026-DON500", who.path("outbreaks").get(0).path("donId").asText());
    }
}
