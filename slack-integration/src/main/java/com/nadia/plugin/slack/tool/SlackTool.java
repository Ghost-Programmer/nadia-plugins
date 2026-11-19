package com.nadia.plugin.slack.tool;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaToolExtension;
import com.nadia.plugin.slack.SlackRuntime;
import com.nadia.plugin.slack.client.SlackSocketClient;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PF4J {@link NadiaToolExtension} exposing Nadia's Slack notification and
 * channel discovery tools
 * ({@code sendSlackNotification} and {@code getSlackNotificationChannel}) to
 * Spring AI and Nadia's {@code ToolRegistry}.
 */
@Extension
public class SlackTool implements NadiaToolExtension {

    private static final Logger logger = LoggerFactory.getLogger(SlackTool.class);

    private final SlackSocketClient explicitClient;

    /**
     * Default PF4J constructor delegating to {@link SlackRuntime#getInstance()}.
     */
    public SlackTool() {
        this(null);
    }

    /**
     * Package/test constructor accepting an explicit {@link SlackSocketClient}.
     *
     * @param explicitClient explicit client for unit testing (or {@code null} to
     *                       use {@link SlackRuntime})
     */
    public SlackTool(SlackSocketClient explicitClient) {
        this.explicitClient = explicitClient;
    }

    private SlackSocketClient resolveClient() {
        return explicitClient != null ? explicitClient : SlackRuntime.getInstance().getSocketClient();
    }

    /**
     * Sends a Markdown notification message to the designated Slack notification
     * channel (or an optional override channel).
     *
     * @param message the Markdown text message to send
     * @param channel optional Slack channel name or ID override
     * @return JSON response string from the Slack Web API
     */
    @NadiaTool(name = "sendSlackNotification", description = "Sends a notification message to the designated Slack notification channel (or an optional override channel). Use for task completions, system alerts, build statuses, and critical failure reports.")
    public String sendSlackNotification(
            @NadiaToolParam(description = "Markdown message content to publish to Slack") String message,
            @NadiaToolParam(description = "Optional Slack channel name (e.g. #alerts) or channel ID override", required = false) String channel) {
        logger.info("[SlackTool] sendSlackNotification called: channel='{}', messageLength={}",
                channel, message != null ? message.length() : 0);
        SlackSocketClient client = resolveClient();
        if (client == null) {
            return "{\"status\":\"ERROR\",\"message\":\"Slack plugin runtime is not initialized.\"}";
        }
        return client.sendNotification(message, channel);
    }

    /**
     * Convenience overload sending a notification message to the configured default
     * Slack channel.
     *
     * @param message the Markdown text message to send
     * @return JSON response string from the Slack Web API
     */
    public String sendSlackNotification(String message) {
        return sendSlackNotification(message, null);
    }

    /**
     * Retrieves the currently configured default Slack notification channel name/ID
     * and connection status.
     *
     * @return JSON string containing channel info and connectivity status
     */
    @NadiaTool(name = "getSlackNotificationChannel", description = "Retrieves the currently configured default Slack notification channel name/ID and connection status.")
    public String getSlackNotificationChannel() {
        SlackSocketClient client = resolveClient();
        if (client == null) {
            return "{\"status\":\"ERROR\",\"message\":\"Slack plugin runtime is not initialized.\"}";
        }
        return client.getNotificationChannelInfo();
    }
}
