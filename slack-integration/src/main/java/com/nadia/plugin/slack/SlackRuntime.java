package com.nadia.plugin.slack;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.slack.client.SlackSocketClient;
import com.nadia.plugin.slack.config.SlackConfig;
import com.nadia.plugin.slack.markdown.SlackMarkdownConverter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared singleton runtime coordinator for the {@code slack-integration} PF4J plugin.
 *
 * <p>Coordinates {@link SlackConfig} persistence, {@link SlackSocketClient} lifecycle, and UI status
 * listeners across the plugin's tool, UI settings tab, and automated background alert extensions.</p>
 */
public final class SlackRuntime {

    /** Canonical PF4J plugin identifier. */
    public static final String PLUGIN_ID = "slack-integration";

    private static final SlackRuntime INSTANCE = new SlackRuntime();

    private final SlackMarkdownConverter markdownConverter = new SlackMarkdownConverter();
    private final List<Runnable> statusListeners = new CopyOnWriteArrayList<>();

    private volatile NadiaPluginContext pluginContext;
    private volatile SlackSocketClient socketClient;

    private SlackRuntime() {
    }

    /**
     * Returns the singleton {@code SlackRuntime}.
     *
     * @return singleton instance
     */
    public static SlackRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the Slack plugin runtime, registers {@code nadia.prop.slack.*} keys, and starts
     * Socket Mode if enabled in configuration.
     *
     * @param context host {@link NadiaPluginContext}
     */
    public synchronized void initialize(NadiaPluginContext context) {
        shutdown();
        this.pluginContext = context;
        if (context == null) {
            return;
        }

        context.registerProperties(SlackConfig.ALL_PROPERTY_KEYS, "Plugin: " + PLUGIN_ID);

        SlackConfig config = SlackConfig.load(context);
        SlackSocketClient client = new SlackSocketClient(context, markdownConverter, this::notifyStatusListeners);
        this.socketClient = client;
        client.applyConfig(config);
    }

    /**
     * Stops the active {@link SlackSocketClient} and releases background resources when the plugin is unloaded.
     */
    public synchronized void shutdown() {
        if (socketClient != null) {
            socketClient.close();
            socketClient = null;
        }
        notifyStatusListeners();
    }

    /**
     * Persists updated {@link SlackConfig} settings and immediately restarts or stops the Socket Mode connection.
     *
     * @param newConfig updated configuration
     */
    public synchronized void saveAndApplyConfig(SlackConfig newConfig) {
        if (newConfig == null) {
            return;
        }
        if (pluginContext != null) {
            newConfig.save(pluginContext);
        }
        if (socketClient == null && pluginContext != null) {
            socketClient = new SlackSocketClient(pluginContext, markdownConverter, this::notifyStatusListeners);
        }
        if (socketClient != null) {
            socketClient.applyConfig(newConfig);
        }
        notifyStatusListeners();
    }

    /**
     * Loads the current {@link SlackConfig} from the host context (or active client).
     *
     * @return current configuration
     */
    public SlackConfig loadCurrentConfig() {
        SlackSocketClient client = this.socketClient;
        if (client != null) {
            return client.getActiveConfig();
        }
        return SlackConfig.load(pluginContext);
    }

    /**
     * Returns the active {@link SlackSocketClient} (may be {@code null} if not initialized).
     *
     * @return active client or {@code null}
     */
    public SlackSocketClient getSocketClient() {
        return socketClient;
    }

    /**
     * Returns the shared {@link SlackMarkdownConverter}.
     *
     * @return markdown converter
     */
    public SlackMarkdownConverter getMarkdownConverter() {
        return markdownConverter;
    }

    /**
     * Returns the host {@link NadiaPluginContext}.
     *
     * @return plugin context or {@code null}
     */
    public NadiaPluginContext getPluginContext() {
        return pluginContext;
    }

    /**
     * Registers a UI listener notified whenever connection state changes.
     *
     * @param listener callback runnable
     */
    public void addStatusListener(Runnable listener) {
        if (listener != null) {
            statusListeners.add(listener);
        }
    }

    /**
     * Removes a previously registered status listener.
     *
     * @param listener callback runnable
     */
    public void removeStatusListener(Runnable listener) {
        statusListeners.remove(listener);
    }

    private void notifyStatusListeners() {
        for (Runnable listener : statusListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
            }
        }
    }
}

