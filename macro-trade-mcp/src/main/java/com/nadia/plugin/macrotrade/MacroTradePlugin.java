package com.nadia.plugin.macrotrade;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J plugin lifecycle entry point for FRED, IMF DataMapper &amp; WTO Trade/Tariff MCP tools
 * ({@code macro-trade-mcp}).
 */
public class MacroTradePlugin extends NadiaPlugin {

    public static final String PLUGIN_ID = "macro-trade-mcp";
    public static final String PROP_FRED_API_KEY = "nadia.prop.fred.apiKey";
    public static final String PROP_IMF_API_KEY = "nadia.prop.imf.apiKey";
    public static final String PROP_WTO_API_KEY = "nadia.prop.wto.apiKey";

    private static volatile HttpClient sharedHttpClient;
    private static volatile MacroTradeClient sharedClient;

    /**
     * Constructs the plugin bound to PF4J's {@link PluginWrapper}.
     *
     * @param wrapper PF4J plugin wrapper
     */
    public MacroTradePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(PROP_FRED_API_KEY, "Free Federal Reserve Economic Data (FRED) API key for CPI, GDP, interest rates, and macro series.");
        props.put(PROP_IMF_API_KEY, "Optional IMF API subscription key (Ocp-Apim-Subscription-Key) for IMF macroeconomic indicator queries.");
        props.put(PROP_WTO_API_KEY, "Free World Trade Organization (WTO) Timeseries API subscription key for bilateral trade and tariff queries.");
        context.registerPropertiesWithDescriptions(props, "Plugin: Macro & Trade MCP");
        synchronized (MacroTradePlugin.class) {
            if (sharedHttpClient == null) {
                sharedHttpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            sharedClient = new MacroTradeClient(sharedHttpClient, context);
        }
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        synchronized (MacroTradePlugin.class) {
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
     * Returns the shared {@link MacroTradeClient}.
     *
     * @return active client
     */
    public static MacroTradeClient getClient() {
        MacroTradeClient c = sharedClient;
        if (c == null) {
            synchronized (MacroTradePlugin.class) {
                if (sharedClient == null) {
                    if (sharedHttpClient == null) {
                        sharedHttpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    sharedClient = new MacroTradeClient(sharedHttpClient, null);
                }
                c = sharedClient;
            }
        }
        return c;
    }
}
