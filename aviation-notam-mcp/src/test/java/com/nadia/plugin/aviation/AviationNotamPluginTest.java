package com.nadia.plugin.aviation;

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

class AviationNotamPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void parsesFaaDelaysXmlAndMilitaryAdsb() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> xmlResp = mock(HttpResponse.class);
        when(xmlResp.statusCode()).thenReturn(200);
        when(xmlResp.body()).thenReturn("""
                <AIRPORT_STATUS_INFORMATION>
                  <Update_Time>Thu Nov 19 17:00:00 2026 GMT</Update_Time>
                  <Delay_type><Ground_Delay_List>
                    <Ground_Delay><ARPT>EWR</ARPT><Reason>wind</Reason><Avg>45 minutes</Avg></Ground_Delay>
                  </Ground_Delay_List></Delay_type>
                </AIRPORT_STATUS_INFORMATION>
                """);

        HttpResponse<String> milResp = mock(HttpResponse.class);
        when(milResp.statusCode()).thenReturn(200);
        when(milResp.body()).thenReturn("{\"ac\":[{\"hex\":\"ae1234\",\"flight\":\"RCH101 \",\"t\":\"C17\",\"r\":\"01-0190\",\"lat\":48.5,\"lon\":11.2,\"alt_baro\":32000,\"gs\":450}]}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            return req.uri().toString().contains("adsb") ? milResp : xmlResp;
        });

        AviationNotamToolExtension tools = new AviationNotamToolExtension(new AviationNotamClient(httpClient, null));
        JsonNode delays = mapper.readTree(tools.faaGetAirportDelays("EWR"));
        assertEquals("OK", delays.path("status").asText());
        assertEquals(1, delays.path("count").asInt());
        assertEquals("wind", delays.path("delays").get(0).path("reason").asText());

        JsonNode mil = mapper.readTree(tools.adsbGetMilitaryFlights("C17", 10));
        assertEquals("OK", mil.path("status").asText());
        assertEquals("RCH101", mil.path("aircraft").get(0).path("callsign").asText());
    }
}
