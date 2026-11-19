package com.nadia.plugin.factcheck;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for the Google Fact Check Tools API MCP tool plugin
 * ({@code factcheck-mcp}).
 */
public class FactCheckPlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "factcheck-mcp";
    public static final String PROP_API_KEY = "nadia.prop.google.factcheck.apiKey";

    private static volatile HttpClient sharedHttpClient;
    private static volatile FactCheckClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public FactCheckPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_API_KEY, "Google Cloud API key with Fact Check Tools API enabled for verifying breaking claims and published fact-checks.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Google Fact Check MCP");
        synchronized (FactCheckPlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new FactCheckClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (FactCheckPlugin.class) {
            sharedClient = null;
            if (sharedHttpClient != null) {
                try {
                    sharedHttpClient.close();
                } catch (Exception ignored) {
                }
                sharedHttpClient = null;
            }
        }
    }

    /**
     * Returns the shared {@link FactCheckClient}.
     *
     * @return active client
     */
    public static FactCheckClient getClient() {
        FactCheckClient c = sharedClient;
        if (c == null) {
            synchronized (FactCheckPlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new FactCheckClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
