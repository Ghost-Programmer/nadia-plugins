package com.nadia.plugin.web.config;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Immutable configuration snapshot for the Nadia Web Interface plugin, persisted in
 * {@code ~/.nadia/nadia.properties} under {@code nadia.prop.web.*}.
 *
 * @param enabled          whether the embedded HTTP server should start automatically on plugin startup
 * @param host             network bind address ({@code "127.0.0.1"} for localhost only, {@code "0.0.0.0"} for all interfaces)
 * @param port             TCP port for the HTTP server ({@code 1024}–{@code 65535})
 * @param authToken        optional access token required to access chat APIs (blank disables authentication)
 * @param pageTitle        display title shown in the browser header and tab
 * @param allowUploads     whether file attachments and drag-and-drop uploads are permitted
 * @param maxUploadMb      maximum single uploaded file size in megabytes ({@code 1}–{@code 200})
 * @param enableCronStream whether scheduled background cron executions stream live to the web UI
 */
public record WebInterfaceConfig(
        boolean enabled,
        String host,
        int port,
        String authToken,
        String pageTitle,
        boolean allowUploads,
        int maxUploadMb,
        boolean enableCronStream
) {

    /** Property key controlling automatic Web Interface server startup. */
    public static final String PROP_ENABLED = "nadia.prop.web.enabled";

    /** Property key specifying the HTTP bind address ({@code 127.0.0.1} or {@code 0.0.0.0}). */
    public static final String PROP_HOST = "nadia.prop.web.host";

    /** Property key specifying the HTTP TCP port. */
    public static final String PROP_PORT = "nadia.prop.web.port";

    /** Property key specifying an optional access token / passcode for browser clients. */
    public static final String PROP_AUTH_TOKEN = "nadia.prop.web.authToken";

    /** Property key specifying the browser window and header title. */
    public static final String PROP_TITLE = "nadia.prop.web.title";

    /** Property key enabling or disabling file upload attachments. */
    public static final String PROP_ALLOW_UPLOADS = "nadia.prop.web.allowUploads";

    /** Property key specifying the maximum file upload size in megabytes. */
    public static final String PROP_MAX_UPLOAD_MB = "nadia.prop.web.maxUploadMb";

    /** Property key enabling or disabling live streaming of scheduled background cron runs. */
    public static final String PROP_ENABLE_CRON_STREAM = "nadia.prop.web.enableCronStream";

    /** Default HTTP bind host (loopback only for zero-trust safety). */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Default HTTP server port (avoids port 8080 and Nadia's loopback hook port 8085). */
    public static final int DEFAULT_PORT = 8088;

    /** Default browser header and page title. */
    public static final String DEFAULT_TITLE = "Nadia AI Assistant";

    /** Default maximum upload file size in megabytes. */
    public static final int DEFAULT_MAX_UPLOAD_MB = 25;

    /** All {@code nadia.prop.web.*} keys registered with Nadia's Properties UI. */
    public static final List<String> ALL_PROPERTY_KEYS = List.of(
            PROP_ENABLED,
            PROP_HOST,
            PROP_PORT,
            PROP_AUTH_TOKEN,
            PROP_TITLE,
            PROP_ALLOW_UPLOADS,
            PROP_MAX_UPLOAD_MB,
            PROP_ENABLE_CRON_STREAM
    );

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Loads the current {@code WebInterfaceConfig} from {@link NadiaPluginContext}.
     *
     * @param context host plugin context (may be {@code null})
     * @return validated {@link WebInterfaceConfig} snapshot
     */
    public static WebInterfaceConfig load(NadiaPluginContext context) {
        if (context == null) {
            return defaults();
        }
        boolean enabled = parseBoolean(context.getProperty(PROP_ENABLED, "true"), true);
        String rawHost = context.getProperty(PROP_HOST, DEFAULT_HOST);
        String host = (rawHost == null || rawHost.isBlank()) ? DEFAULT_HOST : rawHost.trim();
        int port = clampPort(parseInt(context.getProperty(PROP_PORT, String.valueOf(DEFAULT_PORT)), DEFAULT_PORT));
        String rawToken = context.getProperty(PROP_AUTH_TOKEN, "");
        String authToken = rawToken != null ? rawToken.trim() : "";
        String rawTitle = context.getProperty(PROP_TITLE, DEFAULT_TITLE);
        String title = (rawTitle == null || rawTitle.isBlank()) ? DEFAULT_TITLE : rawTitle.trim();
        boolean allowUploads = parseBoolean(context.getProperty(PROP_ALLOW_UPLOADS, "true"), true);
        int maxUploadMb = clampUploadMb(parseInt(context.getProperty(PROP_MAX_UPLOAD_MB, String.valueOf(DEFAULT_MAX_UPLOAD_MB)), DEFAULT_MAX_UPLOAD_MB));
        boolean enableCron = parseBoolean(context.getProperty(PROP_ENABLE_CRON_STREAM, "true"), true);

        return new WebInterfaceConfig(enabled, host, port, authToken, title, allowUploads, maxUploadMb, enableCron);
    }

    /**
     * Persists this configuration snapshot into {@code ~/.nadia/nadia.properties} via {@link NadiaPluginContext}.
     *
     * @param context host plugin context
     */
    public void save(NadiaPluginContext context) {
        if (context == null) {
            return;
        }
        context.setProperty(PROP_ENABLED, String.valueOf(enabled));
        context.setProperty(PROP_HOST, host != null && !host.isBlank() ? host.trim() : DEFAULT_HOST);
        context.setProperty(PROP_PORT, String.valueOf(clampPort(port)));
        context.setProperty(PROP_AUTH_TOKEN, authToken != null ? authToken.trim() : "");
        context.setProperty(PROP_TITLE, pageTitle != null && !pageTitle.isBlank() ? pageTitle.trim() : DEFAULT_TITLE);
        context.setProperty(PROP_ALLOW_UPLOADS, String.valueOf(allowUploads));
        context.setProperty(PROP_MAX_UPLOAD_MB, String.valueOf(clampUploadMb(maxUploadMb)));
        context.setProperty(PROP_ENABLE_CRON_STREAM, String.valueOf(enableCronStream));
    }

    /**
     * Returns a default {@code WebInterfaceConfig} instance.
     *
     * @return default configuration
     */
    public static WebInterfaceConfig defaults() {
        return new WebInterfaceConfig(
                true,
                DEFAULT_HOST,
                DEFAULT_PORT,
                "",
                DEFAULT_TITLE,
                true,
                DEFAULT_MAX_UPLOAD_MB,
                true
        );
    }

    /**
     * Returns whether token authentication is active.
     *
     * @return {@code true} when a non-blank {@link #authToken()} is configured
     */
    public boolean isAuthRequired() {
        return authToken != null && !authToken.isBlank();
    }

    /**
     * Generates a cryptographically random 32-character hexadecimal access token.
     *
     * @return random hex token string
     */
    public static String generateRandomToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Clamps a TCP port into the valid user-space range ({@code 1024}–{@code 65535}),
     * allowing {@code 0} solely for ephemeral port binding in automated tests.
     *
     * @param port candidate port number
     * @return clamped port
     */
    public static int clampPort(int port) {
        if (port == 0) {
            return 0;
        }
        if (port < 1024) {
            return 1024;
        }
        return Math.min(port, 65535);
    }

    /**
     * Clamps maximum upload megabytes between {@code 1} and {@code 200}.
     *
     * @param mb candidate size in megabytes
     * @return clamped size in megabytes
     */
    public static int clampUploadMb(int mb) {
        if (mb < 1) {
            return 1;
        }
        return Math.min(mb, 200);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

