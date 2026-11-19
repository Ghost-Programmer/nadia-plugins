package com.nadia.plugin.web.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.PluginChatTurn;
import com.nadia.app.plugin.api.PluginStaffDescriptor;
import com.nadia.plugin.web.config.WebInterfaceConfig;
import com.nadia.plugin.web.service.WebChatSessionStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded Virtual-Thread HTTP &amp; Server-Sent Events (SSE) server powering Nadia's browser-based
 * Web Chat Interface.
 *
 * <p>Implements full functional parity with Nadia's desktop {@code ChatWindowView}, including
 * multi-session tabs ({@code ➕ New Chat}), {@code @Staff} mention autocomplete and routing,
 * drag-and-drop file attachments, Markdown/Mermaid/table/code rendering, RAG vector ingestion,
 * and live background scheduled cron job streaming.</p>
 */
public class WebInterfaceServer {

    private static final Logger logger = LoggerFactory.getLogger(WebInterfaceServer.class);
    private static final java.util.regex.Pattern WHITESPACE_PATTERN = java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern UNSAFE_FILENAME_PATTERN = java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final byte[] SSE_HEARTBEAT_BYTES = ": keepalive\n\n".getBytes(StandardCharsets.UTF_8);
    private static final long UPLOAD_TTL_MS = 30L * 60L * 1000L;

    private final NadiaPluginContext pluginContext;
    private final WebChatSessionStore sessionStore;
    private final Path webAssetsDir;
    private final Path uploadsDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record StagedAttachment(Path path, long uploadedAtMs) {}
    private record CachedStaticAsset(long lastModifiedMs, String etag, byte[] rawBytes, byte[] gzipBytes) {}

    private final Map<String, StagedAttachment> uploadedAttachments = new ConcurrentHashMap<>();
    private final Map<String, CachedStaticAsset> staticAssetCache = new ConcurrentHashMap<>();
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();

    private volatile WebInterfaceConfig activeConfig = WebInterfaceConfig.defaults();
    private volatile HttpServer httpServer;
    private volatile ExecutorService requestExecutor;
    private volatile ScheduledExecutorService heartbeatScheduler;
    private volatile int actualBoundPort = -1;
    private volatile String lastErrorMessage = "";

    private static final class SseClient {
        final HttpExchange exchange;
        final OutputStream outputStream;
        final AtomicBoolean closed = new AtomicBoolean(false);

        SseClient(HttpExchange exchange, OutputStream outputStream) {
            this.exchange = exchange;
            this.outputStream = outputStream;
        }

        synchronized boolean sendRawBytes(byte[] frameBytes) {
            if (closed.get()) {
                return false;
            }
            try {
                outputStream.write(frameBytes);
                outputStream.flush();
                return true;
            } catch (IOException e) {
                closeQuietly();
                return false;
            }
        }

        boolean sendEvent(String eventType, String jsonData) {
            String frame = "event: " + eventType + "\ndata: " + jsonData + "\n\n";
            return sendRawBytes(frame.getBytes(StandardCharsets.UTF_8));
        }

        boolean sendHeartbeat() {
            return sendRawBytes(SSE_HEARTBEAT_BYTES);
        }

