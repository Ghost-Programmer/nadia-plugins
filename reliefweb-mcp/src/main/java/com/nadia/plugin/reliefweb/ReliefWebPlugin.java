package com.nadia.plugin.reliefweb;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for the ReliefWeb (UN OCHA) API MCP tool plugin
 * ({@code reliefweb-mcp}).
 */
public class ReliefWebPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "reliefweb-mcp";
    public static final String PROP_APPNAME = "nadia.prop.reliefweb.appname";
    public static final String PROP_API_URL = "nadia.prop.reliefweb.apiUrl";

    /**
     * Constructs the {@code ReliefWebPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public ReliefWebPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_APPNAME, "Pre-approved ReliefWeb application name (appname) required on all UN OCHA ReliefWeb API v2 requests.");
        props.put(PROP_API_URL, "Optional override base URL for the UN OCHA ReliefWeb API.");
        context.registerPropertiesWithDescriptions(props, "Plugin: ReliefWeb (UN OCHA) MCP");
        ReliefWebRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        ReliefWebRuntime.getInstance().shutdown();
    }
}
