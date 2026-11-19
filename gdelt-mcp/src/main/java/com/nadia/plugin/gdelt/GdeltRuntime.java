package com.nadia.plugin.gdelt;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Singleton runtime managing the shared {@link HttpClient} and {@link GdeltClient}
 * lifecycle for the {@code gdelt-mcp} plugin.
 */
public final class GdeltRuntime {

    private static final GdeltRuntime INSTANCE = new GdeltRuntime();

    private volatile NadiaPluginContext context;
    private volatile HttpClient httpClient;
    private volatile GdeltClient client;

    private GdeltRuntime() {
    }

    /**
     * Returns the singleton runtime instance.
     *
     * @return the {@code GdeltRuntime} instance
     */
    public static GdeltRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the shared HTTP client and GDELT client.
     *
     * @param context the host {@link NadiaPluginContext}
     */
    public synchronized void initialize(NadiaPluginContext context) {
        this.context = context;
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        this.client = new GdeltClient(this.httpClient, context);
    }

    /**
     * Returns the active {@link GdeltClient}, lazily creating one if needed.
     *
     * @return active {@link GdeltClient}
     */
    public GdeltClient getClient() {
        GdeltClient current = this.client;
        if (current == null) {
            synchronized (this) {
                if (this.client == null) {
                    if (this.httpClient == null) {
                        this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    this.client = new GdeltClient(this.httpClient, this.context);
                }
                current = this.client;
            }
        }
        return current;
    }

    /**
     * Shuts down the runtime and releases HTTP client resources.
     */
    public synchronized void shutdown() {
        this.client = null;
        this.context = null;
        if (this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
            this.httpClient = null;
        }
    }
}
