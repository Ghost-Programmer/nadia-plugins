package com.nadia.plugin.slack.ui;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.slack.SlackRuntime;
import com.nadia.plugin.slack.client.SlackSocketClient;
import com.nadia.plugin.slack.config.SlackConfig;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Factory building the localized JavaFX Settings {@link Tab} for the
 * {@code slack-integration} PF4J plugin.
 */
public final class SlackSettingsTab {

    private SlackSettingsTab() {
    }

    private static String tr(NadiaPluginContext ctx, String key, String fallback) {
        if (ctx == null) {
            return fallback;
        }
        String resolved = ctx.translate(key);
        return (resolved == null || resolved.equals(key)) ? fallback : resolved;
    }

    /**
     * Creates a new localized {@link Tab} bound to {@link SlackRuntime}.
     *
     * @param runtime Slack plugin runtime coordinator
     * @param context host {@link NadiaPluginContext} for localization and
     *                persistence
     * @return configured JavaFX {@link Tab}
     */
    public static Tab create(SlackRuntime runtime, NadiaPluginContext context) {
        Tab tab = new Tab("💬 " + tr(context, "settings.tab.slack", "Slack Integration"));
        tab.setClosable(false);

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(16));
        content.getStyleClass().add("settings-tab-content");

        // Header
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label(tr(context, "settings.slack.title", "Slack Bot Integration (Socket Mode)"));
        titleLabel.getStyleClass().add("settings-title-label");
        Label descLabel = new Label(tr(context, "settings.slack.desc",
                "Connect Nadia to your Slack workspace via Socket Mode (WebSocket) with zero public webhooks or firewall exposure. Nadia responds to direct messages, @mentions, and dispatches automated system alerts."));
        descLabel.getStyleClass().add("settings-info-label");
        descLabel.setWrapText(true);
        headerBox.getChildren().addAll(titleLabel, descLabel);
        BorderPane.setMargin(headerBox, new Insets(0, 0, 16, 0));
        content.setTop(headerBox);

        SlackConfig current = runtime.loadCurrentConfig();
        SlackSocketClient client = runtime.getSocketClient();

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(14);
        grid.setAlignment(Pos.TOP_LEFT);

        // 1. Status Indicator
        Label statusKeyLabel = new Label(tr(context, "settings.slack.status", "Connection Status:"));
        statusKeyLabel.getStyleClass().add("info-grid-key");
        Label statusValLabel = new Label(client != null
                ? client.getConnectionStatus()
                : tr(context, "settings.slack.unavailable", "Disabled"));
        statusValLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -accent-color;");

        // 2. Enable CheckBox
        Label enableKeyLabel = new Label(tr(context, "settings.slack.integration", "Slack Integration:"));
        enableKeyLabel.getStyleClass().add("info-grid-key");
        CheckBox enableSlackCheck = new CheckBox(
                tr(context, "settings.slack.enable", "Enable Slack Socket Mode Connection"));
        enableSlackCheck.getStyleClass().add("console-checkbox");
        enableSlackCheck.setSelected(current.enabled());

        // 3. Bot Token
        Label botTokenLabel = new Label(tr(context, "settings.slack.bot_token", "Bot User OAuth Token:"));
        botTokenLabel.getStyleClass().add("info-grid-key");
        PasswordField botTokenField = new PasswordField();
        botTokenField.getStyleClass().add("settings-input");
        botTokenField.setPromptText("xoxb-xxxxxxxxxxxx-xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxx");
        botTokenField.setText(current.botToken());

        // 4. App Token
        Label appTokenLabel = new Label(tr(context, "settings.slack.app_token", "App-Level Token:"));
        appTokenLabel.getStyleClass().add("info-grid-key");
        PasswordField appTokenField = new PasswordField();
        appTokenField.getStyleClass().add("settings-input");
        appTokenField.setPromptText("xapp-xxxxxxxxxxxx-xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxx");
        appTokenField.setText(current.appToken());

        // 5. Allowed Users
        Label allowedUsersLabel = new Label(tr(context, "settings.slack.allowed_users", "Allowed Slack User IDs:"));
        allowedUsersLabel.getStyleClass().add("info-grid-key");
        TextField allowedUsersField = new TextField();
        allowedUsersField.getStyleClass().add("settings-input");
        allowedUsersField.setPromptText(tr(context, "settings.slack.allowed_users_prompt",
                "U01234567, U89ABCDEF (Leave blank to allow all workspace members)"));
        allowedUsersField.setText(current.allowedUsers());

