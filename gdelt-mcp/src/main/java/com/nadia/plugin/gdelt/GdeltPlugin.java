package com.nadia.plugin.gdelt;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for the GDELT Project (Global Database of Events,
 * Language, and Tone) MCP tool plugin ({@code gdelt-mcp}).
 */
public class GdeltPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "gdelt-mcp";
    public static final String PROP_DOC_API_URL = "nadia.prop.gdelt.docApiUrl";
    public static final String PROP_GEO_API_URL = "nadia.prop.gdelt.geoApiUrl";

    /**
     * Constructs the {@code GdeltPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public GdeltPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_DOC_API_URL, "Optional override URL for the GDELT Project DOC 2.0 API endpoint.");
        props.put(PROP_GEO_API_URL, "Optional override URL for the GDELT Project GEO 2.0 API endpoint.");
        context.registerPropertiesWithDescriptions(props, "Plugin: GDELT Project MCP");
        GdeltRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        GdeltRuntime.getInstance().shutdown();
    }
}
