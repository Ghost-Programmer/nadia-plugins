package com.nadia.plugin.reliefweb;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Singleton runtime managing the shared {@link HttpClient} and {@link ReliefWebClient}
 * lifecycle for the {@code reliefweb-mcp} plugin.
 */
public final class ReliefWebRuntime {

    private static final ReliefWebRuntime INSTANCE = new ReliefWebRuntime();

    private volatile NadiaPluginContext context;
    private volatile HttpClient httpClient;
    private volatile ReliefWebClient client;

    private ReliefWebRuntime() {
    }

    /**
     * Returns the singleton runtime instance.
     *
     * @return the {@code ReliefWebRuntime} instance
     */
    public static ReliefWebRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the shared HTTP client and ReliefWeb client.
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
        this.client = new ReliefWebClient(this.httpClient, context);
    }

    /**
     * Returns the active {@link ReliefWebClient}, lazily creating one if needed.
     *
     * @return active {@link ReliefWebClient}
     */
    public ReliefWebClient getClient() {
        ReliefWebClient current = this.client;
        if (current == null) {
            synchronized (this) {
                if (this.client == null) {
                    if (this.httpClient == null) {
                        this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    this.client = new ReliefWebClient(this.httpClient, this.context);
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
