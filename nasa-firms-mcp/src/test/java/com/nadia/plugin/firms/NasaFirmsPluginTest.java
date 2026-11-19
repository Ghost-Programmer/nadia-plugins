package com.nadia.plugin.firms;

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

class NasaFirmsPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void parsesFirmsCsvAndEonetJson() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(NasaFirmsPlugin.PROP_FIRMS_API_KEY), anyString())).thenReturn("test-map-key");
        when(ctx.getProperty(eq(NasaFirmsPlugin.PROP_EONET_API_URL), anyString())).thenReturn(NasaFirmsClient.DEFAULT_EONET_URL);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> firmsResp = mock(HttpResponse.class);
        when(firmsResp.statusCode()).thenReturn(200);
        when(firmsResp.body()).thenReturn("latitude,longitude,bright_ti4,acq_date,confidence\n34.1,-118.2,340.5,2026-11-19,h\n");

        HttpResponse<String> eonetResp = mock(HttpResponse.class);
        when(eonetResp.statusCode()).thenReturn(200);
        when(eonetResp.body()).thenReturn("{\"events\":[{\"id\":\"EONET_1\",\"title\":\"Mount Etna Eruption\"}]}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("eonet") ? eonetResp : firmsResp;
        });

        NasaFirmsToolExtension tools = new NasaFirmsToolExtension(new NasaFirmsClient(httpClient, ctx));

        JsonNode fires = mapper.readTree(tools.firmsGetActiveFires("-120,32,-115,36", "VIIRS_SNPP_NRT", 1, 10));
        assertEquals("OK", fires.path("status").asText());
        assertEquals(1, fires.path("returnedCount").asInt());
        assertEquals("34.1", fires.path("fires").get(0).path("latitude").asText());

        JsonNode events = mapper.readTree(tools.eonetSearchNaturalEvents("volcanoes", "open", 30, 5));
        assertEquals("OK", events.path("status").asText());
        assertEquals("Mount Etna Eruption", events.path("events").get(0).path("title").asText());
    }
}
