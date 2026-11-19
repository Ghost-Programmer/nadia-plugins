package com.nadia.plugin.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.PluginChatTurn;
import com.nadia.app.plugin.api.PluginStaffDescriptor;
import com.nadia.plugin.slack.client.SlackSocketClient;
import com.nadia.plugin.slack.config.SlackConfig;
import com.nadia.plugin.slack.markdown.SlackMarkdownConverter;
import com.nadia.plugin.slack.service.SlackAlertBackgroundService;
import com.nadia.plugin.slack.tool.SlackTool;
import com.nadia.plugin.slack.ui.SlackUiExtension;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit and integration test suite for the {@code slack-integration} PF4J
 * plugin.
 */
class SlackIntegrationPluginTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer mockSlackApi;

  @AfterEach
  void tearDown() {
    SlackRuntime.getInstance().shutdown();
    if (mockSlackApi != null) {
      mockSlackApi.stop(0);
      mockSlackApi = null;
    }
  }

  @Test
  void pf4jExtensionsIndexRegistersAllThreeSlackExtensions() throws Exception {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/extensions.idx")) {
      assertNotNull(in, "PF4J annotation processor must generate META-INF/extensions.idx");
      String idx = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(idx.contains(SlackTool.class.getName()));
      assertTrue(idx.contains(SlackAlertBackgroundService.class.getName()));
      assertTrue(idx.contains(SlackUiExtension.class.getName()));
    }
  }

  @Test
  void slackMarkdownConverterFormatsCommonMarkAndGfmToMrkdwn() {
    SlackMarkdownConverter converter = new SlackMarkdownConverter();
    String md = """
        # Deployment Report
        **Status**: *Online* with ~~0 warnings~~ `v1.0.3`
        See [Dashboard](https://example.com/dash).

        - Core Engine
        - Plugins

        | Service | State |
        | --- | --- |
        | Slack | OK |
        """;
    String mrkdwn = converter.toSlackMrkdwn(md);
    assertTrue(mrkdwn.contains("*Deployment Report*"));
    assertTrue(mrkdwn.contains("*Status*: _Online_ with ~0 warnings~ `v1.0.3`"));
    assertTrue(mrkdwn.contains("<https://example.com/dash|Dashboard>"));
    assertTrue(mrkdwn.contains("• Core Engine"));
    assertTrue(mrkdwn.contains("Service | State"));
    assertTrue(mrkdwn.contains("Slack   | OK"));
  }

  @Test
  void slackConfigLoadsLegacyNadiaSlackPropertiesAndAuthorizesUsers(@TempDir Path tempHome) throws Exception {
    Files.writeString(tempHome.resolve("nadia.properties"), """
        nadia.slack.enabled=true
        nadia.slack.bot-token=xoxb-legacy-bot
        nadia.slack.app-token=xapp-legacy-app
        nadia.slack.allowed-users=U111, U222
        nadia.slack.notification-channel=#ops-alerts
        """, StandardCharsets.UTF_8);

    Map<String, String> propStore = new HashMap<>();
    NadiaPluginContext ctx = mock(NadiaPluginContext.class);
    when(ctx.getNadiaHomeDir()).thenReturn(tempHome);
    when(ctx.getProperty(anyString(), any())).thenAnswer(inv -> {
      String key = inv.getArgument(0);
      String def = inv.getArgument(1);
      return propStore.getOrDefault(key, def);
    });

    SlackConfig loaded = SlackConfig.load(ctx);
    assertTrue(loaded.enabled());
    assertEquals("xoxb-legacy-bot", loaded.botToken());
    assertEquals("xapp-legacy-app", loaded.appToken());
    assertEquals("#ops-alerts", loaded.notificationChannel());
    assertTrue(loaded.isUserAllowed("U111"));
    assertTrue(loaded.isUserAllowed("u222"));
    assertFalse(loaded.isUserAllowed("U999"));
  }

  @Test
  void slackSocketClientHandlesOutboundNotificationsAndInboundStaffThreadedReplies() throws Exception {
    BlockingQueue<JsonNode> postedMessages = new LinkedBlockingQueue<>();
    mockSlackApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    mockSlackApi.createContext("/api/chat.postMessage", exchange -> {
      byte[] reqBytes = exchange.getRequestBody().readAllBytes();
      postedMessages.offer(objectMapper.readTree(reqBytes));
      byte[] resp = "{\"ok\":true,\"channel\":\"C123\",\"ts\":\"1700000000.000100\"}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, resp.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(resp);
      }
    });
    mockSlackApi.start();
    String baseUrl = "http://127.0.0.1:" + mockSlackApi.getAddress().getPort() + "/api";

    NadiaPluginContext ctx = mock(NadiaPluginContext.class);
    when(ctx.getAvailableStaff()).thenReturn(List.of(
        new PluginStaffDescriptor("Nadia", "🤖", "General Assistant"),
        new PluginStaffDescriptor("3D Printer", "🖨️", "3D Print Specialist"),
        new PluginStaffDescriptor("Software Architect", "🏛️", "Architecture Specialist")));
    when(ctx.submitChatQuery(eq("slack-C123-1700.55"), anyString(), any(), eq("Software Architect"), any()))
        .thenReturn(List.of(new PluginChatTurn("Software Architect", "**Design** approved!")));

    SlackSocketClient client = new SlackSocketClient(ctx, new SlackMarkdownConverter(), null);
    client.setApiBaseUrl(baseUrl);
    // Keep Socket Mode start harmless via blank appToken while testing Web API &
    // envelope dispatch
    SlackConfig cfg = new SlackConfig(true, "xoxb-test-token", "", "U_ALICE", "#general");
    client.applyConfig(cfg);
    assertEquals("Missing Tokens", client.getConnectionStatus());

    // 1. Outbound tool notification
    SlackTool tool = new SlackTool(client);
    String notifJson = tool.sendSlackNotification("Build **succeeded**!", null);
    assertTrue(notifJson.contains("\"status\":\"SUCCESS\""));
    JsonNode outboundPost = postedMessages.poll(5, TimeUnit.SECONDS);
    assertNotNull(outboundPost);
    assertEquals("#general", outboundPost.path("channel").asText());
    assertEquals("Build *succeeded*!", outboundPost.path("text").asText());

    // 2. Staff extraction check
    assertEquals("Software Architect", client.extractTargetStaff("Hey @SoftwareArchitect review this"));
    assertEquals("3D Printer", client.extractTargetStaff("ask 3D Printer about nozzle temp"));

    // 3. Inbound Socket Mode envelope -> ACK + threaded AI response
    WebSocket mockWs = mock(WebSocket.class);
    when(mockWs.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(mockWs));

    String envelope = """
        {
          "envelope_id": "env-42",
          "type": "events_api",
          "payload": {
            "event": {
              "type": "app_mention",
              "user": "U_ALICE",
              "text": "<@U_BOT> @SoftwareArchitect please review our API",
              "channel": "C123",
              "ts": "1700.55"
            }
          }
        }
        """;
    client.handleSocketEnvelope(envelope, mockWs);
    verify(mockWs).sendText("{\"envelope_id\":\"env-42\"}", true);

    JsonNode replyPost = postedMessages.poll(5, TimeUnit.SECONDS);
    assertNotNull(replyPost);
    assertEquals("C123", replyPost.path("channel").asText());
    assertEquals("1700.55", replyPost.path("thread_ts").asText());
    assertEquals("*Design* approved!", replyPost.path("text").asText());

    // 4. Unauthorized user is ignored
    String unauthEnvelope = """
        {
          "envelope_id": "env-43",
          "type": "events_api",
          "payload": {
            "event": {
              "type": "message",
              "user": "U_INTRUDER",
              "text": "run something",
              "channel": "C123",
              "ts": "1700.99"
            }
          }
        }
        """;
    client.handleSocketEnvelope(unauthEnvelope, mockWs);
    assertNull(postedMessages.poll(300, TimeUnit.MILLISECONDS));
  }

  @Test
  void slackAlertBackgroundServiceFormatsAndDebouncesSystemEvents() {
    SlackSocketClient mockClient = mock(SlackSocketClient.class);
    when(mockClient.getActiveConfig()).thenReturn(
        new SlackConfig(true, "xoxb-1", "xapp-1", "", "#alerts"));

    SlackAlertBackgroundService alertSvc = new SlackAlertBackgroundService(mockClient);

    String printerDefect = alertSvc.buildStaffEventAlertMessage(
        "printer", "print_defect_paused", "192.168.1.50", "First-Layer Detachment");
    assertNotNull(printerDefect);
    assertTrue(printerDefect.contains("3D Print Defect Detected"));
    assertTrue(printerDefect.contains("192.168.1.50"));

    assertTrue(alertSvc.shouldSendAlert("printer::hms_error::192.168.1.50"));
    assertFalse(alertSvc.shouldSendAlert("printer::hms_error::192.168.1.50"));
    alertSvc.clearDebounceCache();
    assertTrue(alertSvc.shouldSendAlert("printer::hms_error::192.168.1.50"));
  }
}
