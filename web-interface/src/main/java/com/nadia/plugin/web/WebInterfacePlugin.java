package com.nadia.plugin.web;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

/**
 * PF4J plugin lifecycle entry point for the Nadia Browser Web Chat Interface ({@code web-interface}).
 */
public class WebInterfacePlugin extends NadiaPlugin {

    /**
     * Constructs the {@code WebInterfacePlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public WebInterfacePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        WebInterfaceRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        WebInterfaceRuntime.getInstance().shutdown();
    }
}

