package com.nadia.plugin.slack.config;

import com.nadia.app.plugin.api.NadiaPluginContext;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable configuration snapshot for the {@code slack-integration} PF4J
 * plugin, persisted in
 * {@code ~/.nadia/nadia.properties} under {@code nadia.prop.slack.*} with
 * transparent fallback to
 * legacy {@code nadia.slack.*} keys.
 *
 * @param enabled             whether Slack Socket Mode and outbound
 *                            notifications are enabled
 * @param botToken            Slack Bot User OAuth Token ({@code xoxb-...})
 * @param appToken            Slack App-Level Token ({@code xapp-...}) with
 *                            {@code connections:write}
 * @param allowedUsers        comma-separated list of authorized Slack user IDs
 *                            (empty allows all workspace users)
 * @param notificationChannel default Slack channel name or ID (e.g.,
 *                            {@code #notifications} or {@code C01234567})
 */
public record SlackConfig(
        boolean enabled,
        String botToken,
        String appToken,
        String allowedUsers,
        String notificationChannel) {

    /** Property key enabling or disabling Slack integration. */
    public static final String PROP_ENABLED = "nadia.prop.slack.enabled";
    /** Property key storing the Slack Bot User OAuth Token ({@code xoxb-...}). */
    public static final String PROP_BOT_TOKEN = "nadia.prop.slack.botToken";
    /** Property key storing the Slack App-Level Token ({@code xapp-...}). */
    public static final String PROP_APP_TOKEN = "nadia.prop.slack.appToken";
    /** Property key storing comma-separated allowed Slack user IDs. */
    public static final String PROP_ALLOWED_USERS = "nadia.prop.slack.allowedUsers";
    /** Property key storing the default Slack notification channel. */
    public static final String PROP_NOTIFICATION_CHANNEL = "nadia.prop.slack.notificationChannel";

    /**
     * Ordered list of all {@code nadia.prop.slack.*} keys registered with Nadia's
     * Properties manager.
     */
    public static final List<String> ALL_PROPERTY_KEYS = List.of(
            PROP_ENABLED,
            PROP_BOT_TOKEN,
            PROP_APP_TOKEN,
            PROP_ALLOWED_USERS,
            PROP_NOTIFICATION_CHANNEL);

    /**
     * Constructs a normalized {@code SlackConfig}.
     */
    public SlackConfig {
        botToken = botToken != null ? botToken.trim() : "";
        appToken = appToken != null ? appToken.trim() : "";
        allowedUsers = allowedUsers != null ? allowedUsers.trim() : "";
        notificationChannel = notificationChannel != null ? notificationChannel.trim() : "";
    }

    /**
     * Returns a default disabled {@code SlackConfig}.
     *
     * @return default configuration
     */
    public static SlackConfig defaults() {
        return new SlackConfig(false, "", "", "", "");
    }

    /**
     * Loads the active {@code SlackConfig} from {@link NadiaPluginContext},
     * transparently falling back
     * to legacy {@code nadia.slack.*} entries in {@code ~/.nadia/nadia.properties}
     * if not yet migrated.
     *
     * @param context host plugin context (may be {@code null})
     * @return loaded configuration
     */
    public static SlackConfig load(NadiaPluginContext context) {
        if (context == null) {
            return defaults();
        }
        Properties legacy = loadRawNadiaProperties(context);

        String rawEnabled = readWithLegacyFallback(context, PROP_ENABLED, legacy, "nadia.slack.enabled", "false");
        String botTok = readWithLegacyFallback(context, PROP_BOT_TOKEN, legacy, "nadia.slack.bot-token", "");
        String appTok = readWithLegacyFallback(context, PROP_APP_TOKEN, legacy, "nadia.slack.app-token", "");
        String allowed = readWithLegacyFallback(context, PROP_ALLOWED_USERS, legacy, "nadia.slack.allowed-users", "");
        String channel = readWithLegacyFallback(context, PROP_NOTIFICATION_CHANNEL, legacy,
                "nadia.slack.notification-channel", "");

        return new SlackConfig(
                Boolean.parseBoolean(rawEnabled),
                botTok,
                appTok,
                allowed,
                channel);
    }

    /**
     * Persists this configuration into {@code ~/.nadia/nadia.properties} via
     * {@link NadiaPluginContext}.
     *
     * @param context host plugin context
     */
    public void save(NadiaPluginContext context) {
        if (context == null) {
            return;
        }
        context.setProperty(PROP_ENABLED, String.valueOf(enabled));
        context.setProperty(PROP_BOT_TOKEN, botToken);
        context.setProperty(PROP_APP_TOKEN, appToken);
        context.setProperty(PROP_ALLOWED_USERS, allowedUsers);
        context.setProperty(PROP_NOTIFICATION_CHANNEL, notificationChannel);
    }

    /**
     * Parses {@link #allowedUsers()} into a lowercase set of Slack user IDs.
     *
     * @return normalized set of allowed Slack user IDs (empty if all workspace
     *         users are permitted)
     */
    public Set<String> allowedUsersSet() {
        if (allowedUsers == null || allowedUsers.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(allowedUsers.split("[,;\\s]+"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Checks whether the given Slack user ID is authorized to send prompts to
     * Nadia.
     *
     * @param userId Slack user ID (e.g. {@code U01234567})
     * @return {@code true} if allowed or if no user restriction is configured
     */
    public boolean isUserAllowed(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Set<String> allowed = allowedUsersSet();
        if (allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(userId.trim().toLowerCase());
    }

    /**
     * Returns {@code true} when both {@link #botToken()} and {@link #appToken()}
     * are non-blank.
     *
     * @return {@code true} if Socket Mode credentials are present
     */
    public boolean hasSocketModeTokens() {
        return !botToken.isBlank() && !appToken.isBlank();
    }

    private static String readWithLegacyFallback(NadiaPluginContext context,
            String propKey,
            Properties legacyProps,
            String legacyKey,
            String defaultVal) {
        String val = context.getProperty(propKey, null);
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        if (legacyProps != null) {
            String legacyVal = legacyProps.getProperty(legacyKey);
            if (legacyVal != null && !legacyVal.isBlank()) {
                return legacyVal.trim();
            }
        }
        return val != null ? val.trim() : defaultVal;
    }

    private static Properties loadRawNadiaProperties(NadiaPluginContext context) {
        try {
            Path home = context.getNadiaHomeDir();
            if (home == null) {
                return null;
            }
            Path propsFile = home.resolve("nadia.properties");
            if (!Files.isRegularFile(propsFile)) {
                return null;
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(propsFile)) {
                props.load(in);
            }
            return props;
        } catch (Exception ignored) {
            return null;
        }
    }
}
