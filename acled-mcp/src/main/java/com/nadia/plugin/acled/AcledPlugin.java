package com.nadia.plugin.acled;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_EMAIL, "Registered myACLED account email address used for OAuth or legacy API authentication.");
        props.put(PROP_PASSWORD, "myACLED account password used to obtain OAuth2 Bearer access tokens (recommended).");
        props.put(PROP_API_KEY, "Optional legacy ACLED API key fallback when OAuth password authentication is not used.");
        props.put(PROP_API_URL, "Optional override URL for the ACLED read API endpoint.");
        context.registerPropertiesWithDescriptions(props, "Plugin: ACLED MCP");
        AcledRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        AcledRuntime.getInstance().shutdown();
    }
}