        Label allowedUsersHelp = new Label(tr(context, "settings.slack.allowed_users_help",
                "Comma-separated Slack Member IDs (found in a user's Slack profile -> Copy member ID)."));
        allowedUsersHelp.getStyleClass().add("settings-desc-label");
        allowedUsersHelp.setWrapText(true);

        // 6. Default Notification Channel
        Label notifChanLabel = new Label(tr(context, "settings.slack.notif_channel", "Notification Channel:"));
        notifChanLabel.getStyleClass().add("info-grid-key");
        TextField notifChanField = new TextField();
        notifChanField.getStyleClass().add("settings-input");
        notifChanField.setPromptText(tr(context, "settings.slack.notif_channel_prompt",
                "#nadia-alerts or C0123456789 (Channel where Nadia sends notifications)"));
        notifChanField.setText(current.notificationChannel());

        Label notifChanHelp = new Label(tr(context, "settings.slack.notif_channel_help",
                "Default Slack channel name (e.g. #general) or Channel ID where Nadia posts automated notifications and alerts. Ensure the Nadia bot is invited to this channel."));
        notifChanHelp.getStyleClass().add("settings-desc-label");
        notifChanHelp.setWrapText(true);

        grid.add(statusKeyLabel, 0, 0);
        grid.add(statusValLabel, 1, 0);
        grid.add(enableKeyLabel, 0, 1);
        grid.add(enableSlackCheck, 1, 1);
        grid.add(botTokenLabel, 0, 2);
        grid.add(botTokenField, 1, 2);
        grid.add(appTokenLabel, 0, 3);
        grid.add(appTokenField, 1, 3);
        grid.add(allowedUsersLabel, 0, 4);
        grid.add(allowedUsersField, 1, 4);
        grid.add(allowedUsersHelp, 1, 5);
        grid.add(notifChanLabel, 0, 6);
        grid.add(notifChanField, 1, 6);
        grid.add(notifChanHelp, 1, 7);

        GridPane.setHgrow(botTokenField, Priority.ALWAYS);
        GridPane.setHgrow(appTokenField, Priority.ALWAYS);
        GridPane.setHgrow(allowedUsersField, Priority.ALWAYS);
        GridPane.setHgrow(notifChanField, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("settings-scroll-pane");
        content.setCenter(scrollPane);

        // Live status listener
        Runnable statusUpdater = () -> {
            Runnable fxUpdate = () -> {
                SlackSocketClient latestClient = runtime.getSocketClient();
                statusValLabel.setText(latestClient != null
                        ? latestClient.getConnectionStatus()
                        : tr(context, "settings.slack.unavailable", "Disabled"));
            };
            try {
                if (Platform.isFxApplicationThread()) {
                    fxUpdate.run();
                } else {
                    Platform.runLater(fxUpdate);
                }
            } catch (IllegalStateException ignored) {
                fxUpdate.run();
            }
        };
        runtime.addStatusListener(statusUpdater);

        // Bottom Action Buttons
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        actionBox.setPadding(new Insets(16, 0, 0, 0));

        Label feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-font-size: 11px;");

        Button saveBtn = new Button(tr(context, "settings.slack.save_btn", "Save & Apply Connection"));
        saveBtn.getStyleClass().addAll("settings-btn", "settings-save-btn");
        saveBtn.setOnAction(e -> {
            SlackConfig updated = new SlackConfig(
                    enableSlackCheck.isSelected(),
                    botTokenField.getText(),
                    appTokenField.getText(),
                    allowedUsersField.getText(),
                    notifChanField.getText());
            feedbackLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 11px;");
            feedbackLabel.setText("...");
            Thread.ofVirtual().name("slack-settings-apply").start(() -> {
                runtime.saveAndApplyConfig(updated);
                Runnable doneUi = () -> {
                    SlackSocketClient latestClient = runtime.getSocketClient();
                    if (latestClient != null) {
                        statusValLabel.setText(latestClient.getConnectionStatus());
                    }
                    feedbackLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11px;");
                    feedbackLabel
                            .setText(tr(context, "settings.slack.saved_ok", "Slack configuration saved successfully!"));
                };
                try {
                    Platform.runLater(doneUi);
                } catch (IllegalStateException ignored) {
                    doneUi.run();
                }
            });
        });

        actionBox.getChildren().addAll(feedbackLabel, saveBtn);
        content.setBottom(actionBox);

        tab.setContent(content);
        return tab;
    }
}
