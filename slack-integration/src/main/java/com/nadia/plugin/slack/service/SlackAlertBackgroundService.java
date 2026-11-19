package com.nadia.plugin.slack.service;

import com.nadia.app.plugin.api.NadiaBackgroundServiceExtension;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.slack.SlackRuntime;
import com.nadia.plugin.slack.client.SlackSocketClient;
import com.nadia.plugin.slack.config.SlackConfig;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PF4J {@link NadiaBackgroundServiceExtension} dispatching automated Slack
 * alerts for Nadia startup/shutdown,
 * system diagnostic failures, Bambu 3D printer HMS &amp; print-defect events,
 * severe weather alerts,
 * network anomalies, Docker/build failures, and high-priority staff event
 * triggers.
 *
 * <p>
 * Applies per-key debouncing (60 seconds) to prevent notification storms on
 * repeated events.
 * </p>
 */
@Extension
public class SlackAlertBackgroundService implements NadiaBackgroundServiceExtension {

    private static final Logger logger = LoggerFactory.getLogger(SlackAlertBackgroundService.class);
    private static final long DEFAULT_DEBOUNCE_MS = 60_000L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final SlackSocketClient explicitClient;
    private final Instant startupTime = Instant.now();
    private final AtomicBoolean startupSent = new AtomicBoolean(false);
    private final AtomicBoolean shutdownSent = new AtomicBoolean(false);
    private final Map<String, Long> alertDebounceMap = new ConcurrentHashMap<>();

    /**
     * Default PF4J constructor delegating to {@link SlackRuntime#getInstance()}.
     */
    public SlackAlertBackgroundService() {
        this(null);
    }

    /**
     * Package/test constructor accepting an explicit {@link SlackSocketClient}.
     *
     * @param explicitClient explicit client for unit testing (or {@code null} to
     *                       use {@link SlackRuntime})
     */
    public SlackAlertBackgroundService(SlackSocketClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private SlackSocketClient resolveClient() {
        return explicitClient != null ? explicitClient : SlackRuntime.getInstance().getSocketClient();
    }

    @Override
    public String getServiceName() {
        return "Slack Automated Alert Dispatcher";
    }

    @Override
    public void onPluginStarted(NadiaPluginContext context) {
        if (startupSent.compareAndSet(false, true)) {
            sendStartupNotification(1500L);
        }
    }

    @Override
    public void onPluginStopped(NadiaPluginContext context) {
        if (shutdownSent.compareAndSet(false, true)) {
            sendShutdownNotification();
        }
    }

    @Override
    public void onSystemEvent(String source,
            String eventType,
            String target,
            String payload,
            NadiaPluginContext context) {
        SlackSocketClient client = resolveClient();
        if (client == null || !client.getActiveConfig().enabled()) {
            return;
        }
        if (source == null || source.isBlank()) {
            return;
        }

        String category = source.trim().toLowerCase();
        String subType = eventType != null ? eventType.trim() : "";

        if ("bambu_hms".equals(category)) {
            handleBambuHmsEvent(client, subType, target, payload);
            return;
        }
        if ("system_alert".equals(category)) {
            handleSystemAlertEvent(client, subType, payload);
            return;
        }

        String lowerSub = subType.toLowerCase();
        String alertKey = category + "::" + lowerSub + "::"
                + (target != null && !target.isBlank() ? target.trim() : "none");
        if (!isDebounced(alertKey)) {
            String alertMessage = buildStaffEventAlertMessage(category, lowerSub, target, payload);
            if (alertMessage != null && shouldSendAlert(alertKey)) {
                dispatchAsyncNotification(client, alertMessage);
            }
        }
    }

    /**
     * Dispatches an asynchronous startup notification to Slack with host and
     * runtime metadata.
     *
     * @param delayMs optional delay in milliseconds before dispatching
     */
    public void sendStartupNotification(long delayMs) {
        SlackSocketClient client = resolveClient();
        if (client == null || !client.getActiveConfig().enabled()) {
            return;
        }

        Thread.ofVirtual().name("slack-plugin-startup-notif").start(() -> {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                SlackConfig cfg = client.getActiveConfig();
                if (!cfg.enabled()) {
                    return;
                }
                String hostname = getLocalHostname();
                String osName = System.getProperty("os.name", "Unknown OS");
                String osArch = System.getProperty("os.arch", "");
                String javaVersion = System.getProperty("java.version", "Unknown Java");
                String channel = cfg.notificationChannel();

                String msg = String.format(
                        "🚀 *Nadia System Online*\n"
                                + "• *Host*: `%s` | *Platform*: `%s (%s)` | *Java*: `%s`\n"
                                + "• *Notification Channel*: `%s`\n"
                                + "• *Status*: All cognitive agents, telemetry monitors, and PF4J plugins are initialized and active.",
                        hostname, osName, osArch, javaVersion,
                        (!channel.isBlank() ? channel : "default"));
                logger.info("[SlackAlertService] Dispatching startup notification to Slack");
                client.sendNotification(msg, null);
            } catch (Exception e) {
                logger.warn("[SlackAlertService] Failed to deliver startup Slack alert: {}", e.getMessage());
            }
        });
    }

