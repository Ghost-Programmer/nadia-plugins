package com.nadia.plugin.reliefweb;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.List;

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
        context.registerProperties(
                List.of(PROP_APPNAME, PROP_API_URL),
                "Plugin: ReliefWeb (UN OCHA) MCP"
        );
        ReliefWebRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        ReliefWebRuntime.getInstance().shutdown();
    }
}
