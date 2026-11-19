package com.nadia.plugin.polymarket;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for Polymarket &amp; Metaculus prediction markets
 * ({@code polymarket-mcp}).
 */
public class PolymarketPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "polymarket-mcp";
    public static final String PROP_POLYMARKET_API_URL = "nadia.prop.polymarket.apiUrl";
    public static final String PROP_METACULUS_API_URL = "nadia.prop.metaculus.apiUrl";

    private static volatile HttpClient sharedHttpClient;
    private static volatile PolymarketClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public PolymarketPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_POLYMARKET_API_URL, "Optional override URL for the Polymarket Gamma Markets API (keyless by default).");
        props.put(PROP_METACULUS_API_URL, "Optional override URL for the Metaculus forecasting API (keyless by default).");
        context.registerPropertiesWithDescriptions(props, "Plugin: Polymarket & Metaculus MCP");
        synchronized (PolymarketPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new PolymarketClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (PolymarketPlugin.class) {
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
     * Returns the shared {@link PolymarketClient}.
     *
     * @return active client
     */
    public static PolymarketClient getClient() {
        PolymarketClient c = sharedClient;
        if (c == null) {
            synchronized (PolymarketPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new PolymarketClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
