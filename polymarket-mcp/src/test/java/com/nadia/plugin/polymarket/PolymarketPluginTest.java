package com.nadia.plugin.polymarket;

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

class PolymarketPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void filtersPolymarketAndMetaculusResults() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> polyResp = mock(HttpResponse.class);
        when(polyResp.statusCode()).thenReturn(200);
        when(polyResp.body()).thenReturn("""
                [
                  {"id":"1","slug":"fed-cut","question":"Will the Fed cut rates in December?","outcomes":"[\\"Yes\\",\\"No\\"]","outcomePrices":"[\\"0.65\\",\\"0.35\\"]","volume24hr":250000},
                  {"id":"2","slug":"sports","question":"Sports game","outcomes":"[\\"A\\",\\"B\\"]","outcomePrices":"[\\"0.5\\",\\"0.5\\"]","volume24hr":1000}
                ]
                """);
        HttpResponse<String> metaResp = mock(HttpResponse.class);
        when(metaResp.statusCode()).thenReturn(200);
        when(metaResp.body()).thenReturn("{\"results\":[{\"id\":42,\"title\":\"Global AI treaty by 2028?\"}]}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("metaculus") ? metaResp : polyResp;
        });

        PolymarketToolExtension tools = new PolymarketToolExtension(new PolymarketClient(httpClient, null));
        JsonNode pm = mapper.readTree(tools.polymarketSearchMarkets("Fed", true, 5));
        assertEquals("OK", pm.path("status").asText());
        assertEquals(1, pm.path("count").asInt());

        JsonNode mc = mapper.readTree(tools.metaculusSearchQuestions("AI", "open", 5));
        assertEquals("OK", mc.path("status").asText());
        assertEquals(1, mc.path("count").asInt());
    }
}
