package com.nadia.plugin.web.config;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration snapshot for the Nadia Web Interface plugin, persisted in
 * {@code ~/.nadia/nadia.properties} under {@code nadia.prop.web.*}.
 *
 * @param enabled          whether the embedded HTTP/HTTPS server should start automatically on plugin startup
 * @param tlsEnabled       {@code true} to serve over {@code https://} (TLS 1.2/1.3), {@code false} for {@code http://}
 * @param host             network bind address ({@code "127.0.0.1"} for localhost only, {@code "0.0.0.0"} for all interfaces)
 * @param port             TCP port for the server ({@code 1024}–{@code 65535})
 * @param keystorePath     optional path to a custom PKCS#12 ({@code .p12}/{@code .pfx}) or JKS ({@code .jks}) keystore (blank uses auto-generated self-signed cert)
 * @param keystorePassword password for the custom keystore
 * @param authToken        optional access token required to access chat APIs (blank disables authentication)
 * @param pageTitle        display title shown in the browser header and tab
 * @param allowUploads     whether file attachments and drag-and-drop uploads are permitted
 * @param maxUploadMb      maximum single uploaded file size in megabytes ({@code 1}–{@code 200})
 * @param enableCronStream whether scheduled background cron executions stream live to the web UI
 */
public record WebInterfaceConfig(
        boolean enabled,
        boolean tlsEnabled,
        String host,
        int port,
        String keystorePath,
        String keystorePassword,
        String authToken,
        String pageTitle,
        boolean allowUploads,
        int maxUploadMb,
        boolean enableCronStream
) {

    /** Property key controlling automatic Web Interface server startup. */
    public static final String PROP_ENABLED = "nadia.prop.web.enabled";

    /** Property key selecting {@code "http"} or {@code "https"} transport protocol. */
    public static final String PROP_PROTOCOL = "nadia.prop.web.protocol";

    /** Property key specifying the bind address ({@code 127.0.0.1} or {@code 0.0.0.0}). */
    public static final String PROP_HOST = "nadia.prop.web.host";

    /** Property key specifying the TCP port. */
    public static final String PROP_PORT = "nadia.prop.web.port";

    /** Property key specifying an optional custom PKCS#12 or JKS TLS keystore file path. */
    public static final String PROP_SSL_KEYSTORE_PATH = "nadia.prop.web.sslKeystorePath";

    /** Property key specifying the password for the custom TLS keystore. */
    public static final String PROP_SSL_KEYSTORE_PASSWORD = "nadia.prop.web.sslKeystorePassword";

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

    /** Default transport protocol ({@code "http"}). */
    public static final String DEFAULT_PROTOCOL = "http";

    /** Default bind host (loopback only for zero-trust safety). */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Default server port (avoids port 8080 and Nadia's loopback hook port 8085). */
    public static final int DEFAULT_PORT = 8088;

    /** Default browser header and page title. */
    public static final String DEFAULT_TITLE = "Nadia AI Assistant";

    /** Default maximum upload file size in megabytes. */
    public static final int DEFAULT_MAX_UPLOAD_MB = 25;

    /** All {@code nadia.prop.web.*} keys registered with Nadia's Properties UI. */
    public static final List<String> ALL_PROPERTY_KEYS = List.of(
            PROP_ENABLED,
            PROP_PROTOCOL,
            PROP_HOST,
            PROP_PORT,
            PROP_SSL_KEYSTORE_PATH,
            PROP_SSL_KEYSTORE_PASSWORD,
            PROP_AUTH_TOKEN,
            PROP_TITLE,
            PROP_ALLOW_UPLOADS,
            PROP_MAX_UPLOAD_MB,
            PROP_ENABLE_CRON_STREAM
    );

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Convenience constructor defaulting to plain HTTP without a custom TLS keystore.
     *
     * @param enabled          auto-start flag
     * @param host             bind address
     * @param port             TCP port
     * @param authToken        optional access token
     * @param pageTitle        browser page title
     * @param allowUploads     allow file attachments
     * @param maxUploadMb      maximum upload size in MB
     * @param enableCronStream enable live cron stream tab
     */
    public WebInterfaceConfig(
            boolean enabled,
            String host,
            int port,
            String authToken,
            String pageTitle,
            boolean allowUploads,
            int maxUploadMb,
            boolean enableCronStream) {
        this(enabled, false, host, port, "", "", authToken, pageTitle, allowUploads, maxUploadMb, enableCronStream);
    }

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
        String rawProtocol = context.getProperty(PROP_PROTOCOL, DEFAULT_PROTOCOL);
        boolean tlsEnabled = isHttpsProtocol(rawProtocol);

        String rawHost = context.getProperty(PROP_HOST, DEFAULT_HOST);
        String host = (rawHost == null || rawHost.isBlank()) ? DEFAULT_HOST : rawHost.trim();
        int port = clampPort(parseInt(context.getProperty(PROP_PORT, String.valueOf(DEFAULT_PORT)), DEFAULT_PORT));

        String ksPath = trimOrEmpty(context.getProperty(PROP_SSL_KEYSTORE_PATH, ""));
        String ksPass = context.getProperty(PROP_SSL_KEYSTORE_PASSWORD, "");
        if (ksPass == null) {
            ksPass = "";
        }

        String authToken = trimOrEmpty(context.getProperty(PROP_AUTH_TOKEN, ""));
        String rawTitle = context.getProperty(PROP_TITLE, DEFAULT_TITLE);
        String title = (rawTitle == null || rawTitle.isBlank()) ? DEFAULT_TITLE : rawTitle.trim();
        boolean allowUploads = parseBoolean(context.getProperty(PROP_ALLOW_UPLOADS, "true"), true);
        int maxUploadMb = clampUploadMb(parseInt(context.getProperty(PROP_MAX_UPLOAD_MB, String.valueOf(DEFAULT_MAX_UPLOAD_MB)), DEFAULT_MAX_UPLOAD_MB));
        boolean enableCron = parseBoolean(context.getProperty(PROP_ENABLE_CRON_STREAM, "true"), true);

        return new WebInterfaceConfig(
                enabled,
                tlsEnabled,
                host,
                port,
                ksPath,
                ksPass,
                authToken,
                title,
                allowUploads,
                maxUploadMb,
                enableCron
        );
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
        context.setProperty(PROP_PROTOCOL, scheme());
        context.setProperty(PROP_HOST, host != null && !host.isBlank() ? host.trim() : DEFAULT_HOST);
        context.setProperty(PROP_PORT, String.valueOf(clampPort(port)));
        context.setProperty(PROP_SSL_KEYSTORE_PATH, trimOrEmpty(keystorePath));
        context.setProperty(PROP_SSL_KEYSTORE_PASSWORD, keystorePassword != null ? keystorePassword : "");
        context.setProperty(PROP_AUTH_TOKEN, trimOrEmpty(authToken));
        context.setProperty(PROP_TITLE, pageTitle != null && !pageTitle.isBlank() ? pageTitle.trim() : DEFAULT_TITLE);
        context.setProperty(PROP_ALLOW_UPLOADS, String.valueOf(allowUploads));
        context.setProperty(PROP_MAX_UPLOAD_MB, String.valueOf(clampUploadMb(maxUploadMb)));
        context.setProperty(PROP_ENABLE_CRON_STREAM, String.valueOf(enableCronStream));
    }

    /**
     * Returns a default {@code WebInterfaceConfig} instance ({@code http://127.0.0.1:8088}).
     *
     * @return default configuration
     */
    public static WebInterfaceConfig defaults() {
        return new WebInterfaceConfig(
                true,
                false,
                DEFAULT_HOST,
                DEFAULT_PORT,
                "",
                "",
                "",
                DEFAULT_TITLE,
                true,
                DEFAULT_MAX_UPLOAD_MB,
                true
        );
    }

    /**
     * Returns the active URL scheme ({@code "https"} when {@link #tlsEnabled()} is {@code true}, else {@code "http"}).
     *
     * @return {@code "https"} or {@code "http"}
     */
    public String scheme() {
        return tlsEnabled ? "https" : "http";
    }

    /**
     * Returns whether a custom external keystore path has been configured.
     *
     * @return {@code true} if {@link #keystorePath()} is non-blank
     */
    public boolean hasCustomKeystore() {
        return keystorePath != null && !keystorePath.isBlank();
    }

    /**
     * Checks whether transport-level bind or TLS settings differ from another configuration,
     * requiring an HTTP/HTTPS socket rebind.
     *
     * @param other comparison configuration
     * @return {@code true} if protocol, host, port, or keystore parameters changed
     */
    public boolean requiresListenerRebind(WebInterfaceConfig other) {
        if (other == null) {
            return true;
        }
        return this.tlsEnabled != other.tlsEnabled
                || this.port != other.port
                || !Objects.equals(this.host, other.host)
                || !Objects.equals(trimOrEmpty(this.keystorePath), trimOrEmpty(other.keystorePath))
                || !Objects.equals(this.keystorePassword, other.keystorePassword);
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
     * Determines whether a protocol string represents HTTPS/TLS mode.
     *
     * @param value raw protocol property value (e.g., {@code "https"}, {@code "http"})
     * @return {@code true} if HTTPS mode is selected
     */
    public static boolean isHttpsProtocol(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return "https".equals(v) || "tls".equals(v) || "true".equals(v);
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

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
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
