package com.nadia.plugin.acled;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Singleton runtime managing the shared {@link HttpClient} and {@link AcledClient}
 * lifecycle for the {@code acled-mcp} plugin.
 */
public final class AcledRuntime {

    private static final AcledRuntime INSTANCE = new AcledRuntime();

    private volatile NadiaPluginContext context;
    private volatile HttpClient httpClient;
    private volatile AcledClient client;

    private AcledRuntime() {
    }

    /**
     * Returns the singleton runtime instance.
     *
     * @return the {@code AcledRuntime} instance
     */
    public static AcledRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the shared HTTP client and ACLED client.
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
        this.client = new AcledClient(this.httpClient, context);
    }

    /**
     * Returns the active {@link AcledClient}, lazily creating one if needed.
     *
     * @return active {@link AcledClient}
     */
    public AcledClient getClient() {
        AcledClient current = this.client;
        if (current == null) {
            synchronized (this) {
                if (this.client == null) {
                    if (this.httpClient == null) {
                        this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    this.client = new AcledClient(this.httpClient, this.context);
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
