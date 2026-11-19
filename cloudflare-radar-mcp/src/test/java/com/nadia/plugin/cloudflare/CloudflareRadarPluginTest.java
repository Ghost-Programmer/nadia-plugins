package com.nadia.plugin.cloudflare;

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

class CloudflareRadarPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsClearErrorWhenTokenMissing() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));

        CloudflareRadarToolExtension tools = new CloudflareRadarToolExtension(
                new CloudflareRadarClient(mock(HttpClient.class), ctx));
        JsonNode resp = mapper.readTree(tools.cloudflareRadarGetOutages("7d", "UA", null, 5));
        assertEquals("ERROR", resp.path("status").asText());
        assertTrue(resp.path("message").asText().contains(CloudflareRadarPlugin.PROP_API_TOKEN));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsBearerAuthAndParsesOutagesResponse() throws Exception {
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getProperty(eq(CloudflareRadarPlugin.PROP_API_TOKEN), anyString())).thenReturn("cf-test-token");
        when(ctx.getProperty(eq(CloudflareRadarPlugin.PROP_API_URL), anyString())).thenReturn(CloudflareRadarClient.DEFAULT_API_URL);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"success\":true,\"result\":{\"annotations\":[{\"id\":\"outage-1\",\"eventType\":\"OUTAGE\"}]}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        CloudflareRadarToolExtension tools = new CloudflareRadarToolExtension(new CloudflareRadarClient(httpClient, ctx));
        JsonNode out = mapper.readTree(tools.cloudflareRadarGetOutages("7d", "ua", "13335", 5));

        assertEquals("OK", out.path("status").asText());
        assertEquals("outage-1", out.path("result").path("annotations").get(0).path("id").asText());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();
        assertEquals("Bearer cf-test-token", sent.headers().firstValue("Authorization").orElse(""));
        assertTrue(sent.uri().toString().contains("/annotations/outages"));
        assertTrue(sent.uri().toString().contains("location=UA"));
        assertTrue(sent.uri().toString().contains("asn=13335"));
    }
}
