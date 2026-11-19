package com.nadia.plugin.slack.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.PluginChatTurn;
import com.nadia.app.plugin.api.PluginStaffDescriptor;
import com.nadia.plugin.slack.config.SlackConfig;
import com.nadia.plugin.slack.markdown.SlackMarkdownConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Pure-JDK Slack Socket Mode (WebSocket) and Web API client for the
 * {@code slack-integration} PF4J plugin.
 *
 * <p>
 * Uses {@link HttpClient} and {@link WebSocket} without external WebSocket/HTTP
 * fat-JAR dependencies,
 * avoiding PF4J classloader conflicts and OS file locks while supporting live
 * hot-reload and clean shutdown.
 * </p>
 */
public class SlackSocketClient {

    /** Default Slack Web API base endpoint. */
    public static final String DEFAULT_SLACK_API_BASE = "https://slack.com/api";

    private static final Logger logger = LoggerFactory.getLogger(SlackSocketClient.class);
    private static final Pattern MENTION_TAG_PATTERN = Pattern.compile("<@[A-Z0-9]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final int MAX_FRAME_BUFFER_CAPACITY = 64 * 1024;
    private static final long MAX_RECONNECT_BACKOFF_MS = 60_000L;

    private final NadiaPluginContext pluginContext;
    private final SlackMarkdownConverter markdownConverter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final Runnable statusChangeCallback;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private volatile String apiBaseUrl = DEFAULT_SLACK_API_BASE;
    private volatile SlackConfig activeConfig = SlackConfig.defaults();
    private volatile WebSocket activeWebSocket;
    private volatile ScheduledExecutorService reconnectExecutor;
    private volatile ScheduledFuture<?> pendingReconnect;
    private volatile boolean running = false;
    private volatile boolean desiredActive = false;
    private volatile String connectionStatus = "Disabled";

    /**
     * Constructs a {@code SlackSocketClient}.
     *
     * @param pluginContext        host {@link NadiaPluginContext}
     * @param markdownConverter    CommonMark-to-Slack {@code mrkdwn} converter
     * @param statusChangeCallback optional callback invoked when connection status
     *                             changes
     */
    public SlackSocketClient(NadiaPluginContext pluginContext,
            SlackMarkdownConverter markdownConverter,
            Runnable statusChangeCallback) {
        this.pluginContext = pluginContext;
        this.markdownConverter = markdownConverter != null ? markdownConverter : new SlackMarkdownConverter();
        this.statusChangeCallback = statusChangeCallback;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Overrides the Slack Web API base URL (used for loopback mock servers in unit
     * tests).
     *
     * @param apiBaseUrl base URL without trailing slash
     */
    public void setApiBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl != null && !apiBaseUrl.isBlank()) {
            this.apiBaseUrl = apiBaseUrl.endsWith("/")
                    ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1)
                    : apiBaseUrl.trim();
        }
    }

    /**
     * Applies the supplied {@link SlackConfig}, stopping any prior Socket Mode
     * session and starting a new
     * connection if {@link SlackConfig#enabled()} is {@code true}.
     *
     * @param config updated Slack configuration
     */
    public synchronized void applyConfig(SlackConfig config) {
        this.activeConfig = config != null ? config : SlackConfig.defaults();
        stop();
        if (this.activeConfig.enabled()) {
            start();
        } else {
            updateStatus("Disabled");
        }
    }

    /**
     * Establishes a Slack Socket Mode WebSocket connection via
     * {@code apps.connections.open}.
     */
    public synchronized void start() {
        SlackConfig cfg = this.activeConfig;
        if (!cfg.enabled()) {
            this.desiredActive = false;
            this.running = false;
            updateStatus("Disabled");
            return;
        }
        if (!cfg.hasSocketModeTokens()) {
            this.desiredActive = false;
            this.running = false;
            updateStatus("Missing Tokens");
            logger.warn("[SlackPlugin] Cannot start Slack Socket Mode: bot token or app token is missing.");
            return;
        }
        if (this.running && this.activeWebSocket != null) {
            return;
        }

        this.desiredActive = true;
        ensureReconnectExecutor();
        connectSocketModeOnce();
    }

    private void connectSocketModeOnce() {
        if (!desiredActive) {
            return;
        }
        SlackConfig cfg = this.activeConfig;
        try {
            updateStatus("Connecting...");
            HttpRequest openReq = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/apps.connections.open"))
                    .header("Authorization", "Bearer " + cfg.appToken())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> openResp = httpClient.send(openReq,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!desiredActive) {
                return;
            }
            JsonNode root = objectMapper.readTree(openResp.body());
            if (!root.path("ok").asBoolean(false)) {
                String err = root.path("error").asText("connection_open_failed");
                this.running = false;
                updateStatus("Error: " + err);
                logger.warn("[SlackPlugin] apps.connections.open returned error: {}", err);
                return;
            }

            String wssUrl = root.path("url").asText("");
            if (wssUrl.isBlank()) {
                this.running = false;
                updateStatus("Error: missing WebSocket URL");
                return;
            }

            WebSocket ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create(wssUrl), new SocketModeListener())
                    .get(15, TimeUnit.SECONDS);

            synchronized (this) {
                if (!desiredActive) {
                    ws.abort();
                    return;
                }
                WebSocket previous = this.activeWebSocket;
                this.activeWebSocket = ws;
                this.running = true;
                reconnectAttempts.set(0);
                if (previous != null && previous != ws) {
                    previous.abort();
                }
            }
            updateStatus("Connected");
            logger.info("[SlackPlugin] Slack Socket Mode WebSocket connected.");
        } catch (Exception e) {
            this.running = false;
            updateStatus("Error: " + e.getMessage());
            logger.warn("[SlackPlugin] Failed to open Slack Socket Mode connection: {}", e.getMessage());
            scheduleReconnectWithBackoff();
        }
    }

    /**
     * Cleanly closes the active Socket Mode WebSocket and stops any pending
     * reconnect timers.
     */
    public synchronized void stop() {
        this.desiredActive = false;
        reconnectAttempts.set(0);
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
            pendingReconnect = null;
        }
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor = null;
        }
        WebSocket ws = this.activeWebSocket;
        this.activeWebSocket = null;
        this.running = false;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin stopped").get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                ws.abort();
            }
        }
        updateStatus(this.activeConfig.enabled() ? "Disconnected" : "Disabled");
    }

    /**
     * Stops Socket Mode and closes the underlying {@link HttpClient} selector pool on plugin unload.
     */
    public void close() {
        stop();
        try {
            httpClient.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * Handles a raw JSON envelope delivered over Slack Socket Mode (also exposed
     * package-privately/publicly for testing).
     *
     * @param rawJson   envelope JSON text
     * @param webSocket optional active WebSocket on which to send
     *                  {@code {"envelope_id":"..."}} acknowledgement
     */
    public void handleSocketEnvelope(String rawJson, WebSocket webSocket) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        try {
            JsonNode envelope = objectMapper.readTree(rawJson);
            String envelopeId = envelope.path("envelope_id").asText("");
            if (!envelopeId.isBlank() && webSocket != null) {
                String ackJson = objectMapper.writeValueAsString(Map.of("envelope_id", envelopeId));
                webSocket.sendText(ackJson, true);
            }

            String type = envelope.path("type").asText("");
            if ("hello".equalsIgnoreCase(type)) {
                this.running = true;
                updateStatus("Connected");
                return;
            }
            if ("disconnect".equalsIgnoreCase(type)) {
                String reason = envelope.path("reason").asText("refresh_requested");
                logger.info("[SlackPlugin] Slack Socket Mode requested reconnect (reason={}).", reason);
                scheduleReconnect(500L);
                return;
            }
            if ("events_api".equalsIgnoreCase(type)) {
                JsonNode event = envelope.path("payload").path("event");
                dispatchSlackEvent(event);
            }
        } catch (Exception e) {
            logger.warn("[SlackPlugin] Error parsing Socket Mode envelope: {}", e.getMessage());
        }
    }

    private void dispatchSlackEvent(JsonNode event) {
        if (event == null || event.isMissingNode()) {
            return;
        }
        String eventType = event.path("type").asText("");
        if ("app_home_opened".equalsIgnoreCase(eventType)) {
            return;
        }
        if (!"message".equalsIgnoreCase(eventType) && !"app_mention".equalsIgnoreCase(eventType)) {
            return;
        }

        // Ignore bot messages and message subtypes (edits, joins, bot_message) to
        // prevent reply loops
        String botId = event.path("bot_id").asText("");
        String subtype = event.path("subtype").asText("");
        if (!botId.isBlank()) {
            return;
        }
        if ("message".equalsIgnoreCase(eventType) && !subtype.isBlank()) {
            return;
        }

        String userId = event.path("user").asText("");
        if (userId.isBlank()) {
            return;
        }
        if (!activeConfig.isUserAllowed(userId)) {
            logger.warn("[SlackPlugin] Unauthorized Slack {} from user '{}'. Allowed users: {}",
                    eventType, userId, activeConfig.allowedUsers());
            return;
        }

        String rawText = event.path("text").asText("");
        String prompt = cleanMessageText(rawText);
        if (prompt.isEmpty()) {
            return;
        }

        String targetStaff = extractTargetStaff(prompt);
        String channelId = event.path("channel").asText("");
        String ts = event.path("ts").asText("");
        String threadTs = event.hasNonNull("thread_ts") && !event.path("thread_ts").asText("").isBlank()
                ? event.path("thread_ts").asText()
                : ts;
        String sessionId = "slack-" + channelId + "-" + threadTs;

        logger.info("[SlackPlugin] Processing Slack {} from user '{}' in channel '{}' (thread: {}, staff: {}): '{}'",
                eventType, userId, channelId, threadTs, targetStaff != null ? targetStaff : "default", prompt);

        Thread.ofVirtual().name("slack-plugin-msg-" + userId).start(() -> {
            try {
                if (pluginContext == null) {
                    return;
                }
                List<PluginChatTurn> turns = pluginContext.submitChatQuery(sessionId, prompt, null, targetStaff, null);
                if (turns != null && !turns.isEmpty()) {
                    StringBuilder combined = new StringBuilder();
                    boolean multiTurn = turns.size() > 1;
                    for (PluginChatTurn turn : turns) {
                        if (turn == null || turn.text() == null || turn.text().isBlank()) {
                            continue;
                        }
                        if (!combined.isEmpty()) {
                            combined.append("\n\n");
                        }
                        if (multiTurn && turn.staffName() != null && !turn.staffName().isBlank()) {
                            combined.append("**[").append(turn.staffName()).append("]**\n");
                        }
                        combined.append(turn.text().trim());
                    }
                    if (!combined.isEmpty()) {
                        String mrkdwn = markdownConverter.toSlackMrkdwn(combined.toString());
                        postChatMessage(channelId, threadTs, mrkdwn);
                    }
                }
            } catch (Exception e) {
                logger.error("[SlackPlugin] Error executing query for Slack user '{}': {}", userId, e.getMessage(), e);
                try {
                    postChatMessage(channelId, threadTs, "⚠️ *Nadia Encountered an Error:* " + e.getMessage());
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * Posts a message to a Slack channel (and optional thread) via
     * {@code chat.postMessage}.
     *
     * @param channel  Slack channel ID or name
     * @param threadTs optional thread timestamp ({@code null} or blank for
     *                 top-level channel post)
     * @param mrkdwn   formatted Slack {@code mrkdwn} text
     * @return parsed Slack JSON response node
     * @throws Exception if the HTTP call fails
     */
    public JsonNode postChatMessage(String channel, String threadTs, String mrkdwn) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("text", mrkdwn);
        if (threadTs != null && !threadTs.isBlank()) {
            body.put("thread_ts", threadTs);
        }
        String payload = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/chat.postMessage"))
                .header("Authorization", "Bearer " + activeConfig.botToken())
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(resp.body());
    }

    /**
     * Publishes a Markdown notification to the specified Slack channel (or
     * configured default notification channel).
     *
     * @param message         Markdown message content
     * @param channelOverride optional channel override
     * @return JSON status summary compatible with {@code sendSlackNotification}
     */
    public String sendNotification(String message, String channelOverride) {
        SlackConfig cfg = this.activeConfig;
        if (!cfg.enabled()) {
            return toJson(Map.of(
                    "status", "ERROR",
                    "message", "Slack integration is disabled in configuration/settings."));
        }
        if (cfg.botToken().isBlank()) {
            return toJson(Map.of(
                    "status", "ERROR",
                    "message", "Slack bot token (xoxb-...) is not configured."));
        }
        String targetChannel = (channelOverride != null && !channelOverride.isBlank())
                ? channelOverride.trim()
                : cfg.notificationChannel();
        if (targetChannel == null || targetChannel.isBlank()) {
            return toJson(Map.of(
                    "status", "ERROR",
                    "message",
                    "No Slack notification channel is designated. Please configure a default notification channel in Settings or provide a channel parameter."));
        }
        if (message == null || message.isBlank()) {
            return toJson(Map.of(
                    "status", "ERROR",
                    "message", "Notification message content cannot be empty."));
        }

        try {
            String formattedText = markdownConverter.toSlackMrkdwn(message);
            JsonNode response = postChatMessage(targetChannel, null, formattedText);
            if (response.path("ok").asBoolean(false)) {
                String ts = response.path("ts").asText("");
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("status", "SUCCESS");
                ok.put("channel", targetChannel);
                ok.put("ts", ts);
                ok.put("message", "Notification sent successfully to " + targetChannel);
                return toJson(ok);
            } else {
                String err = response.path("error").asText("unknown_error");
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("status", "ERROR");
                fail.put("channel", targetChannel);
                fail.put("error", err);
                return toJson(fail);
            }
        } catch (Exception e) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("status", "ERROR");
            fail.put("channel", targetChannel);
            fail.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return toJson(fail);
        }
    }

    /**
     * Returns a JSON object describing the current Slack connection state and
     * default notification channel.
     *
     * @return JSON status string
     */
    public String getNotificationChannelInfo() {
        SlackConfig cfg = this.activeConfig;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("enabled", cfg.enabled());
        info.put("status", connectionStatus);
        info.put("notificationChannel", cfg.notificationChannel());
        return toJson(info);
    }

    /**
     * Strips Slack {@code <@U...>} user mention tokens and trims surrounding
     * whitespace.
     *
     * @param text raw Slack message text
     * @return cleaned prompt string
     */
    public String cleanMessageText(String text) {
        if (text == null) {
            return "";
        }
        return MENTION_TAG_PATTERN.matcher(text).replaceAll("").trim();
    }

    /**
     * Resolves any {@code @Staff} or {@code ask <Staff>} target persona mentioned
     * in the prompt using
     * {@link NadiaPluginContext#getAvailableStaff()}.
     *
     * @param prompt cleaned prompt text
     * @return canonical staff name, or {@code null} if none matched
     */
    public String extractTargetStaff(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        List<String> staffNames = new ArrayList<>();
        if (pluginContext != null) {
            for (PluginStaffDescriptor desc : pluginContext.getAvailableStaff()) {
                if (desc != null && desc.name() != null && !desc.name().isBlank()) {
                    staffNames.add(desc.name().trim());
                }
            }
        }
        if (!staffNames.contains("Nadia")) {
            staffNames.add("Nadia");
        }
        staffNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

        String lower = prompt.toLowerCase();
        for (String staff : staffNames) {
            String lowerStaff = staff.toLowerCase();
            String noSpaceStaff = WHITESPACE_PATTERN.matcher(lowerStaff).replaceAll("");
            String[] prefixes = {
                    "ask @" + lowerStaff,
                    "ask " + lowerStaff,
                    "@" + lowerStaff,
                    "ask @" + noSpaceStaff,
                    "ask " + noSpaceStaff,
                    "@" + noSpaceStaff
            };
            for (String prefix : prefixes) {
                int idx = lower.indexOf(prefix);
                while (idx >= 0) {
                    boolean validBefore = (idx == 0) || !Character.isLetterOrDigit(lower.charAt(idx - 1));
                    int afterIdx = idx + prefix.length();
                    boolean validAfter = (afterIdx >= lower.length())
                            || Character.isWhitespace(lower.charAt(afterIdx))
                            || lower.charAt(afterIdx) == ':' || lower.charAt(afterIdx) == ','
                            || lower.charAt(afterIdx) == '.' || lower.charAt(afterIdx) == '!'
                            || lower.charAt(afterIdx) == '?' || lower.charAt(afterIdx) == '-'
                            || lower.charAt(afterIdx) == ';';
                    if (validBefore && validAfter) {
                        return staff;
                    }
                    idx = lower.indexOf(prefix, idx + 1);
                }
            }
        }
        return null;
    }

    /**
     * Returns whether the Socket Mode WebSocket is currently connected.
     *
     * @return {@code true} if connected
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the current human-readable connection status.
     *
     * @return status string (e.g. {@code Connected}, {@code Disabled},
     *         {@code Missing Tokens})
     */
    public String getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * Returns the active {@link SlackConfig}.
     *
     * @return active Slack configuration
     */
    public SlackConfig getActiveConfig() {
        return activeConfig;
    }

    private synchronized void ensureReconnectExecutor() {
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
                    r -> Thread.ofPlatform().daemon(true).name("slack-plugin-reconnect").unstarted(r));
        }
    }

    private void scheduleReconnectWithBackoff() {
        int attempt = Math.min(reconnectAttempts.getAndIncrement(), 6);
        long baseDelay = Math.min(MAX_RECONNECT_BACKOFF_MS, 2000L << attempt);
        long jitter = ThreadLocalRandom.current().nextLong(250L, 1250L);
        scheduleReconnect(Math.min(MAX_RECONNECT_BACKOFF_MS, baseDelay + jitter));
    }

    private synchronized void scheduleReconnect(long delayMs) {
        if (!desiredActive || reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            return;
        }
        if (pendingReconnect != null && !pendingReconnect.isDone()) {
            return;
        }
        pendingReconnect = reconnectExecutor.schedule(() -> {
            WebSocket prev = this.activeWebSocket;
            this.activeWebSocket = null;
            this.running = false;
            if (prev != null) {
                try {
                    prev.abort();
                } catch (Exception ignored) {
                }
            }
            connectSocketModeOnce();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void updateStatus(String nextStatus) {
        this.connectionStatus = nextStatus;
        if (statusChangeCallback != null) {
            try {
                statusChangeCallback.run();
            } catch (Exception ignored) {
            }
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\"}";
        }
    }

    private final class SocketModeListener implements WebSocket.Listener {
        private StringBuilder frameBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frameBuffer.append(data);
            if (last) {
                String json = frameBuffer.toString();
                if (frameBuffer.capacity() > MAX_FRAME_BUFFER_CAPACITY) {
                    frameBuffer = new StringBuilder();
                } else {
                    frameBuffer.setLength(0);
                }
                handleSocketEnvelope(json, webSocket);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            running = false;
            if (desiredActive) {
                updateStatus("Reconnecting...");
                scheduleReconnectWithBackoff();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            running = false;
            if (desiredActive) {
                updateStatus("Error: " + (error != null ? error.getMessage() : "socket error"));
                scheduleReconnectWithBackoff();
            }
        }
    }
}
