package com.nadia.plugin.sanctions;

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

class SanctionsPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void screensOfacSdnCsvFallbackWithoutApiKey() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("""
                1001,"SHADOW TANKER ALPHA","vessel","RUSSIA-EO14024","-0-","-0-","-0-","-0-","-0-","-0-","-0-","IMO 9999999; Linked to oil transport"
                1002,"ACME FRONT CORP","entity","IRAN","-0-","-0-","-0-","-0-","-0-","-0-","-0-","Procurement network"
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        SanctionsToolExtension tools = new SanctionsToolExtension(new SanctionsClient(httpClient, null));
        JsonNode vessel = mapper.readTree(tools.sanctionsCheckVesselOrAircraft("9999999", "vessel", 10));
        assertEquals("OK", vessel.path("status").asText());
        assertEquals(1, vessel.path("returnedCount").asInt());
        assertEquals("SHADOW TANKER ALPHA", vessel.path("results").get(0).path("name").asText());
    }
}
