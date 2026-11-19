package com.nadia.plugin.slack;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

/**
 * PF4J plugin lifecycle entry point for the Nadia Slack Integration
 * ({@code slack-integration}).
 */
public class SlackIntegrationPlugin extends NadiaPlugin {

    /**
     * Constructs the {@code SlackIntegrationPlugin} bound to PF4J's
     * {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public SlackIntegrationPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        SlackRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        SlackRuntime.getInstance().shutdown();
    }
}
