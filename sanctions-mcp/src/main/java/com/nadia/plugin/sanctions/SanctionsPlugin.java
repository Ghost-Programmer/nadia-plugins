package com.nadia.plugin.sanctions;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for OpenSanctions &amp; U.S. OFAC SDN sanctions screening
 * ({@code sanctions-mcp}).
 */
public class SanctionsPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "sanctions-mcp";
    public static final String PROP_OPENSANCTIONS_API_KEY = "nadia.prop.opensanctions.apiKey";
    public static final String PROP_OPENSANCTIONS_API_URL = "nadia.prop.opensanctions.apiUrl";
    public static final String PROP_OFAC_SDN_URL = "nadia.prop.ofac.sdnUrl";

    private static volatile HttpClient sharedHttpClient;
    private static volatile SanctionsClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public SanctionsPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_OPENSANCTIONS_API_KEY, "Optional OpenSanctions API key for global multi-jurisdiction sanctions & PEP screening (falls back to keyless U.S. OFAC SDN list if blank).");
        props.put(PROP_OPENSANCTIONS_API_URL, "Optional override base URL for the OpenSanctions API.");
        props.put(PROP_OFAC_SDN_URL, "Optional override URL for the U.S. Treasury OFAC SDN CSV list.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Sanctions Screening MCP");
        synchronized (SanctionsPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new SanctionsClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (SanctionsPlugin.class) {
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
     * Returns the shared {@link SanctionsClient}.
     *
     * @return active client
     */
    public static SanctionsClient getClient() {
        SanctionsClient c = sharedClient;
        if (c == null) {
            synchronized (SanctionsPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new SanctionsClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
