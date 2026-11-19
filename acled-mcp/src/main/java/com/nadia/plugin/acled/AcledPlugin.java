package com.nadia.plugin.acled;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.List;

/**
 * PF4J plugin lifecycle entry point for the ACLED (Armed Conflict Location &amp; Event Data)
 * MCP tool plugin ({@code acled-mcp}).
 */
public class AcledPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "acled-mcp";
    public static final String PROP_EMAIL = "nadia.prop.acled.email";
    public static final String PROP_PASSWORD = "nadia.prop.acled.password";
    public static final String PROP_API_KEY = "nadia.prop.acled.apiKey";
    public static final String PROP_API_URL = "nadia.prop.acled.apiUrl";

    /**
     * Constructs the {@code AcledPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public AcledPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        context.registerProperties(
                List.of(PROP_EMAIL, PROP_PASSWORD, PROP_API_KEY, PROP_API_URL),
                "Plugin: ACLED MCP"
        );
        AcledRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        AcledRuntime.getInstance().shutdown();
    }
}
