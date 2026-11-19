package com.nadia.plugin.cloudflare;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.List;

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
        context.registerProperties(
                List.of(PROP_API_TOKEN, PROP_API_URL),
                "Plugin: Cloudflare Radar MCP"
        );
        CloudflareRadarRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        CloudflareRadarRuntime.getInstance().shutdown();
    }
}
