package com.nadia.plugin.aviation;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for FAA Airport Delays, NOTAM Search &amp; Military ADS-B
 * Flight Tracking ({@code aviation-notam-mcp}).
 */
public class AviationNotamPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "aviation-notam-mcp";
    public static final String PROP_NOTAM_CLIENT_ID = "nadia.prop.faa.notam.clientId";
    public static final String PROP_NOTAM_CLIENT_SECRET = "nadia.prop.faa.notam.clientSecret";
    public static final String PROP_ADSB_MIL_URL = "nadia.prop.adsb.militaryUrl";

    private static volatile HttpClient sharedHttpClient;
    private static volatile AviationNotamClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public AviationNotamPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_NOTAM_CLIENT_ID, "FAA API Portal client_id for official NOTAM search queries (https://api.faa.gov/).");
        props.put(PROP_NOTAM_CLIENT_SECRET, "FAA API Portal client_secret paired with faa.notam.clientId.");
        props.put(PROP_ADSB_MIL_URL, "Optional override URL for the public military ADS-B feed (defaults to https://api.adsb.lol/v2/mil, keyless).");
        context.registerPropertiesWithDescriptions(props, "Plugin: Aviation & NOTAM MCP");
        synchronized (AviationNotamPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new AviationNotamClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (AviationNotamPlugin.class) {
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
     * Returns the shared {@link AviationNotamClient}.
     *
     * @return active client
     */
    public static AviationNotamClient getClient() {
        AviationNotamClient c = sharedClient;
        if (c == null) {
            synchronized (AviationNotamPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new AviationNotamClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
