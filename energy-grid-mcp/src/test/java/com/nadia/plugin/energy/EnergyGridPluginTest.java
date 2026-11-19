package com.nadia.plugin.energy;

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

class EnergyGridPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void queriesEiaAndGieAgsi() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(EnergyGridPlugin.PROP_EIA_API_KEY), anyString())).thenReturn("eia-key");
        when(ctx.getProperty(eq(EnergyGridPlugin.PROP_GIE_API_KEY), anyString())).thenReturn("gie-key");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"data\":[{\"gasDayStart\":\"2026-11-18\",\"full\":\"88.4\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        EnergyGridToolExtension tools = new EnergyGridToolExtension(new EnergyGridClient(httpClient, ctx));
        JsonNode gie = mapper.readTree(tools.gieGetEuGasStorage("EU", 5));
        assertEquals("OK", gie.path("status").asText());
        assertEquals("88.4", gie.path("data").path("data").get(0).path("full").asText());
    }
}
