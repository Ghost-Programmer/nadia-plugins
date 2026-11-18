package com.nadia.plugin.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.PluginChatTurn;
import com.nadia.app.plugin.api.PluginStaffDescriptor;
import com.nadia.plugin.web.config.WebInterfaceConfig;
import com.nadia.plugin.web.server.WebInterfaceServer;
import com.nadia.plugin.web.service.WebChatSessionStore;
import com.nadia.plugin.web.tool.WebInterfaceTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginWrapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit and live HTTP integration tests for the {@code web-interface} PF4J plugin.
 */
public class WebInterfacePluginTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    public void tearDown() {
        WebInterfaceRuntime.getInstance().shutdown();
    }

    @Test
    public void testExtensionsIdxContainsUiAndToolExtensions() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/extensions.idx")) {
            assertNotNull(in, "PF4J META-INF/extensions.idx must be generated");
            String idx = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(idx.contains(WebInterfaceUiExtension.class.getName()));
            assertTrue(idx.contains(WebInterfaceTool.class.getName()));
        }
    }

    @Test
    public void testConfigPersistenceAndTokenGeneration() {
        Map<String, String> store = new HashMap<>();
        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ctx).setProperty(anyString(), anyString());
        when(ctx.getProperty(anyString(), anyString())).thenAnswer(inv ->
                store.getOrDefault(inv.getArgument(0), inv.getArgument(1)));

        String token = WebInterfaceConfig.generateRandomToken();
        assertEquals(32, token.length());

        WebInterfaceConfig cfg = new WebInterfaceConfig(true, "0.0.0.0", 9095, token, "Custom Nadia", false, 50, false);
        cfg.save(ctx);

        WebInterfaceConfig loaded = WebInterfaceConfig.load(ctx);
        assertTrue(loaded.enabled());
        assertEquals("0.0.0.0", loaded.host());
        assertEquals(9095, loaded.port());
        assertEquals(token, loaded.authToken());
        assertTrue(loaded.isAuthRequired());
        assertEquals("Custom Nadia", loaded.pageTitle());
        assertFalse(loaded.allowUploads());
        assertEquals(50, loaded.maxUploadMb());
        assertFalse(loaded.enableCronStream());
    }

    @Test
    public void testExtractTargetStaffMatchesChatWindowViewSemantics() {
        List<PluginStaffDescriptor> staff = List.of(
                new PluginStaffDescriptor("Nadia", "🤖", "Assistant"),
                new PluginStaffDescriptor("Software Architect", "🏛️", "Architecture"),
                new PluginStaffDescriptor("3D Printer", "🖨️", "Printing")
        );

        assertEquals("Software Architect", WebInterfaceServer.extractTargetStaff("@Software Architect review this repo", staff));
        assertEquals("Software Architect", WebInterfaceServer.extractTargetStaff("please ask @softwarearchitect to check", staff));
        assertEquals("3D Printer", WebInterfaceServer.extractTargetStaff("ask 3D Printer: what is nozzle temp?", staff));
        assertNull(WebInterfaceServer.extractTargetStaff("hello everyone, how are you?", staff));
    }

    @Test
    public void testEndToEndHttpChatAttachmentsAuthRagAndCronStream(@TempDir Path tempDir) throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebInterfaceConfig.PROP_ENABLED, "true");
        props.put(WebInterfaceConfig.PROP_HOST, "127.0.0.1");
        props.put(WebInterfaceConfig.PROP_PORT, "0"); // Ephemeral port
        props.put(WebInterfaceConfig.PROP_AUTH_TOKEN, "secret-test-token");
        props.put(WebInterfaceConfig.PROP_TITLE, "Nadia Web Test");

        NadiaPluginContext ctx = mock(NadiaPluginContext.class);
        when(ctx.getPluginDataDir("web-interface")).thenReturn(tempDir);
        when(ctx.getProperty(anyString(), anyString())).thenAnswer(inv ->
                props.getOrDefault(inv.getArgument(0), inv.getArgument(1)));

        when(ctx.getAvailableStaff()).thenReturn(List.of(
                new PluginStaffDescriptor("Nadia", "🤖", "General Assistant"),
                new PluginStaffDescriptor("Software Architect", "🏛️", "Architecture")
        ));

        CountDownLatch turnLatch = new CountDownLatch(1);
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        AtomicReference<String> capturedStaff = new AtomicReference<>();

        when(ctx.submitChatQuery(anyString(), anyString(), anyList(), any(), any())).thenAnswer(inv -> {
            capturedQuery.set(inv.getArgument(1));
            capturedStaff.set(inv.getArgument(3));
            java.util.function.BiConsumer<String, String> listener = inv.getArgument(4);
            if (listener != null) {
                listener.accept("Software Architect", "Architecture looks solid!");
            }
            turnLatch.countDown();
            return List.of(new PluginChatTurn("Software Architect", "Architecture looks solid!"));
        });

        when(ctx.ingestDocumentIntoRag(anyString(), anyMap())).thenReturn(true);

        AtomicReference<NadiaPluginContext.PluginScheduledJobListener> cronListenerRef = new AtomicReference<>();
        when(ctx.registerScheduledJobListener(any())).thenAnswer(inv -> {
            cronListenerRef.set(inv.getArgument(0));
            return (AutoCloseable) () -> cronListenerRef.set(null);
        });

        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginId()).thenReturn("web-interface");

        WebInterfacePlugin plugin = new WebInterfacePlugin(wrapper);
        plugin.setPluginContext(ctx);
        plugin.start();

        WebInterfaceServer server = WebInterfaceRuntime.getInstance().getServer();
        assertNotNull(server);
        assertTrue(server.isRunning());
        String baseUrl = server.getPrimaryUrl();

        HttpClient client = HttpClient.newHttpClient();

        // 1. Static index.html loads publicly (so lock screen can render)
        HttpResponse<String> htmlResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, htmlResp.statusCode());
        assertTrue(htmlResp.body().contains("Nadia AI Assistant"));

        // 2. Unauthenticated /api/staff returns 401
        HttpResponse<String> unauthResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/staff")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, unauthResp.statusCode());

        // 3. Authenticated /api/staff returns Nadia + Software Architect
        HttpResponse<String> staffResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/staff"))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, staffResp.statusCode());
        assertTrue(staffResp.body().contains("Software Architect"));

        // 4. Upload text attachment
        HttpResponse<String> uploadResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/upload"))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .header("X-File-Name", "notes.md")
                        .POST(HttpRequest.BodyPublishers.ofString("# Design Notes\nHello World"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, uploadResp.statusCode());
        String attId = mapper.readTree(uploadResp.body()).get("attachmentId").asText();

        // 5. Create new chat session & submit @Software Architect message with attachment
        HttpResponse<String> newSessionResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/sessions"))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Arch Review\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, newSessionResp.statusCode());
        String sessionId = mapper.readTree(newSessionResp.body()).path("session").path("id").asText();

        String sendPayload = mapper.writeValueAsString(Map.of(
                "sessionId", sessionId,
                "text", "@Software Architect please review attached notes",
                "attachmentIds", List.of(attId)
        ));
        HttpResponse<String> sendResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat/send"))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(sendPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(202, sendResp.statusCode());
        assertTrue(turnLatch.await(5, TimeUnit.SECONDS));
        assertEquals("Software Architect", capturedStaff.get());
        assertTrue(capturedQuery.get().contains("--- Attached Files ---"));
        assertTrue(capturedQuery.get().contains("# Design Notes"));

        // Verify assistant reply was stored in session history
        Thread.sleep(80);
        HttpResponse<String> historyResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/sessions/" + sessionId))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, historyResp.statusCode());
        assertTrue(historyResp.body().contains("Architecture looks solid!"));

        // 6. Test Send to RAG endpoint
        HttpResponse<String> ragResp = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat/rag"))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"markdown\":\"Important architecture fact\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, ragResp.statusCode());
        assertTrue(ragResp.body().contains("\"success\":true"));

        // 7. Test Scheduled Cron stream listener
        assertNotNull(cronListenerRef.get());
        cronListenerRef.get().onScheduledJobMessage("morning-brief", "Run morning brief", "All systems nominal.");
        HttpResponse<String> cronHistory = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/sessions/" + WebChatSessionStore.CRON_SESSION_ID))
                        .header("X-Nadia-Auth", "secret-test-token")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, cronHistory.statusCode());
        assertTrue(cronHistory.body().contains("All systems nominal."));

        // 8. Verify WebInterfaceTool status output & clean stop
        String toolStatus = new WebInterfaceTool().getWebInterfaceStatus();
        JsonNode statusJson = mapper.readTree(toolStatus);
        assertTrue(statusJson.get("running").asBoolean());

        plugin.stop();
        assertNull(WebInterfaceRuntime.getInstance().getServer());
    }
}

