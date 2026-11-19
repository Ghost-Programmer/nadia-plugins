package com.nadia.plugin.eurepoc;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.List;

/**
 * PF4J plugin lifecycle entry point for the EuRepoC (European Repository of Cyber Incidents)
 * MCP tool plugin ({@code eurepoc-mcp}).
 */
public class EuRepoCPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "eurepoc-mcp";
    public static final String PROP_DATASET_URL = "nadia.prop.eurepoc.datasetUrl";
    public static final String PROP_API_URL = "nadia.prop.eurepoc.apiUrl";
    public static final String PROP_API_KEY = "nadia.prop.eurepoc.apiKey";

    /**
     * Constructs the {@code EuRepoCPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public EuRepoCPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        context.registerProperties(
                List.of(PROP_DATASET_URL, PROP_API_URL, PROP_API_KEY),
                "Plugin: EuRepoC MCP"
        );
        EuRepoCRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        EuRepoCRuntime.getInstance().shutdown();
    }
}
