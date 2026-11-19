package com.nadia.plugin.energy;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for U.S. EIA, GIE AGSI+ EU Gas Storage &amp; ENTSO-E
 * European Power Grid MCP tools ({@code energy-grid-mcp}).
 */
public class EnergyGridPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "energy-grid-mcp";
    public static final String PROP_EIA_API_KEY = "nadia.prop.eia.apiKey";
    public static final String PROP_GIE_API_KEY = "nadia.prop.gie.apiKey";
    public static final String PROP_ENTSOE_TOKEN = "nadia.prop.entsoe.token";

    private static volatile HttpClient sharedHttpClient;
    private static volatile EnergyGridClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public EnergyGridPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_EIA_API_KEY, "Free U.S. Energy Information Administration (EIA) v2 API key for oil, natural gas, and electricity series.");
        props.put(PROP_GIE_API_KEY, "Free GIE AGSI+ API key (x-key header) for European Union natural gas storage fill levels.");
        props.put(PROP_ENTSOE_TOKEN, "Free ENTSO-E Transparency Platform security token for European electrical grid load and generation telemetry.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Energy & Power Grid MCP");
        synchronized (EnergyGridPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new EnergyGridClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (EnergyGridPlugin.class) {
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
     * Returns the shared {@link EnergyGridClient}.
     *
     * @return active client
     */
    public static EnergyGridClient getClient() {
        EnergyGridClient c = sharedClient;
        if (c == null) {
            synchronized (EnergyGridPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new EnergyGridClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