    /**
     * Dispatches a synchronous shutdown notification to Slack with session uptime
     * statistics.
     */
    public void sendShutdownNotification() {
        SlackSocketClient client = resolveClient();
        if (client == null || !client.getActiveConfig().enabled()) {
            return;
        }
        try {
            String hostname = getLocalHostname();
            long uptimeSeconds = Math.max(0L, Duration.between(startupTime, Instant.now()).toSeconds());
            long hours = uptimeSeconds / 3600;
            long minutes = (uptimeSeconds % 3600) / 60;
            long seconds = uptimeSeconds % 60;
            String uptimeFormatted = String.format("%dh %dm %ds", hours, minutes, seconds);

            String msg = String.format(
                    "🛑 *Nadia System Shutting Down*\n"
                            + "• *Host*: `%s`\n"
                            + "• *Session Uptime*: `%s`\n"
                            + "• *Status*: Background workers, telemetry monitors, and socket connections stopped gracefully.",
                    hostname, uptimeFormatted);
            logger.info("[SlackAlertService] Dispatching shutdown notification to Slack");
            client.sendNotification(msg, null);
        } catch (Exception e) {
            logger.warn("[SlackAlertService] Failed to deliver shutdown Slack alert: {}", e.getMessage());
        }
    }

    /**
     * Formats a staff array event into a Slack alert Markdown message when the
     * category/subtype warrants alerting.
     *
     * @param category event category
     * @param subType  lowercase event subtype
     * @param s3       target host/device/location
     * @param s4       diagnostic details
     * @return formatted Markdown alert or {@code null} if not alert-worthy
     */
    public String buildStaffEventAlertMessage(String category, String subType, String s3, String s4) {
        String target = (s3 != null && !s3.isBlank()) ? s3.trim() : "N/A";
        String details = (s4 != null && !s4.isBlank()) ? s4.trim() : "(no additional details provided)";

        return switch (category) {
            case "printer" -> formatPrinterAlert(subType, target, details);
            case "weather" -> formatWeatherAlert(subType, target, details);
            case "network" -> formatNetworkAlert(subType, target, details);
            case "system" -> formatSystemDiagnosticAlert(subType, target, details);
            case "docker" -> formatDockerAlert(subType, target, details);
            case "code" -> formatBuildFailureAlert(subType, target, details);
            case "hue" -> formatSmartHomeAlert(subType, target, details);
            case "email" -> formatUrgentEmailAlert(subType, target, details);
            case "stock", "finance" -> formatFinancialAlert(subType, target, details);
            case "satellite" -> formatSatelliteAlert(subType, target, details);
            case "scheduler" -> formatSchedulerAlert(subType, target, details);
            case "blog" -> formatBlogAlert(subType, target, details);
            default -> null;
        };
    }

    private void handleBambuHmsEvent(SlackSocketClient client, String hmsCode, String encodedTarget,
            String description) {
        String ip = "unknown";
        String model = "3D Printer";
        String severity = "WARNING";
        if (encodedTarget != null && !encodedTarget.isBlank()) {
            String[] parts = encodedTarget.split("\\|", -1);
            if (parts.length > 0 && !parts[0].isBlank())
                ip = parts[0].trim();
            if (parts.length > 1 && !parts[1].isBlank())
                model = parts[1].trim();
            if (parts.length > 2 && !parts[2].isBlank())
                severity = parts[2].trim();
        }
        String code = (hmsCode != null && !hmsCode.isBlank()) ? hmsCode : "HMS_UNKNOWN";
        String alertKey = "bambu_hms::" + ip + "::" + code;
        if (!shouldSendAlert(alertKey)) {
            return;
        }
        String msg = String.format(
                "🚨 *Bambu 3D Printer HMS Hardware Error*\n"
                        + "• *Printer*: `%s` (%s)\n"
                        + "• *Error Code*: `%s` [%s]\n"
                        + "• *Description*: %s\n"
                        + "• *Timestamp*: %s",
                ip,
                model,
                code,
                severity,
                (description != null && !description.isBlank() ? description : "Hardware malfunction detected"),
                TIME_FORMATTER.format(Instant.now()));
        dispatchAsyncNotification(client, msg);
    }

