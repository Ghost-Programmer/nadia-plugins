package com.nadia.plugin.ucdp;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for UCDP Conflict Events &amp; UNHCR Refugee Displacement
 * ({@code ucdp-displacement-mcp}).
 */
public class UcdpDisplacementPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "ucdp-displacement-mcp";
    public static final String PROP_UCDP_TOKEN = "nadia.prop.ucdp.accessToken";
    public static final String PROP_UCDP_API_URL = "nadia.prop.ucdp.apiUrl";
    public static final String PROP_UNHCR_API_URL = "nadia.prop.unhcr.apiUrl";

    private static volatile HttpClient sharedHttpClient;
    private static volatile UcdpDisplacementClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public UcdpDisplacementPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_UCDP_TOKEN, "Optional UCDP API access token (x-ucdp-access-token) for Uppsala Conflict Data Program GED queries.");
        props.put(PROP_UCDP_API_URL, "Optional override URL for the UCDP GED events API.");
        props.put(PROP_UNHCR_API_URL, "Optional override URL for the UNHCR Refugee Population Statistics API (keyless by default).");
        context.registerPropertiesWithDescriptions(props, "Plugin: UCDP & UNHCR Displacement MCP");
        synchronized (UcdpDisplacementPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new UcdpDisplacementClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (UcdpDisplacementPlugin.class) {
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
     * Returns the shared {@link UcdpDisplacementClient}.
     *
     * @return active client
     */
    public static UcdpDisplacementClient getClient() {
        UcdpDisplacementClient c = sharedClient;
        if (c == null) {
            synchronized (UcdpDisplacementPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new UcdpDisplacementClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
