package com.nadia.plugin.acled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcledPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsClearErrorWhenCredentialsMissing() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));

        AcledToolExtension tools = new AcledToolExtension(new AcledClient(mock(HttpClient.class), ctx));
        JsonNode resp = mapper.readTree(tools.acledSearchEvents("Sudan", null, null, null, null, null, null, 5));
        assertEquals("ERROR", resp.path("status").asText());
        assertTrue(resp.path("message").asText().contains(AcledPlugin.PROP_EMAIL));
    }

    @Test
    @SuppressWarnings("unchecked")
    void authenticatesWithOAuthAndSummarizesCountryEvents() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(AcledPlugin.PROP_EMAIL), anyString())).thenReturn("analyst@example.org");
        when(ctx.getProperty(eq(AcledPlugin.PROP_PASSWORD), anyString())).thenReturn("secret-pass");
        when(ctx.getProperty(eq(AcledPlugin.PROP_API_KEY), anyString())).thenReturn("");
        when(ctx.getProperty(eq(AcledPlugin.PROP_API_URL), anyString())).thenReturn(AcledClient.DEFAULT_API_URL);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResp = mock(HttpResponse.class);
        when(tokenResp.statusCode()).thenReturn(200);
        when(tokenResp.body()).thenReturn("{\"access_token\":\"oauth-tok-123\",\"expires_in\":3600}");

        HttpResponse<String> dataResp = mock(HttpResponse.class);
        when(dataResp.statusCode()).thenReturn(200);
        when(dataResp.body()).thenReturn("""
                {
                  "data": [
                    {"event_date":"2026-11-10","event_type":"Battles","actor1":"Force A","fatalities":"4","admin1":"Khartoum"},
                    {"event_date":"2026-11-11","event_type":"Protests","actor1":"Protesters","fatalities":"0","admin1":"Khartoum"}
                  ]
                }
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    HttpRequest req = inv.getArgument(0);
                    return req.uri().toString().contains("/oauth/token") ? tokenResp : dataResp;
                });

        AcledToolExtension tools = new AcledToolExtension(new AcledClient(httpClient, ctx));
        JsonNode summary = mapper.readTree(tools.acledGetCountrySummary("Sudan", "2026-11-01", "2026-11-19", 50));

        assertEquals("OK", summary.path("status").asText());
        assertEquals(2, summary.path("sampledEvents").asInt());
        assertEquals(4, summary.path("totalFatalities").asInt());
        assertEquals("Khartoum", summary.path("topAdminRegions").get(0).path("name").asText());
    }
}
