package com.nadia.plugin.firms;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for NASA FIRMS Active Fires &amp; NASA EONET Natural Events
 * ({@code nasa-firms-mcp}).
 */
public class NasaFirmsPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "nasa-firms-mcp";
    public static final String PROP_FIRMS_API_KEY = "nadia.prop.nasa.firms.apiKey";
    public static final String PROP_EONET_API_URL = "nadia.prop.nasa.eonet.apiUrl";

    private static volatile HttpClient sharedHttpClient;
    private static volatile NasaFirmsClient sharedClient;

    /**
     * Constructs the {@code NasaFirmsPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public NasaFirmsPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_FIRMS_API_KEY, "Free NASA FIRMS MAP_KEY for satellite active fire/thermal anomaly CSV queries (https://firms.modaps.eosdis.nasa.gov/api/area/).");
        props.put(PROP_EONET_API_URL, "Optional override base URL for the NASA EONET v3 natural events API (keyless by default).");
        context.registerPropertiesWithDescriptions(props, "Plugin: NASA FIRMS & EONET MCP");
        synchronized (NasaFirmsPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new NasaFirmsClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (NasaFirmsPlugin.class) {
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
     * Returns the active shared {@link NasaFirmsClient}, lazily initializing one if needed.
     *
     * @return active {@link NasaFirmsClient}
     */
    public static NasaFirmsClient getClient() {
        NasaFirmsClient c = sharedClient;
        if (c == null) {
            synchronized (NasaFirmsPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new NasaFirmsClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
