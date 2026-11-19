package com.nadia.plugin.threatintel;

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

class ThreatIntelPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void queriesNvdAndUrlhaus() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(ThreatIntelPlugin.PROP_NVD_API_KEY), anyString())).thenReturn("");
        when(ctx.getProperty(eq(ThreatIntelPlugin.PROP_URLHAUS_AUTH_KEY), anyString())).thenReturn("");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> nvdResp = mock(HttpResponse.class);
        when(nvdResp.statusCode()).thenReturn(200);
        when(nvdResp.body()).thenReturn("{\"totalResults\":1,\"vulnerabilities\":[{\"cve\":{\"id\":\"CVE-2024-3400\"}}]}");

        HttpResponse<String> urlhausResp = mock(HttpResponse.class);
        when(urlhausResp.statusCode()).thenReturn(200);
        when(urlhausResp.body()).thenReturn("{\"query_status\":\"ok\",\"host\":\"evil.example\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("urlhaus") ? urlhausResp : nvdResp;
        });

        ThreatIntelToolExtension tools = new ThreatIntelToolExtension(new ThreatIntelClient(httpClient, ctx));
        JsonNode cve = mapper.readTree(tools.nvdGetCve("CVE-2024-3400", null, 5));
        assertEquals("OK", cve.path("status").asText());
        assertEquals("CVE-2024-3400", cve.path("data").path("vulnerabilities").get(0).path("cve").path("id").asText());

        JsonNode uh = mapper.readTree(tools.urlhausLookup("evil.example"));
        assertEquals("OK", uh.path("status").asText());
        assertEquals("ok", uh.path("data").path("query_status").asText());
    }
}
