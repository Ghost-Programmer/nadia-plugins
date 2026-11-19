package com.nadia.plugin.macrotrade;

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

class MacroTradePluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void queriesFredAndImf() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(MacroTradePlugin.PROP_FRED_API_KEY), anyString())).thenReturn("fred-key");
        when(ctx.getProperty(eq(MacroTradePlugin.PROP_IMF_API_KEY), anyString())).thenReturn("");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"observations\":[{\"date\":\"2026-10-01\",\"value\":\"3.9\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        MacroTradeToolExtension tools = new MacroTradeToolExtension(new MacroTradeClient(httpClient, ctx));
        JsonNode fred = mapper.readTree(tools.fredGetSeries("UNRATE", "2026-01-01", 10));
        assertEquals("OK", fred.path("status").asText());
        assertEquals("3.9", fred.path("data").path("observations").get(0).path("value").asText());

        JsonNode imf = mapper.readTree(tools.imfGetIndicator("NGDP_RPCH", "USA"));
        assertEquals("OK", imf.path("status").asText());
    }
}
