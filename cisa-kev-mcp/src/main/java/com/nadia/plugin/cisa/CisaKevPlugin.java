package com.nadia.plugin.cisa;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for the CISA KEV &amp; Cybersecurity Advisories
 * MCP tool plugin ({@code cisa-kev-mcp}).
 */
public class CisaKevPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "cisa-kev-mcp";
    public static final String PROP_KEV_URL = "nadia.prop.cisa.kevUrl";
    public static final String PROP_ADVISORIES_FEED_URL = "nadia.prop.cisa.advisoriesFeedUrl";

    /**
     * Constructs the {@code CisaKevPlugin} bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public CisaKevPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_KEV_URL, "Optional override URL for the CISA Known Exploited Vulnerabilities (KEV) JSON feed.");
        props.put(PROP_ADVISORIES_FEED_URL, "Optional override URL for the CISA Cybersecurity Advisories XML/RSS feed.");
        context.registerPropertiesWithDescriptions(props, "Plugin: CISA KEV & Advisories MCP");
        CisaKevRuntime.getInstance().initialize(context);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        CisaKevRuntime.getInstance().shutdown();
    }
}
