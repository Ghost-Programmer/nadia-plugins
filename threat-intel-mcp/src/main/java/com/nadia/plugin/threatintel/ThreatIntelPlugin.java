package com.nadia.plugin.threatintel;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for NIST NVD CVE, AbuseIPDB, AlienVault OTX &amp; URLhaus
 * Threat Intelligence ({@code threat-intel-mcp}).
 */
public class ThreatIntelPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "threat-intel-mcp";
    public static final String PROP_NVD_API_KEY = "nadia.prop.nvd.apiKey";
    public static final String PROP_ABUSEIPDB_API_KEY = "nadia.prop.abuseipdb.apiKey";
    public static final String PROP_OTX_API_KEY = "nadia.prop.otx.apiKey";
    public static final String PROP_URLHAUS_AUTH_KEY = "nadia.prop.urlhaus.authKey";

    private static volatile HttpClient sharedHttpClient;
    private static volatile ThreatIntelClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public ThreatIntelPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_NVD_API_KEY, "Optional NIST NVD API key for higher rate limits on CVE 2.0 lookups (works keyless with lower rate limits).");
        props.put(PROP_ABUSEIPDB_API_KEY, "AbuseIPDB API v2 key for checking IPv4/IPv6 abuse confidence scores and threat reports.");
        props.put(PROP_OTX_API_KEY, "AlienVault OTX API key for querying threat pulses and IOC intelligence (IP, domain, hash).");
        props.put(PROP_URLHAUS_AUTH_KEY, "Optional abuse.ch URLhaus Auth-Key for querying malicious URLs, hosts, and malware payloads.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Threat Intelligence MCP");
        synchronized (ThreatIntelPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new ThreatIntelClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (ThreatIntelPlugin.class) {
            sharedClient = null;
            if (sharedHttpClient != null) {
                try {
                    sharedHttpClient.close();
                } catch (Exception ignored) {
                }
                sharedHttpClient = null;
            }
        }
    }

    /**
     * Returns the shared {@link ThreatIntelClient}.
     *
     * @return active client
     */
    public static ThreatIntelClient getClient() {
        ThreatIntelClient c = sharedClient;
        if (c == null) {
            synchronized (ThreatIntelPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new ThreatIntelClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
