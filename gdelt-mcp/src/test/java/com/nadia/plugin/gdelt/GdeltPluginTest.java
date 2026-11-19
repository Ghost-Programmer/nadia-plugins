package com.nadia.plugin.gdelt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void queriesDocAndGeoEndpoints() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> docResp = mock(HttpResponse.class);
        when(docResp.statusCode()).thenReturn(200);
        when(docResp.body()).thenReturn("{\"articles\":[{\"title\":\"Grid Cyber Alert\",\"url\":\"https://example.org/a1\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(docResp);

        GdeltToolExtension tools = new GdeltToolExtension(new GdeltClient(httpClient, null));
        JsonNode out = mapper.readTree(tools.gdeltSearchArticles("power grid", "24h", "US", "english", "CYBER_ATTACK", "DateDesc", 10));

        assertEquals("OK", out.path("status").asText());
        assertEquals("Grid Cyber Alert", out.path("articles").get(0).path("title").asText());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.contains("mode=artlist"));
        assertTrue(uri.contains("maxrecords=10"));
        assertTrue(uri.contains("CYBER_ATTACK"));
    }
}
