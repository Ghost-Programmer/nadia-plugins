package com.nadia.plugin.cloudflare;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Singleton runtime managing the shared {@link HttpClient} and {@link CloudflareRadarClient}
 * lifecycle for the {@code cloudflare-radar-mcp} plugin.
 */
public final class CloudflareRadarRuntime {

    private static final CloudflareRadarRuntime INSTANCE = new CloudflareRadarRuntime();

    private volatile NadiaPluginContext context;
    private volatile HttpClient httpClient;
    private volatile CloudflareRadarClient client;

    private CloudflareRadarRuntime() {
    }

    /**
     * Returns the singleton runtime instance.
     *
     * @return the {@code CloudflareRadarRuntime} instance
     */
    public static CloudflareRadarRuntime getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the shared HTTP client and Cloudflare Radar client.
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
        this.client = new CloudflareRadarClient(this.httpClient, context);
    }

    /**
     * Returns the active {@link CloudflareRadarClient}, lazily creating one if needed.
     *
     * @return active {@link CloudflareRadarClient}
     */
    public CloudflareRadarClient getClient() {
        CloudflareRadarClient current = this.client;
        if (current == null) {
            synchronized (this) {
                if (this.client == null) {
                    if (this.httpClient == null) {
                        this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .build();
                    }
                    this.client = new CloudflareRadarClient(this.httpClient, this.context);
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
