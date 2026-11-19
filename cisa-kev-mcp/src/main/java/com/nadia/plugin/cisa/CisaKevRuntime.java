package com.nadia.plugin.cisa;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Singleton runtime managing the shared {@link HttpClient} and {@link CisaKevClient}
 * lifecycle for the {@code cisa-kev-mcp} plugin.
 */
public final class CisaKevRuntime {

    private static final CisaKevRuntime INSTANCE = new CisaKevRuntime();

    private volatile NadiaPluginContext context;
    private volatile HttpClient httpClient;
    private volatile CisaKevClient client;

    private CisaKevRuntime() {
    }

    /**
     * Returns the singleton runtime instance.
     *
     * @return the {@code CisaKevRuntime} instance
     */
    public static CisaKevRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the shared HTTP client and CISA client using the host plugin context.
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
        this.client = new CisaKevClient(this.httpClient, context);
    }

    /**
     * Returns the active {@link CisaKevClient}, lazily creating one if needed.
     *
     * @return active {@link CisaKevClient}
     */
    public CisaKevClient getClient() {
        CisaKevClient current = this.client;
        if (current == null) {
            synchronized (this) {
                if (this.client == null) {
                    if (this.httpClient == null) {
                        this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    this.client = new CisaKevClient(this.httpClient, this.context);
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
