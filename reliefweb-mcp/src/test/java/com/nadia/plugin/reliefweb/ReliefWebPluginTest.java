package com.nadia.plugin.reliefweb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReliefWebPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsErrorWhenAppnameUnset() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));

        ReliefWebToolExtension tools = new ReliefWebToolExtension(new ReliefWebClient(mock(HttpClient.class), ctx));
        JsonNode resp = mapper.readTree(tools.reliefwebSearchDisasters("flood", null, "current", 5));
        assertEquals("ERROR", resp.path("status").asText());
        assertTrue(resp.path("message").asText().contains(ReliefWebPlugin.PROP_APPNAME));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsToReliefWebV2WithAppname() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(ReliefWebPlugin.PROP_APPNAME), anyString())).thenReturn("nadia-ai-desktop");
        when(ctx.getProperty(eq(ReliefWebPlugin.PROP_API_URL), anyString())).thenReturn(ReliefWebClient.DEFAULT_API_URL);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"totalCount\":42,\"count\":1,\"data\":[{\"id\":\"51234\",\"fields\":{\"title\":\"Sudan SitRep\"}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        ReliefWebToolExtension tools = new ReliefWebToolExtension(new ReliefWebClient(httpClient, ctx));
        JsonNode out = mapper.readTree(tools.reliefwebSearchReports("displacement", "Sudan", null, 5));

        assertEquals("OK", out.path("status").asText());
        assertEquals(42, out.path("totalCount").asInt());
        assertEquals("Sudan SitRep", out.path("data").get(0).path("fields").path("title").asText());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertEquals("POST", captor.getValue().method());
        assertTrue(captor.getValue().uri().toString().contains("/v2/reports?appname=nadia-ai-desktop"));
    }
}