        void closeQuietly() {
            if (closed.compareAndSet(false, true)) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
                try {
                    exchange.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Constructs a {@code WebInterfaceServer}.
     *
     * @param pluginContext host {@link NadiaPluginContext}
     * @param sessionStore  shared {@link WebChatSessionStore}
     * @param webAssetsDir  directory containing extracted static HTML/CSS/JS assets
     * @param uploadsDir    directory used to stage browser file uploads
     */
    public WebInterfaceServer(
            NadiaPluginContext pluginContext,
            WebChatSessionStore sessionStore,
            Path webAssetsDir,
            Path uploadsDir) {
        this.pluginContext = pluginContext;
        this.sessionStore = sessionStore;
        this.webAssetsDir = webAssetsDir;
        this.uploadsDir = uploadsDir;
    }

    /**
     * Starts the HTTP server using the supplied {@link WebInterfaceConfig}.
     * If already running, stops the existing listener before rebinding.
     *
     * @param config configuration specifying host, port, auth token, and upload settings
     * @return {@code true} if the server bound and started successfully
     */
    public synchronized boolean start(WebInterfaceConfig config) {
        stop();
        this.activeConfig = config != null ? config : WebInterfaceConfig.defaults();
        this.lastErrorMessage = "";

        try {
            Files.createDirectories(uploadsDir);
            InetSocketAddress bindAddr = new InetSocketAddress(activeConfig.host(), activeConfig.port());
            HttpServer srv;
            if (activeConfig.tlsEnabled()) {
                Path dataDir = webAssetsDir.getParent() != null ? webAssetsDir.getParent() : webAssetsDir;
                SSLContext sslContext = TlsKeystoreManager.buildSslContext(activeConfig, dataDir);
                HttpsServer httpsSrv = HttpsServer.create(bindAddr, 0);
                httpsSrv.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    @Override
                    public void configure(HttpsParameters params) {
                        SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
                        sslParams.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                        params.setSSLParameters(sslParams);
                    }
                });
                srv = httpsSrv;
            } else {
                srv = HttpServer.create(bindAddr, 0);
            }
            ExecutorService vte = Executors.newVirtualThreadPerTaskExecutor();
            srv.setExecutor(vte);

            srv.createContext("/api/config", this::handleConfig);
            srv.createContext("/api/staff", this::handleStaff);
            srv.createContext("/api/sessions", this::handleSessions);
            srv.createContext("/api/upload", this::handleUpload);
            srv.createContext("/api/chat/send", this::handleChatSend);
            srv.createContext("/api/chat/rag", this::handleSendToRag);
            srv.createContext("/api/events", this::handleSseEvents);
            srv.createContext("/", this::handleStaticAsset);

            srv.start();
            this.httpServer = srv;
            this.requestExecutor = vte;
            this.actualBoundPort = srv.getAddress().getPort();

            ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = Thread.ofPlatform().daemon(true).name("nadia-web-sse-heartbeat").unstarted(r);
                return t;
            });
            hb.scheduleAtFixedRate(this::pruneAndHeartbeatClients, 20, 20, TimeUnit.SECONDS);
            this.heartbeatScheduler = hb;

