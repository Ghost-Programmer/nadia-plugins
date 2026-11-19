package com.nadia.plugin.cisa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CisaKevPluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void searchesKevAndParsesAdvisoriesFeed() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> kevResp = mock(HttpResponse.class);
        when(kevResp.statusCode()).thenReturn(200);
        when(kevResp.body()).thenReturn("""
                {
                  "catalogVersion": "2026.11.19",
                  "dateReleased": "2026-11-19T00:00:00Z",
                  "count": 2,
                  "vulnerabilities": [
                    {
                      "cveID": "CVE-2024-3400",
                      "vendorProject": "Palo Alto Networks",
                      "product": "PAN-OS",
                      "vulnerabilityName": "PAN-OS Command Injection",
                      "dateAdded": "2024-04-12",
                      "shortDescription": "Command injection vulnerability in GlobalProtect.",
                      "dueDate": "2024-04-19",
                      "knownRansomwareCampaignUse": "Known"
                    },
                    {
                      "cveID": "CVE-2023-23397",
                      "vendorProject": "Microsoft",
                      "product": "Outlook",
                      "vulnerabilityName": "Microsoft Outlook Privilege Escalation",
                      "dateAdded": "2023-03-14",
                      "shortDescription": "Privilege escalation via crafted message.",
                      "dueDate": "2023-04-04",
                      "knownRansomwareCampaignUse": "Unknown"
                    }
                  ]
                }
                """);

        HttpResponse<String> rssResp = mock(HttpResponse.class);
        when(rssResp.statusCode()).thenReturn(200);
        when(rssResp.body()).thenReturn("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>CISA Releases ICS Advisory on Siemens PLC</title>
                    <link>https://www.cisa.gov/news-events/ics-advisories/icsa-26-001</link>
                    <pubDate>Thu, 19 Nov 2026 12:00:00 GMT</pubDate>
                    <description>&lt;p&gt;Critical industrial control system update.&lt;/p&gt;</description>
                  </item>
                </channel></rss>
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    HttpRequest req = inv.getArgument(0);
                    return req.uri().toString().endsWith(".xml") ? rssResp : kevResp;
                });

        CisaKevToolExtension tools = new CisaKevToolExtension(new CisaKevClient(httpClient, null));

        JsonNode kevSearch = mapper.readTree(tools.cisaSearchKev("PAN-OS", null, null, true, "2024-01-01", 10));
        assertEquals("OK", kevSearch.path("status").asText());
        assertEquals(1, kevSearch.path("returnedCount").asInt());
        assertEquals("CVE-2024-3400", kevSearch.path("vulnerabilities").get(0).path("cveID").asText());

        JsonNode found = mapper.readTree(tools.cisaGetKevByCve("CVE-2024-3400"));
        assertTrue(found.path("inKev").asBoolean());

        JsonNode missing = mapper.readTree(tools.cisaGetKevByCve("CVE-1999-0001"));
        assertFalse(missing.path("inKev").asBoolean());

        JsonNode summary = mapper.readTree(tools.cisaGetKevSummary());
        assertEquals(2, summary.path("totalCount").asInt());
        assertEquals(1, summary.path("knownRansomwareCount").asInt());

        JsonNode advisories = mapper.readTree(tools.cisaGetAdvisories("ics", "Siemens", 5));
        assertEquals("OK", advisories.path("status").asText());
        assertEquals(1, advisories.path("returnedCount").asInt());
        assertTrue(advisories.path("advisories").get(0).path("summary").asText().contains("industrial control system"));
    }
}