    private void handleSystemAlertEvent(SlackSocketClient client, String rawAlertType, String message) {
        String alertType = (rawAlertType != null && !rawAlertType.isBlank()) ? rawAlertType.toUpperCase() : "WARNING";
        String body = message != null ? message : "";
        String alertKey = "system_alert::" + alertType + "::" + body;
        if (!shouldSendAlert(alertKey)) {
            return;
        }
        String icon = switch (alertType) {
            case "ERROR", "CRITICAL" -> "❌";
            case "SUCCESS" -> "✅";
            case "INFO" -> "ℹ️";
            default -> "⚠️";
        };
        String msg = String.format(
                "%s *System Alert: %s*\n"
                        + "• *Message*: %s\n"
                        + "• *Timestamp*: %s",
                icon, alertType, body, TIME_FORMATTER.format(Instant.now()));
        dispatchAsyncNotification(client, msg);
    }

    private String formatPrinterAlert(String subType, String target, String details) {
        return switch (subType) {
            case "hms_error" -> String.format(
                    "🖨️ *Bambu 3D Printer HMS Error Alert*\n• *Printer / IP*: `%s`\n• *Diagnostic Details*: %s",
                    target, details);
            case "print_failed" -> String.format(
                    "⚠️ *3D Printer Job Failed / Halted*\n• *Printer*: `%s`\n• *Reason*: %s", target, details);
            case "thermal_anomaly" -> String.format(
                    "🔥 *3D Printer Thermal Anomaly Alert*\n• *Printer*: `%s`\n• *Telemetry*: %s", target, details);
            case "filament_runout" -> String.format(
                    "🧵 *3D Printer Filament Runout / AMS Warning*\n• *Printer*: `%s`\n• *Slot / Details*: %s", target,
                    details);
            case "job_started" -> String.format(
                    "🖨️ *3D Print Job Started*\n• *Printer*: `%s`\n• *Job*: `%s`", target, details);
            case "print_completed" -> String.format(
                    "✅ *3D Print Job Completed Successfully*\n• *Printer*: `%s`\n• *Summary*: %s", target, details);
            case "print_defect_paused" -> String.format(
                    "🚨 *3D Print Defect Detected — Print Job Automatically Paused*\n• *Printer*: `%s`\n• *Visual Inspection Findings*: %s",
                    target, details);
            default -> null;
        };
    }

    private String formatWeatherAlert(String subType, String target, String details) {
        if (subType.contains("severe") || subType.contains("alert") || subType.contains("warning")
                || subType.contains("forecast")) {
            return String.format("⛈️ *Severe Weather Alert*\n• *Location*: `%s`\n• *Alert / Conditions*: %s", target,
                    details);
        }
        return null;
    }

    private String formatNetworkAlert(String subType, String target, String details) {
        return switch (subType) {
            case "rogue_device" -> String.format(
                    "🚨 *Network Security Alert: Unrecognized Device Detected*\n• *IP Address*: `%s`\n• *Hardware / Vendor*: %s",
                    target, details);
            case "host_offline" -> String.format(
                    "⚠️ *Network Critical Host Offline*\n• *Target Host*: `%s`\n• *Status*: %s", target, details);
            case "port_anomaly" -> String.format(
                    "🛡️ *Network Port Anomaly Detected*\n• *Host*: `%s`\n• *Port / Service*: %s", target, details);
            case "alert" -> String.format(
                    "🌐 *Network Security & Diagnostic Event*\n• *Target*: `%s`\n• *Details*: %s", target, details);
            case "subnet_scan_complete" -> String.format(
                    "🌐 *Network Subnet Discovery Completed*\n• *Subnet*: `%s`\n• *Inventory*: %s", target, details);
            default -> null;
        };
    }

    private String formatSystemDiagnosticAlert(String subType, String target, String details) {
        if (subType.contains("fail") || subType.contains("error") || subType.contains("crash")
                || subType.contains("diagnostic")) {
            return String.format(
                    "❌ *System Diagnostic Test Failure*\n• *Subsystem / Test ID*: `%s`\n• *Failure Details*: %s",
                    target, details);
        }
        return null;
    }

