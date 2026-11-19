package com.nadia.plugin.radhealth;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for Safecast Radiation Monitoring &amp; WHO Disease Outbreak
 * News ({@code radiation-health-mcp}).
 */
public class RadiationHealthPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "radiation-health-mcp";
    public static final String PROP_SAFECAST_API_URL = "nadia.prop.safecast.apiUrl";
    public static final String PROP_WHO_DON_API_URL = "nadia.prop.who.donApiUrl";

    private static volatile HttpClient sharedHttpClient;
    private static volatile RadiationHealthClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public RadiationHealthPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_SAFECAST_API_URL, "Optional override URL for the Safecast environmental radiation measurements API (keyless by default).");
        props.put(PROP_WHO_DON_API_URL, "Optional override URL for the WHO Disease Outbreak News (DON) JSON API (keyless by default).");
        context.registerPropertiesWithDescriptions(props, "Plugin: Radiation & Health Alerts MCP");
        synchronized (RadiationHealthPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new RadiationHealthClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (RadiationHealthPlugin.class) {
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
     * Returns the shared {@link RadiationHealthClient}.
     *
     * @return active client
     */
    public static RadiationHealthClient getClient() {
        RadiationHealthClient c = sharedClient;
        if (c == null) {
            synchronized (RadiationHealthPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new RadiationHealthClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
