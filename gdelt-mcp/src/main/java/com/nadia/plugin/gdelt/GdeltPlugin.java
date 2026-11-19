package com.nadia.plugin.gdelt;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.List;

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
        context.registerProperties(
                List.of(PROP_DOC_API_URL, PROP_GEO_API_URL),
                "Plugin: GDELT Project MCP"
        );
        GdeltRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        GdeltRuntime.getInstance().shutdown();
    }
}
