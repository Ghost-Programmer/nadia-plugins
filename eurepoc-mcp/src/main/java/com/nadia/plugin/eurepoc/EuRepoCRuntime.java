package com.nadia.plugin.eurepoc;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Singleton runtime managing the shared {@link HttpClient} and {@link EuRepoCClient}
 * lifecycle for the {@code eurepoc-mcp} plugin.
 */
public final class EuRepoCRuntime {

    private static final EuRepoCRuntime INSTANCE = new EuRepoCRuntime();

    private volatile NadiaPluginContext context;
    private volatile HttpClient httpClient;
    private volatile EuRepoCClient client;

    private EuRepoCRuntime() {
    }

    /**
     * Returns the singleton runtime instance.
     *
     * @return the {@code EuRepoCRuntime} instance
     */
    public static EuRepoCRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the shared HTTP client and EuRepoC client using the host plugin context.
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
        this.client = new EuRepoCClient(this.httpClient, context);
    }

    /**
     * Returns the active {@link EuRepoCClient}, lazily creating a standalone client if needed.
     *
     * @return active {@link EuRepoCClient}
     */
    public EuRepoCClient getClient() {
        EuRepoCClient current = this.client;
        if (current == null) {
            synchronized (this) {
                if (this.client == null) {
                    if (this.httpClient == null) {
                        this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    this.client = new EuRepoCClient(this.httpClient, this.context);
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
