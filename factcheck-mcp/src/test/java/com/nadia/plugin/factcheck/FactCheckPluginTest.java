package com.nadia.plugin.factcheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactCheckPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void searchesClaimsWithApiKey() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(FactCheckPlugin.PROP_API_KEY), anyString())).thenReturn("fc-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"claims\":[{\"text\":\"Viral claim\",\"claimReview\":[{\"textualRating\":\"False\"}]}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        FactCheckToolExtension tools = new FactCheckToolExtension(new FactCheckClient(httpClient, ctx));
        JsonNode out = mapper.readTree(tools.factCheckSearchClaims("viral claim", "en", 30, 5));
        assertEquals("OK", out.path("status").asText());
        assertEquals(1, out.path("count").asInt());
        assertEquals("False", out.path("claims").get(0).path("claimReview").get(0).path("textualRating").asText());
    }
}
