package com.nadia.plugin.web;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.web.config.WebInterfaceConfig;
import com.nadia.plugin.web.server.WebInterfaceServer;
import com.nadia.plugin.web.service.WebChatSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared runtime coordinator for the {@code web-interface} PF4J plugin.
 *
 * <p>Manages extraction of static web assets into {@code ~/.nadia/plugins/data/web-interface/web/}
 * (avoiding OS file locks on the plugin JAR), wires live scheduled cron listeners from
 * {@link NadiaPluginContext}, and coordinates live HTTP server lifecycle updates from the
 * {@code 🌐 Web Interface} Settings tab.</p>
 */
public final class WebInterfaceRuntime {

    /** Canonical PF4J plugin identifier. */
    public static final String PLUGIN_ID = "web-interface";

    private static final Logger logger = LoggerFactory.getLogger(WebInterfaceRuntime.class);
    private static final List<String> BUNDLED_WEB_ASSETS = List.of("index.html", "app.css", "app.js");
    private static final WebInterfaceRuntime INSTANCE = new WebInterfaceRuntime();

    private final WebChatSessionStore sessionStore = new WebChatSessionStore();
    private final List<Runnable> statusListeners = new CopyOnWriteArrayList<>();

    private volatile NadiaPluginContext pluginContext;
    private volatile WebInterfaceServer server;
    private volatile AutoCloseable cronSubscription;

    private WebInterfaceRuntime() {
    }

    /**
     * Returns the singleton {@code WebInterfaceRuntime} instance.
     *
     * @return runtime singleton
     */
    public static WebInterfaceRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the plugin runtime, extracts bundled web assets, subscribes to scheduled cron
     * outputs, and starts the HTTP server if {@code nadia.prop.web.enabled} is {@code true}.
     *
     * @param context host {@link NadiaPluginContext}
     */
    public synchronized void initialize(NadiaPluginContext context) {
        shutdown();
        this.pluginContext = context;
        if (context == null) {
            return;
        }

        context.registerProperties(WebInterfaceConfig.ALL_PROPERTY_KEYS, "Plugin: web-interface");

        Path dataDir = context.getPluginDataDir(PLUGIN_ID);
        Path webDir = dataDir.resolve("web");
        Path uploadsDir = dataDir.resolve("uploads");
        extractBundledAssets(webDir);

        WebInterfaceConfig config = WebInterfaceConfig.load(context);
        WebInterfaceServer srv = new WebInterfaceServer(context, sessionStore, webDir, uploadsDir);
        this.server = srv;

        this.cronSubscription = context.registerScheduledJobListener(srv::publishScheduledJobMessage);

        if (config.enabled()) {
            srv.start(config);
        }
        notifyStatusListeners();
    }

    /**
     * Cleanly stops the embedded HTTP server and unregisters scheduled cron listeners when the
     * plugin is disabled, upgraded, or unloaded.
     */
    public synchronized void shutdown() {
        if (cronSubscription != null) {
            try {
                cronSubscription.close();
            } catch (Exception ignored) {
            }
            cronSubscription = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
        notifyStatusListeners();
    }

    /**
     * Saves a new {@link WebInterfaceConfig} to {@code ~/.nadia/nadia.properties} and applies it
     * immediately to the running HTTP server.
     *
     * @param newConfig updated configuration
     * @return {@code true} if applied cleanly without bind errors
     */
    public synchronized boolean saveAndApplyConfig(WebInterfaceConfig newConfig) {
        if (newConfig == null) {
            return false;
        }
        if (pluginContext != null) {
            newConfig.save(pluginContext);
        }
        boolean ok = true;
        if (server != null) {
            ok = server.applyConfiguration(newConfig);
        }
        notifyStatusListeners();
        return ok;
    }

    /**
     * Starts or restarts the HTTP server immediately using the current configuration.
     *
     * @return {@code true} if bound and running
     */
    public synchronized boolean startServerNow() {
        if (server == null) {
            return false;
        }
        WebInterfaceConfig cfg = pluginContext != null ? WebInterfaceConfig.load(pluginContext) : server.getActiveConfig();
        boolean ok = server.start(cfg);
        notifyStatusListeners();
        return ok;
    }

    /**
     * Stops the HTTP/HTTPS server immediately without altering persisted startup preferences.
     */
    public synchronized void stopServerNow() {
        if (server != null) {
            server.stop();
            notifyStatusListeners();
        }
    }

    /**
     * Regenerates the built-in self-signed PKCS#12 HTTPS keystore inside the plugin's data directory,
     * and restarts the listener if HTTPS self-signed mode is currently running.
     *
     * @return path to the newly generated {@code .p12} keystore
     * @throws IOException if certificate generation fails or plugin context is uninitialized
     */
    public synchronized Path regenerateSelfSignedCertificate() throws IOException {
        if (pluginContext == null) {
            throw new IOException("Plugin context is not initialized.");
        }
        Path dataDir = pluginContext.getPluginDataDir(PLUGIN_ID);
        Path generated = com.nadia.plugin.web.server.TlsKeystoreManager.ensureSelfSignedKeystore(dataDir, true);
        if (server != null && server.isRunning()) {
            WebInterfaceConfig cfg = loadCurrentConfig();
            if (cfg.tlsEnabled() && !cfg.hasCustomKeystore()) {
                server.start(cfg);
            }
        }
        notifyStatusListeners();
        return generated;
    }

    /**
     * Loads the current persisted (or active) {@link WebInterfaceConfig}.
     *
     * @return current configuration
     */
    public WebInterfaceConfig loadCurrentConfig() {
        if (pluginContext != null) {
            return WebInterfaceConfig.load(pluginContext);
        }
        return server != null ? server.getActiveConfig() : WebInterfaceConfig.defaults();
    }

    /**
     * Returns the active {@link WebInterfaceServer} instance, or {@code null} if uninitialized.
     *
     * @return HTTP server instance
     */
    public WebInterfaceServer getServer() {
        return server;
    }

    /**
     * Returns the shared {@link WebChatSessionStore}.
     *
     * @return session store
     */
    public WebChatSessionStore getSessionStore() {
        return sessionStore;
    }

    /**
     * Registers a UI callback notified whenever the server starts, stops, or updates configuration.
     *
     * @param listener status change callback
     */
    public void addStatusListener(Runnable listener) {
        if (listener != null) {
            statusListeners.add(listener);
        }
    }

    private void notifyStatusListeners() {
        for (Runnable r : statusListeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    private void extractBundledAssets(Path targetWebDir) {
        try {
            Files.createDirectories(targetWebDir);
            ClassLoader cl = WebInterfaceRuntime.class.getClassLoader();
            for (String asset : BUNDLED_WEB_ASSETS) {
                try (InputStream in = cl.getResourceAsStream("web/" + asset)) {
                    if (in != null) {
                        Files.copy(in, targetWebDir.resolve(asset), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        logger.warn("[WebInterface] Missing bundled resource: web/{}", asset);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("[WebInterface] Failed extracting web assets to {}: {}", targetWebDir, e.getMessage(), e);
        }
    }
}