            logger.info("[WebInterface] {} chat interface listening at {}",
                    activeConfig.scheme().toUpperCase(), getPrimaryUrl());
            return true;
        } catch (Exception e) {
            this.lastErrorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.error("[WebInterface] Failed to bind {} server on {}:{}: {}",
                    activeConfig.scheme().toUpperCase(), activeConfig.host(), activeConfig.port(), lastErrorMessage, e);
            stop();
            return false;
        }
    }

    /**
     * Stops the HTTP server, closes all active SSE streams, and releases the bound socket immediately.
     */
    public synchronized void stop() {
        for (SseClient client : sseClients) {
            client.closeQuietly();
        }
        sseClients.clear();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            } catch (Exception ignored) {
            }
            httpServer = null;
        }
        if (requestExecutor != null) {
            requestExecutor.shutdownNow();
            requestExecutor = null;
        }
        actualBoundPort = -1;
    }

    /**
     * Updates the active configuration without rebinding if host and port did not change,
     * or restarts the HTTP listener if bind settings changed.
     *
     * @param newConfig updated configuration
     * @return {@code true} if the server is running (or intentionally disabled) without error
     */
    public synchronized boolean applyConfiguration(WebInterfaceConfig newConfig) {
        WebInterfaceConfig previous = this.activeConfig;
        this.activeConfig = newConfig != null ? newConfig : WebInterfaceConfig.defaults();

        if (!this.activeConfig.enabled()) {
            stop();
            this.lastErrorMessage = "";
            return true;
        }

        boolean needsRebind = !isRunning() || this.activeConfig.requiresListenerRebind(previous);

        if (needsRebind) {
            return start(this.activeConfig);
        }

        broadcastEvent("config_updated", Map.of(
                "title", this.activeConfig.pageTitle(),
                "protocol", this.activeConfig.scheme(),
                "allowUploads", this.activeConfig.allowUploads(),
                "maxUploadMb", this.activeConfig.maxUploadMb(),
                "enableCronStream", this.activeConfig.enableCronStream()
        ));
        return true;
    }

    /**
     * Returns whether the embedded HTTP server is currently bound and listening.
     *
     * @return {@code true} when running
     */
    public boolean isRunning() {
        return httpServer != null && actualBoundPort > 0;
    }

    /**
     * Returns the actual TCP port bound by the server, or {@code -1} when stopped.
     *
     * @return bound TCP port or {@code -1}
     */
    public int getActualBoundPort() {
        return actualBoundPort;
    }

    /**
     * Returns the active {@link WebInterfaceConfig}.
     *
     * @return current configuration
     */
    public WebInterfaceConfig getActiveConfig() {
        return activeConfig;
    }

    /**
     * Returns the most recent startup/bind error message, or empty string if healthy.
     *
     * @return last error description
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * Returns the number of currently connected SSE browser tabs.
     *
     * @return connected client count
     */
    public int getConnectedClientCount() {
        return sseClients.size();
    }

    /**
     * Returns the primary browser URL for accessing the Web Interface locally.
     *
     * @return URL string (e.g., {@code http://127.0.0.1:8088})
     */
    public String getPrimaryUrl() {
        int port = actualBoundPort > 0 ? actualBoundPort : activeConfig.port();
        String host = "0.0.0.0".equals(activeConfig.host()) ? "127.0.0.1" : activeConfig.host();
        return activeConfig.scheme() + "://" + host + ":" + port;
    }

    /**
     * Resolves non-loopback IPv4 LAN URLs when bound to {@code 0.0.0.0}.
     *
     * @return list of LAN URLs reachable from other devices on the network
     */
    public List<String> getLanUrls() {
        int port = actualBoundPort > 0 ? actualBoundPort : activeConfig.port();
        if (!"0.0.0.0".equals(activeConfig.host())) {
            return List.of();
        }
        String scheme = activeConfig.scheme();
        List<String> urls = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets != null && nets.hasMoreElements()) {
                NetworkInterface nif = nets.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        urls.add(scheme + "://" + addr.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return urls;
    }

    /**
     * Publishes a completed background scheduled cron prompt and assistant reply to the
     * {@code 🕒 Scheduled Tasks} session and all connected SSE clients.
     *
     * @param jobSessionId originating scheduled job session identifier
     * @param prompt       executed scheduled prompt
     * @param reply        assistant Markdown reply
     */
    public void publishScheduledJobMessage(String jobSessionId, String prompt, String reply) {
        if (!activeConfig.enableCronStream()) {
            return;
        }
        String label = (jobSessionId != null && !jobSessionId.isBlank()) ? jobSessionId : "cron";
        WebChatSessionStore.ChatMessage promptMsg = sessionStore.appendMessage(
                WebChatSessionStore.CRON_SESSION_ID,
                true,
                "Scheduled Job",
                "🕒 **[Scheduled Job - " + label + "]**\n" + (prompt != null ? prompt : ""),
                List.of()
        );
        WebChatSessionStore.ChatMessage replyMsg = sessionStore.appendMessage(
                WebChatSessionStore.CRON_SESSION_ID,
                false,
                "Nadia",
                reply != null ? reply : "",
                List.of()
        );
        broadcastEvent("message_added", promptMsg);
        broadcastEvent("message_added", replyMsg);
    }

    // -------------------------------------------------------------------------
    // HTTP Handlers
    // -------------------------------------------------------------------------

    private void handleStaticAsset(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/index.html";
        }

        String cleanRel = path.startsWith("/") ? path.substring(1) : path;
        if (cleanRel.contains("..") || cleanRel.contains("\\")) {
            sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        Path targetFile = webAssetsDir.resolve(cleanRel).normalize();
        if (!targetFile.startsWith(webAssetsDir.normalize()) || !Files.isRegularFile(targetFile)) {
            if ("favicon.ico".equals(cleanRel)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            sendJson(exchange, 404, Map.of("error", "Not found"));
            return;
        }

        long lastMod = Files.getLastModifiedTime(targetFile).toMillis();
        CachedStaticAsset cached = staticAssetCache.compute(cleanRel, (k, existing) -> {
            if (existing != null && existing.lastModifiedMs() == lastMod) {
                return existing;
            }
            try {
                byte[] raw = Files.readAllBytes(targetFile);
                byte[] gz = gzipBytes(raw);
                String etag = "\"" + lastMod + "-" + raw.length + "\"";
                return new CachedStaticAsset(lastMod, etag, raw, gz);
            } catch (IOException e) {
                return existing;
            }
        });
        if (cached == null) {
            sendJson(exchange, 500, Map.of("error", "Failed to read asset"));
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", guessContentType(cleanRel));
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, must-revalidate");
        exchange.getResponseHeaders().set("ETag", cached.etag());
        exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (cached.etag().equals(ifNoneMatch)) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }

        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        boolean useGzip = acceptEncoding != null && acceptEncoding.toLowerCase().contains("gzip")
                && cached.gzipBytes() != null && cached.gzipBytes().length < cached.rawBytes().length;
        byte[] payload = useGzip ? cached.gzipBytes() : cached.rawBytes();
        if (useGzip) {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        }

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
        exchange.close();
    }

    private static byte[] gzipBytes(byte[] raw) {
        if (raw == null || raw.length < 512) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(raw.length / 2);
             java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(baos)) {
            gz.write(raw);
            gz.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        WebInterfaceConfig cfg = this.activeConfig;
        boolean authed = isAuthorized(exchange);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("title", cfg.pageTitle());
        resp.put("protocol", cfg.scheme());
        resp.put("authRequired", cfg.isAuthRequired());
        resp.put("authenticated", authed);
        resp.put("allowUploads", cfg.allowUploads());
        resp.put("maxUploadMb", cfg.maxUploadMb());
        resp.put("enableCronStream", cfg.enableCronStream());
        sendJson(exchange, 200, resp);
    }

    private void handleStaff(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        List<PluginStaffDescriptor> staff = pluginContext != null
                ? pluginContext.getAvailableStaff()
                : List.of(new PluginStaffDescriptor("Nadia", "🤖", "General Cognitive AI Assistant"));
        sendJson(exchange, 200, Map.of("staff", staff));
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) {
            return;
        }
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();
        String suffix = path.length() > "/api/sessions".length()
                ? path.substring("/api/sessions".length())
                : "";

        if (suffix.isEmpty() || "/".equals(suffix)) {
            if ("GET".equals(method)) {
                sendJson(exchange, 200, Map.of(
                        "sessions", sessionStore.listSessions(activeConfig.enableCronStream())
                ));
                return;
            }
            if ("POST".equals(method)) {
                JsonNode body = readJsonBody(exchange);
                String title = body != null ? body.path("title").asText("") : "";
                WebChatSessionStore.SessionSummary created = sessionStore.createSession(title);
                broadcastEvent("sessions_updated", Map.of(
                        "sessions", sessionStore.listSessions(activeConfig.enableCronStream())
                ));
                sendJson(exchange, 201, Map.of("session", created));
                return;
            }
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String trimmed = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (trimmed.endsWith("/clear") && "POST".equals(method)) {
            String sessionId = URLDecoder.decode(trimmed.substring(0, trimmed.length() - "/clear".length()), StandardCharsets.UTF_8);
            List<WebChatSessionStore.ChatMessage> cleared = sessionStore.clearSession(sessionId);
            broadcastEvent("session_cleared", Map.of("sessionId", sessionId, "messages", cleared));
            sendJson(exchange, 200, Map.of("sessionId", sessionId, "messages", cleared));
            return;
        }

        String sessionId = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
        if ("GET".equals(method)) {
            List<WebChatSessionStore.ChatMessage> messages = sessionStore.getMessages(sessionId);
            WebChatSessionStore.SessionSummary summary = sessionStore.getSessionSummary(sessionId)
                    .orElse(new WebChatSessionStore.SessionSummary(sessionId, sessionId, false, false, messages.size()));
            sendJson(exchange, 200, Map.of("session", summary, "messages", messages));
            return;
        }
        if ("DELETE".equals(method)) {
            boolean deleted = sessionStore.deleteSession(sessionId);
            if (!deleted) {
                sendJson(exchange, 400, Map.of("error", "Cannot delete primary or Scheduled Tasks session."));
                return;
            }
            broadcastEvent("sessions_updated", Map.of(
                    "sessions", sessionStore.listSessions(activeConfig.enableCronStream())
            ));
            sendJson(exchange, 200, Map.of("deleted", true, "sessionId", sessionId));
            return;
        }

        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        if (!activeConfig.allowUploads()) {
            sendJson(exchange, 403, Map.of("error", "File uploads are disabled in Web Interface Settings."));
            return;
        }

        String rawName = exchange.getRequestHeaders().getFirst("X-File-Name");
        String decodedName = (rawName != null && !rawName.isBlank())
                ? URLDecoder.decode(rawName, StandardCharsets.UTF_8)
                : "attachment.bin";
        String safeFileName = sanitizeFileName(decodedName);

        long maxBytes = activeConfig.maxUploadMb() * 1024L * 1024L;
        String attachmentId = "att-" + UUID.randomUUID().toString();
        Path subDir = uploadsDir.resolve(attachmentId);
        Files.createDirectories(subDir);
        Path savedFile = subDir.resolve(safeFileName);
        long writtenBytes;
        try {
            writtenBytes = streamToFileWithLimit(exchange.getRequestBody(), savedFile, maxBytes);
        } catch (IllegalArgumentException oversize) {
            deleteUploadQuietly(savedFile);
            sendJson(exchange, 413, Map.of("error", oversize.getMessage()));
            return;
        } catch (IOException ioEx) {
            deleteUploadQuietly(savedFile);
            throw ioEx;
        }
        uploadedAttachments.put(attachmentId, new StagedAttachment(savedFile, System.currentTimeMillis()));

        sendJson(exchange, 200, Map.of(
                "attachmentId", attachmentId,
                "fileName", safeFileName,
                "sizeBytes", writtenBytes
        ));
    }

    private void handleChatSend(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        JsonNode body = readJsonBody(exchange);
        if (body == null) {
            sendJson(exchange, 400, Map.of("error", "Invalid JSON request body."));
            return;
        }

        String sessionId = body.path("sessionId").asText("").trim();
        String rawText = body.path("text").asText("").trim();
        List<File> attachedFiles = new ArrayList<>();
        List<String> attachedNames = new ArrayList<>();

        JsonNode attArray = body.get("attachmentIds");
        if (attArray != null && attArray.isArray()) {
            for (JsonNode idNode : attArray) {
                StagedAttachment staged = uploadedAttachments.remove(idNode.asText(""));
                Path p = staged != null ? staged.path() : null;
                if (p != null && Files.isRegularFile(p)) {
                    File f = p.toFile();
                    attachedFiles.add(f);
                    attachedNames.add(f.getName());
                }
            }
        }

        if (rawText.isEmpty() && attachedFiles.isEmpty()) {
            sendJson(exchange, 400, Map.of("error", "Message text and attachments cannot both be empty."));
            return;
        }

        if (sessionId.isEmpty()) {
            sessionId = sessionStore.listSessions(true).get(0).id();
        }

        List<PluginStaffDescriptor> allStaff = pluginContext != null
                ? pluginContext.getAvailableStaff()
                : List.of(new PluginStaffDescriptor("Nadia", "🤖", "General Cognitive AI Assistant"));
        String targetStaff = extractTargetStaff(rawText, allStaff);
        String compiledText = compilePromptWithAttachments(rawText, attachedFiles);

        StringBuilder uiDisplay = new StringBuilder();
        uiDisplay.append(!rawText.isEmpty() ? rawText : "[Sent files only]");
        if (!attachedNames.isEmpty()) {
            uiDisplay.append("\n\nAttachments:");
            for (String fn : attachedNames) {
                uiDisplay.append("\n📎 ").append(fn);
            }
        }

        WebChatSessionStore.ChatMessage userBubble = sessionStore.appendMessage(
                sessionId,
                true,
                "User",
                uiDisplay.toString(),
                attachedNames
        );
        sessionStore.setThinking(sessionId, true);
        broadcastEvent("message_added", userBubble);
        broadcastEvent("thinking_changed", Map.of("sessionId", sessionId, "thinking", true));

        final String targetSessionId = sessionId;
        ExecutorService exec = this.requestExecutor;
        if (exec != null && !exec.isShutdown()) {
            exec.submit(() -> executeOrchestratorTurnAsync(targetSessionId, compiledText, attachedFiles, targetStaff, allStaff));
        }

        sendJson(exchange, 202, Map.of(
                "accepted", true,
                "sessionId", sessionId,
                "userMessage", userBubble
        ));
    }

    private void executeOrchestratorTurnAsync(
            String sessionId,
            String compiledText,
            List<File> attachedFiles,
            String targetStaff,
            List<PluginStaffDescriptor> allStaff) {
        AtomicBoolean emittedAny = new AtomicBoolean(false);
        try {
            if (pluginContext == null) {
                throw new IllegalStateException("Plugin context is not initialized.");
            }
            List<PluginChatTurn> turns = pluginContext.submitChatQuery(
                    sessionId,
                    compiledText,
                    attachedFiles,
                    targetStaff,
                    (respondingStaff, replyMarkdown) -> {
                        emittedAny.set(true);
                        String formatted = formatStaffResponse(respondingStaff, replyMarkdown, allStaff);
                        WebChatSessionStore.ChatMessage botMsg = sessionStore.appendMessage(
                                sessionId,
                                false,
                                respondingStaff,
                                formatted,
                                List.of()
                        );
                        broadcastEvent("message_added", botMsg);
                    }
            );

            if (!emittedAny.get()) {
                if (turns != null && !turns.isEmpty()) {
                    for (PluginChatTurn t : turns) {
                        String formatted = formatStaffResponse(t.staffName(), t.text(), allStaff);
                        WebChatSessionStore.ChatMessage botMsg = sessionStore.appendMessage(
                                sessionId,
                                false,
                                t.staffName(),
                                formatted,
                                List.of()
                        );
                        broadcastEvent("message_added", botMsg);
                    }
                } else {
                    WebChatSessionStore.ChatMessage emptyMsg = sessionStore.appendMessage(
                            sessionId,
                            false,
                            "Nadia",
                            "*(No responses received)*",
                            List.of()
                    );
                    broadcastEvent("message_added", emptyMsg);
                }
            }
        } catch (Exception e) {
            logger.error("[WebInterface] Error executing chat query in session '{}': {}", sessionId, e.getMessage(), e);
            WebChatSessionStore.ChatMessage errMsg = sessionStore.appendMessage(
                    sessionId,
                    false,
                    "System",
                    formatErrorMessage(e),
                    List.of()
            );
            broadcastEvent("message_added", errMsg);
        } finally {
            sessionStore.setThinking(sessionId, false);
            broadcastEvent("thinking_changed", Map.of("sessionId", sessionId, "thinking", false));
            if (attachedFiles != null) {
                for (File f : attachedFiles) {
                    if (f != null) {
                        deleteUploadQuietly(f.toPath());
                    }
                }
            }
        }
    }

    private void handleSendToRag(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        JsonNode body = readJsonBody(exchange);
        String markdown = body != null ? body.path("markdown").asText("") : "";
        if (markdown.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "error", "Markdown content cannot be empty."));
            return;
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("file_name", "web_chat_response_" + System.currentTimeMillis() + ".md");
        meta.put("type", "rag_ingested_file");
        meta.put("source", "web-interface");
        meta.put("created_at", System.currentTimeMillis());

        boolean ok = pluginContext != null && pluginContext.ingestDocumentIntoRag(markdown, meta);
        if (ok) {
            sendJson(exchange, 200, Map.of("success", true, "message", "Successfully ingested message into Nadia RAG vector database."));
        } else {
            sendJson(exchange, 503, Map.of("success", false, "error", "RAG Vector Store is not available in the current Nadia context."));
        }
    }

    private void handleSseEvents(HttpExchange exchange) throws IOException {
        if (!requireAuth(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        SseClient client = new SseClient(exchange, os);
        sseClients.add(client);

        client.sendEvent("connected", objectMapper.writeValueAsString(Map.of("status", "ok")));
    }

    // -------------------------------------------------------------------------
    // Helpers (Auth, SSE Broadcast, Prompt & Staff Formatting)
    // -------------------------------------------------------------------------

    private void broadcastEvent(String eventType, Object payload) {
        if (sseClients.isEmpty()) {
            return;
        }
        byte[] frameBytes;
        try {
            String json = objectMapper.writeValueAsString(payload);
            frameBytes = ("event: " + eventType + "\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return;
        }
        for (SseClient client : sseClients) {
            Thread.ofVirtual().name("web-sse-send").start(() -> {
                if (!client.sendRawBytes(frameBytes)) {
                    sseClients.remove(client);
                }
            });
        }
    }

    private void pruneAndHeartbeatClients() {
        for (SseClient client : sseClients) {
            Thread.ofVirtual().name("web-sse-hb").start(() -> {
                if (!client.sendHeartbeat()) {
                    sseClients.remove(client);
                }
            });
        }
        long cutoff = System.currentTimeMillis() - UPLOAD_TTL_MS;
        uploadedAttachments.entrySet().removeIf(entry -> {
            if (entry.getValue().uploadedAtMs() < cutoff) {
                deleteUploadQuietly(entry.getValue().path());
                return true;
            }
            return false;
        });
    }

    private boolean requireAuth(HttpExchange exchange) throws IOException {
        if (isAuthorized(exchange)) {
            return true;
        }
        sendJson(exchange, 401, Map.of("error", "Unauthorized: valid access token required."));
        return false;
    }

    private boolean isAuthorized(HttpExchange exchange) {
        WebInterfaceConfig cfg = this.activeConfig;
        if (!cfg.isAuthRequired()) {
            return true;
        }
        String expected = cfg.authToken();
        String candidate = exchange.getRequestHeaders().getFirst("X-Nadia-Auth");
        if (candidate == null || candidate.isBlank()) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                candidate = authHeader.substring(7).trim();
            }
        }
        if (candidate == null || candidate.isBlank()) {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0 && "token".equals(pair.substring(0, eq))) {
                        candidate = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
        }
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidate.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Extracts a targeted {@code @Staff} persona from prompt text using the same matching rules as
     * Nadia's desktop {@code ChatWindowView.extractTargetStaff}.
     *
     * @param text     raw user prompt
     * @param allStaff available staff list
     * @return matched canonical staff name, or {@code null} if untagged
     */
    public static String extractTargetStaff(String text, List<PluginStaffDescriptor> allStaff) {
        if (text == null || text.isBlank() || allStaff == null || allStaff.isEmpty()) {
            return null;
        }
        List<PluginStaffDescriptor> sorted = new ArrayList<>(allStaff);
        sorted.sort((a, b) -> Integer.compare(b.name().length(), a.name().length()));

        String lower = text.toLowerCase();
        for (PluginStaffDescriptor staff : sorted) {
            String staffName = staff.name();
            String lowerName = staffName.toLowerCase();
            String noSpaceName = WHITESPACE_PATTERN.matcher(lowerName).replaceAll("");

            String[] targets = {
                    "ask @" + lowerName,
                    "ask " + lowerName,
                    "@" + lowerName,
                    "ask @" + noSpaceName,
                    "ask " + noSpaceName,
                    "@" + noSpaceName
            };
            for (String target : targets) {
                int idx = lower.indexOf(target);
                while (idx >= 0) {
                    boolean validBefore = (idx == 0) || !Character.isLetterOrDigit(lower.charAt(idx - 1));
                    int after = idx + target.length();
                    boolean validAfter = (after >= lower.length())
                            || Character.isWhitespace(lower.charAt(after))
                            || ":,.!?-;".indexOf(lower.charAt(after)) >= 0;
                    if (validBefore && validAfter) {
                        return staffName;
                    }
                    idx = lower.indexOf(target, idx + 1);
                }
            }
        }
        return null;
    }

    private static String compilePromptWithAttachments(String text, List<File> attachedFiles) {
        StringBuilder sb = new StringBuilder();
        if (text != null && !text.isEmpty()) {
            sb.append(text);
        }
        if (attachedFiles != null && !attachedFiles.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("--- Attached Files ---");
            for (File file : attachedFiles) {
                sb.append("\n\n📄 **").append(file.getName()).append("**");
                if (isTextFile(file)) {
                    sb.append("\n```\n").append(readTextFileSafe(file)).append("\n```");
                } else {
                    sb.append(" (Binary/Omitted, size: ").append(Math.max(1, file.length() / 1024)).append(" KB)");
                }
            }
        }
        return sb.toString();
    }

    private static String formatStaffResponse(String staffName, String text, List<PluginStaffDescriptor> staffList) {
        String effective = (staffName == null || staffName.isBlank()) ? "Nadia" : staffName;
        String emoji = "🤖";
        if (staffList != null) {
            for (PluginStaffDescriptor d : staffList) {
                if (d.name().equalsIgnoreCase(effective)) {
                    emoji = d.emoji();
                    break;
                }
            }
        }
        if (text != null && text.startsWith("**[" + emoji)) {
            return text;
        }
        return "**[" + emoji + " " + effective + "]**\n\n" + (text != null ? text : "");
    }

    private static String formatErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "❌ **Error**: Unknown error occurred.";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage()
                : root.getClass().getSimpleName();
        String mainMsg = throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();

        StringBuilder sb = new StringBuilder();
        sb.append("❌ **Error processing request**: ").append(mainMsg);
        if (!mainMsg.equals(rootMsg)) {
            sb.append("\n\n**Root Cause**: ").append(rootMsg);
        }
        String lowerRoot = rootMsg.toLowerCase();
        if (lowerRoot.contains("connection refused") || lowerRoot.contains("connectexception")) {
            sb.append("\n\n💡 *Tip: Check that Ollama or your LLM backend is running and accessible in System Settings.*");
        } else if (lowerRoot.contains("401") || lowerRoot.contains("unauthorized") || lowerRoot.contains("api key")) {
            sb.append("\n\n💡 *Tip: Check that your LLM API Key is configured properly in System Settings.*");
        }
        return sb.toString();
    }

    private static boolean isTextFile(File file) {
        try {
            String type = Files.probeContentType(file.toPath());
            if (type != null && (type.startsWith("text/")
                    || type.startsWith("application/json")
                    || type.startsWith("application/xml")
                    || type.startsWith("application/javascript"))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[1024];
            int read = in.read(buf);
            if (read == -1) {
                return true;
            }
            int controlChars = 0;
            for (int i = 0; i < read; i++) {
                byte b = buf[i];
                if (b == 0) {
                    return false;
                }
                if (b < 32 && b != 9 && b != 10 && b != 13) {
                    controlChars++;
                }
            }
            return ((double) controlChars / read) < 0.1;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readTextFileSafe(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
            } catch (Exception ex) {
                return "[Error reading file content: " + ex.getMessage() + "]";
            }
        }
    }

    private static String sanitizeFileName(String raw) {
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = UNSAFE_FILENAME_PATTERN.matcher(base).replaceAll("_").trim();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "attachment.bin";
        }
        return cleaned;
    }

    private static long streamToFileWithLimit(InputStream in, Path destFile, long maxBytes) throws IOException {
        byte[] buffer = new byte[16384];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(destFile)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("File exceeds maximum upload limit (" + (maxBytes / (1024 * 1024)) + " MB).");
                }
                out.write(buffer, 0, n);
            }
        }
        return total;
    }

    private void deleteUploadQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null && parent.startsWith(uploadsDir) && !parent.equals(uploadsDir)) {
                Files.deleteIfExists(parent);
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] readLimitedBytes(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException("File exceeds maximum upload limit (" + (maxBytes / (1024 * 1024)) + " MB).");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private JsonNode readJsonBody(HttpExchange exchange) {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] bytes = readLimitedBytes(is, 5L * 1024L * 1024L);
            if (bytes.length == 0) {
                return null;
            }
            return objectMapper.readTree(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, json.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json);
        } finally {
            exchange.close();
        }
    }

    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}

