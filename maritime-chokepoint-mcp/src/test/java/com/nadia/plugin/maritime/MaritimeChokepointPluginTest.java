package com.nadia.plugin.maritime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaritimeChokepointPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void listsChokepointsAndFiltersNgaWarnings() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("""
                {
                  "broadcast-warn": [
                    {"msgYear":2026,"msgNumber":101,"navArea":"IX","subregion":"Red Sea","issueDate":"2026-11-18","text":"BAB EL MANDEB MARITIME SECURITY WARNING"},
                    {"msgYear":2026,"msgNumber":102,"navArea":"IV","subregion":"Gulf","issueDate":"2026-11-18","text":"UNLIT BUOY"}
                  ]
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        MaritimeChokepointToolExtension tools = new MaritimeChokepointToolExtension(new MaritimeChokepointClient(httpClient, null));
        JsonNode cps = mapper.readTree(tools.maritimeListChokepoints(null));
        assertEquals("OK", cps.path("status").asText());
        assertTrue(cps.path("count").asInt() >= 8);

        JsonNode warns = mapper.readTree(tools.maritimeGetDisruptions("bab-el-mandeb", null, 10));
        assertEquals("OK", warns.path("status").asText());
        assertEquals(1, warns.path("count").asInt());
    }
}