    private String formatDockerAlert(String subType, String target, String details) {
        if (subType.contains("crash") || subType.contains("fail") || subType.contains("error")) {
            return String.format("🐳 *Docker Container Failure Alert*\n• *Container*: `%s`\n• *State / Diagnostic*: %s",
                    target, details);
        }
        return null;
    }

    private String formatBuildFailureAlert(String subType, String target, String details) {
        if (subType.contains("build_failed") || subType.contains("test_failed") || subType.contains("compile_error")) {
            return String.format(
                    "🔨 *Build / Test Suite Failure Alert*\n• *Project / Component*: `%s`\n• *Error Summary*: %s",
                    target, details);
        }
        return null;
    }

    private String formatSmartHomeAlert(String subType, String target, String details) {
        if (subType.contains("offline") || subType.contains("unreachable") || subType.contains("error")) {
            return String.format(
                    "💡 *Smart Home Alert: Philips Hue Bridge Offline*\n• *Bridge IP*: `%s`\n• *Status*: %s", target,
                    details);
        }
        return null;
    }

    private String formatUrgentEmailAlert(String subType, String target, String details) {
        if (subType.contains("vip") || subType.contains("urgent")) {
            return String.format("📧 *VIP / Urgent Email Notification*\n• *Sender*: `%s`\n• *Subject / Preview*: %s",
                    target, details);
        }
        return null;
    }

    private String formatFinancialAlert(String subType, String target, String details) {
        if (subType.contains("spike") || subType.contains("alert") || subType.contains("volatility")
                || subType.contains("circuit_breaker")) {
            return String.format(
                    "📈 *Financial Market Volatility Alert*\n• *Ticker / Instrument*: `%s`\n• *Market Action*: %s",
                    target, details);
        }
        return null;
    }

    private String formatSatelliteAlert(String subType, String target, String details) {
        if (subType.contains("pass") || subType.contains("overhead")) {
            return String.format(
                    "🛰️ *Visible Satellite Overhead Pass*\n• *Satellite*: `%s`\n• *Trajectory / Viewing Info*: %s",
                    target, details);
        }
        return null;
    }

    private String formatSchedulerAlert(String subType, String target, String details) {
        if (subType.contains("fail") || subType.contains("error")) {
            return String.format("🕒 *Scheduled Job Execution Failure*\n• *Job / Session*: `%s`\n• *Error*: %s", target,
                    details);
        }
        return null;
    }

    private String formatBlogAlert(String subType, String target, String details) {
        if (subType.contains("new_post")) {
            return String.format("📰 *New Article Discovered*\n• *Feed / Source*: `%s`\n• *Article*: %s", target,
                    details);
        }
        return null;
    }

    private void dispatchAsyncNotification(SlackSocketClient client, String message) {
        Thread.ofVirtual().name("slack-plugin-alert-dispatch").start(() -> {
            try {
                client.sendNotification(message, null);
            } catch (Exception e) {
                logger.error("[SlackAlertService] Error dispatching notification to Slack:", e);
            }
        });
    }

    private boolean isDebounced(String alertKey) {
        Long lastSent = alertDebounceMap.get(alertKey);
        return lastSent != null && (System.currentTimeMillis() - lastSent) < DEFAULT_DEBOUNCE_MS;
    }

    /**
     * Checks whether an alert key should be sent or suppressed by the 60-second
     * debounce window.
     *
     * @param alertKey unique category + subtype + target key
     * @return {@code true} if allowed to send; {@code false} if debounced
     */
    public boolean shouldSendAlert(String alertKey) {
        long now = System.currentTimeMillis();
        String boundedKey = alertKey != null && alertKey.length() > 160
                ? alertKey.substring(0, 128) + "#" + Integer.toHexString(alertKey.hashCode())
                : alertKey;
        final boolean[] allowed = {false};
        alertDebounceMap.compute(boundedKey, (k, lastSent) -> {
            if (lastSent != null && (now - lastSent) < DEFAULT_DEBOUNCE_MS) {
                return lastSent;
            }
            allowed[0] = true;
            return now;
        });
        if (allowed[0] && alertDebounceMap.size() > 500) {
            alertDebounceMap.entrySet().removeIf(entry -> (now - entry.getValue()) > (DEFAULT_DEBOUNCE_MS * 2));
        }
        return allowed[0];
    }

    /**
     * Clears the internal alert debounce cache.
     */
    public void clearDebounceCache() {
        alertDebounceMap.clear();
    }

    private static String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
