package com.nadia.plugin.cloudflare;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for the Cloudflare Radar API MCP tool plugin
 * ({@code cloudflare-radar-mcp}).
 */
public class CloudflareRadarPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "cloudflare-radar-mcp";
    public static final String PROP_API_TOKEN = "nadia.prop.cloudflare.radar.apiToken";
    public static final String PROP_API_URL = "nadia.prop.cloudflare.radar.apiUrl";

    /**
     * Constructs the {@code CloudflareRadarPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public CloudflareRadarPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_API_TOKEN, "Cloudflare API Token with Radar:Read permission for querying outages, traffic anomalies, DDoS attacks, and BGP events.");
        props.put(PROP_API_URL, "Optional override base URL for the Cloudflare Radar v4 API.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Cloudflare Radar MCP");
        CloudflareRadarRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        CloudflareRadarRuntime.getInstance().shutdown();
    }
}
