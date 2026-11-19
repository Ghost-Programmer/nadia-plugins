package com.nadia.plugin.maritime;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for Global Maritime Chokepoint Risk &amp; NGA Maritime
 * Safety Information (MSI) Broadcast Warnings ({@code maritime-chokepoint-mcp}).
 */
public class MaritimeChokepointPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "maritime-chokepoint-mcp";
    public static final String PROP_NGA_MSI_URL = "nadia.prop.maritime.ngaMsiUrl";
    public static final String PROP_AISSTREAM_API_KEY = "nadia.prop.aisstream.apiKey";

    private static volatile HttpClient sharedHttpClient;
    private static volatile MaritimeChokepointClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public MaritimeChokepointPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_NGA_MSI_URL, "Optional override URL for the NGA Maritime Safety Information broadcast warnings API (keyless by default).");
        props.put(PROP_AISSTREAM_API_KEY, "Optional AISStream.io API key for live WebSocket AIS vessel tracking integrations.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Maritime Chokepoint MCP");
        synchronized (MaritimeChokepointPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new MaritimeChokepointClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (MaritimeChokepointPlugin.class) {
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
     * Returns the shared {@link MaritimeChokepointClient}.
     *
     * @return active client
     */
    public static MaritimeChokepointClient getClient() {
        MaritimeChokepointClient c = sharedClient;
        if (c == null) {
            synchronized (MaritimeChokepointPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new MaritimeChokepointClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
