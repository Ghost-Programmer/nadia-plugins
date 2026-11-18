package com.nadia.plugin.web.ui;

import com.nadia.plugin.web.WebInterfaceRuntime;
import com.nadia.plugin.web.config.WebInterfaceConfig;
import com.nadia.plugin.web.server.WebInterfaceServer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * JavaFX configuration page contributed to Nadia's {@code SettingsWindowView} under the
 * {@code 🌐 Web Interface} tab.
 *
 * <p>Provides live HTTP/HTTPS server status telemetry, one-click Start / Stop / Restart /
 * Open in Browser / Copy URL actions, HTTP vs. HTTPS protocol switching (with automatic
 * self-signed TLS certificate generation or custom PKCS#12/JKS keystore selection), network
 * host &amp; port binding controls, optional Access Token authentication management, file upload
 * toggles/limits, and Scheduled Cron stream controls.</p>
 */
public final class WebInterfaceSettingsTab {

    private static final String PROTOCOL_HTTP = "HTTP (Standard)";
    private static final String PROTOCOL_HTTPS = "HTTPS (TLS Encrypted)";

    private static final String BIND_LOCALHOST = "127.0.0.1 (Localhost Only — Recommended)";
    private static final String BIND_ALL_INTERFACES = "0.0.0.0 (All Network Interfaces / LAN)";

    private WebInterfaceSettingsTab() {
    }

    /**
     * Constructs a {@link Tab} containing the full Web Interface configuration UI bound to
     * {@link WebInterfaceRuntime}.
     *
     * @param runtime active {@link WebInterfaceRuntime} coordinator
     * @return non-closable JavaFX {@link Tab}
     */
    public static Tab create(WebInterfaceRuntime runtime) {
        Tab tab = new Tab("🌐 Web Interface");
        tab.setClosable(false);
        try {
            tab.setContent(buildTabContent(runtime));
        } catch (IllegalStateException toolkitNotInitialized) {
            tab.setOnSelectionChanged(ev -> {
                if (tab.isSelected() && tab.getContent() == null) {
                    tab.setContent(buildTabContent(runtime));
                }
            });
        }
        return tab;
    }

    private static BorderPane buildTabContent(WebInterfaceRuntime runtime) {
        BorderPane outer = new BorderPane();
        outer.setPadding(new Insets(16));
        outer.getStyleClass().add("settings-tab-content");

        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("Browser Web Chat Interface (HTTP / HTTPS)");
        titleLabel.getStyleClass().add("settings-title-label");
        Label descLabel = new Label(
                "Configure Nadia's embedded HTTP & HTTPS web chat server. Access full multi-session chat, "
                + "@Staff specialist autocomplete, drag-and-drop file attachments, Markdown/Mermaid rendering, "
                + "RAG vector ingestion, and live Scheduled Tasks streaming over HTTP or TLS-encrypted HTTPS."
        );
        descLabel.getStyleClass().add("settings-info-label");
        descLabel.setWrapText(true);
        headerBox.getChildren().addAll(titleLabel, descLabel);
        BorderPane.setMargin(headerBox, new Insets(0, 0, 14, 0));
        outer.setTop(headerBox);

        WebInterfaceConfig initial = runtime.loadCurrentConfig();

        // ---------------------------------------------------------------------
        // 1. Live Server Status & Quick Actions Card
        // ---------------------------------------------------------------------
        VBox statusCard = createCard();
        Label statusHeader = createCardTitle("1. Live Server Status & Quick Actions");

        Label statusBadge = new Label();
        statusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label urlInfoLabel = new Label();
        urlInfoLabel.getStyleClass().add("settings-info-label");
        urlInfoLabel.setWrapText(true);

        Button startBtn = new Button("▶ Start");
        startBtn.getStyleClass().add("settings-btn");

        Button stopBtn = new Button("⏹ Stop");
        stopBtn.getStyleClass().add("settings-btn");

        Button restartBtn = new Button("↻ Restart");
        restartBtn.getStyleClass().add("settings-btn");

        Button openBrowserBtn = new Button("🌐 Open in Browser");
        openBrowserBtn.getStyleClass().add("chat-send-btn");
        openBrowserBtn.setTooltip(new Tooltip("Launch the Web Chat Interface in your default system browser"));

        Button copyUrlBtn = new Button("📋 Copy URL");
        copyUrlBtn.getStyleClass().add("settings-btn");
        copyUrlBtn.setTooltip(new Tooltip("Copy the active Web Interface URL to clipboard"));

        HBox actionRow = new HBox(8, startBtn, stopBtn, restartBtn, new Region(), copyUrlBtn, openBrowserBtn);
        HBox.setHgrow(actionRow.getChildren().get(3), Priority.ALWAYS);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        statusCard.getChildren().addAll(statusHeader, statusBadge, urlInfoLabel, actionRow);

        // ---------------------------------------------------------------------
        // 2. Protocol, Network & TLS Certificate Card
        // ---------------------------------------------------------------------
        VBox networkCard = createCard();
        Label networkHeader = createCardTitle("2. Protocol, Network & TLS Certificate");

        CheckBox enabledCheck = new CheckBox("Automatically start Web Interface server when Nadia launches");
        enabledCheck.setSelected(initial.enabled());

        GridPane netGrid = new GridPane();
        netGrid.setHgap(12);
        netGrid.setVgap(10);

        Label protocolLabel = new Label("Protocol:");
        ComboBox<String> protocolCombo = new ComboBox<>();
        protocolCombo.getItems().addAll(PROTOCOL_HTTP, PROTOCOL_HTTPS);
        protocolCombo.setValue(initial.tlsEnabled() ? PROTOCOL_HTTPS : PROTOCOL_HTTP);
        protocolCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(protocolCombo, Priority.ALWAYS);

        Label hostLabel = new Label("Bind Address:");
        ComboBox<String> hostCombo = new ComboBox<>();
        hostCombo.setEditable(true);
        hostCombo.getItems().addAll(BIND_LOCALHOST, BIND_ALL_INTERFACES);
        hostCombo.setMaxWidth(Double.MAX_VALUE);
        hostCombo.setValue("0.0.0.0".equals(initial.host()) ? BIND_ALL_INTERFACES
                : ("127.0.0.1".equals(initial.host()) ? BIND_LOCALHOST : initial.host()));
        GridPane.setHgrow(hostCombo, Priority.ALWAYS);

        Label portLabel = new Label("Server Port (1024–65535):");
        TextField portField = new TextField(String.valueOf(initial.port()));
        portField.setPromptText("8088");
        portField.setPrefWidth(140);

        Label pageTitleLabel = new Label("Browser Page Title:");
        TextField pageTitleField = new TextField(initial.pageTitle());
        pageTitleField.setPromptText(WebInterfaceConfig.DEFAULT_TITLE);
        GridPane.setHgrow(pageTitleField, Priority.ALWAYS);

        netGrid.add(protocolLabel, 0, 0);
        netGrid.add(protocolCombo, 1, 0);
        netGrid.add(hostLabel, 0, 1);
        netGrid.add(hostCombo, 1, 1);
        netGrid.add(portLabel, 0, 2);
        netGrid.add(portField, 1, 2);
        netGrid.add(pageTitleLabel, 0, 3);
        netGrid.add(pageTitleField, 1, 3);

        // Collapsible TLS / HTTPS Certificate Settings sub-panel
        VBox tlsBox = new VBox(8);
        tlsBox.setStyle("-fx-background-color: rgba(59, 130, 246, 0.07); -fx-border-color: -accent-color; "
                + "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

        Label tlsSubTitle = new Label("🔐 HTTPS / TLS Certificate Configuration");
        tlsSubTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -title-color;");

        Label tlsHint = new Label(
                "Leave 'Custom Keystore Path' blank to automatically generate and use a built-in RSA-2048 "
                + "self-signed certificate (with localhost & LAN IP SANs), or select your own PKCS#12 (.p12 / .pfx) or JKS (.jks) keystore."
        );
        tlsHint.getStyleClass().add("settings-info-label");
        tlsHint.setWrapText(true);

        TextField keystorePathField = new TextField(initial.keystorePath());
        keystorePathField.setPromptText("Optional: path to custom .p12 / .pfx / .jks keystore (blank = auto self-signed)");
        HBox.setHgrow(keystorePathField, Priority.ALWAYS);

        Button browseKeystoreBtn = new Button("📂 Browse...");
        browseKeystoreBtn.getStyleClass().add("settings-btn");
        browseKeystoreBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select PKCS#12 or JKS Keystore");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("TLS Keystores (*.p12, *.pfx, *.jks)", "*.p12", "*.pfx", "*.jks"),
                    new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
            );
            File selected = chooser.showOpenDialog(outer.getScene() != null ? outer.getScene().getWindow() : null);
            if (selected != null) {
                keystorePathField.setText(selected.getAbsolutePath());
            }
        });

        Button clearKeystoreBtn = new Button("Clear");
        clearKeystoreBtn.getStyleClass().add("settings-btn");
        clearKeystoreBtn.setOnAction(e -> keystorePathField.setText(""));

        HBox keystorePathRow = new HBox(8, new Label("Keystore Path:"), keystorePathField, browseKeystoreBtn, clearKeystoreBtn);
        keystorePathRow.setAlignment(Pos.CENTER_LEFT);

        PasswordField ksPassMask = new PasswordField();
        ksPassMask.setText(initial.keystorePassword());
        ksPassMask.setPromptText("Custom keystore password (if using custom keystore)");
        HBox.setHgrow(ksPassMask, Priority.ALWAYS);

        TextField ksPassPlain = new TextField(initial.keystorePassword());
        ksPassPlain.setPromptText("Custom keystore password (if using custom keystore)");
        ksPassPlain.setVisible(false);
        ksPassPlain.setManaged(false);
        HBox.setHgrow(ksPassPlain, Priority.ALWAYS);
        ksPassPlain.textProperty().bindBidirectional(ksPassMask.textProperty());

        CheckBox showKsPassCheck = new CheckBox("Show");
        showKsPassCheck.setOnAction(e -> {
            boolean show = showKsPassCheck.isSelected();
            ksPassPlain.setVisible(show);
            ksPassPlain.setManaged(show);
            ksPassMask.setVisible(!show);
            ksPassMask.setManaged(!show);
        });

        Button regenCertBtn = new Button("🔄 Regenerate Self-Signed Cert");
        regenCertBtn.getStyleClass().add("settings-btn");
        regenCertBtn.setTooltip(new Tooltip("Generate a fresh RSA-2048 self-signed certificate in ~/.nadia/plugins/data/web-interface/tls/"));

        HBox keystorePassRow = new HBox(8, new Label("Keystore Password:"), ksPassMask, ksPassPlain, showKsPassCheck, regenCertBtn);
        keystorePassRow.setAlignment(Pos.CENTER_LEFT);

        tlsBox.getChildren().addAll(tlsSubTitle, tlsHint, keystorePathRow, keystorePassRow);

        Runnable updateTlsVisibility = () -> {
            boolean httpsSelected = PROTOCOL_HTTPS.equals(protocolCombo.getValue());
            tlsBox.setVisible(httpsSelected);
            tlsBox.setManaged(httpsSelected);
        };
        protocolCombo.setOnAction(e -> updateTlsVisibility.run());
        updateTlsVisibility.run();

        networkCard.getChildren().addAll(networkHeader, enabledCheck, netGrid, tlsBox);

        // ---------------------------------------------------------------------
        // 3. Security, File Attachments & Cron Stream Card
        // ---------------------------------------------------------------------
        VBox securityCard = createCard();
        Label securityHeader = createCardTitle("3. Access Token, Attachments & Live Streams");

        Label authDesc = new Label(
                "Access Token (Optional — strongly recommended when binding to 0.0.0.0 LAN so remote browsers must authenticate):"
        );
        authDesc.getStyleClass().add("settings-info-label");
        authDesc.setWrapText(true);

        PasswordField tokenMaskField = new PasswordField();
        tokenMaskField.setText(initial.authToken());
        tokenMaskField.setPromptText("Leave blank for passwordless localhost access");
        HBox.setHgrow(tokenMaskField, Priority.ALWAYS);

        TextField tokenPlainField = new TextField(initial.authToken());
        tokenPlainField.setPromptText("Leave blank for passwordless localhost access");
        tokenPlainField.setVisible(false);
        tokenPlainField.setManaged(false);
        HBox.setHgrow(tokenPlainField, Priority.ALWAYS);
        tokenPlainField.textProperty().bindBidirectional(tokenMaskField.textProperty());

        CheckBox showTokenCheck = new CheckBox("Show");
        showTokenCheck.setOnAction(e -> {
            boolean show = showTokenCheck.isSelected();
            tokenPlainField.setVisible(show);
            tokenPlainField.setManaged(show);
            tokenMaskField.setVisible(!show);
            tokenMaskField.setManaged(!show);
        });

        Button generateTokenBtn = new Button("🎲 Generate Token");
        generateTokenBtn.getStyleClass().add("settings-btn");
        generateTokenBtn.setOnAction(e -> {
            tokenMaskField.setText(WebInterfaceConfig.generateRandomToken());
            showTokenCheck.setSelected(true);
            tokenPlainField.setVisible(true);
            tokenPlainField.setManaged(true);
            tokenMaskField.setVisible(false);
            tokenMaskField.setManaged(false);
        });

        Button clearTokenBtn = new Button("Clear");
        clearTokenBtn.getStyleClass().add("settings-btn");
        clearTokenBtn.setOnAction(e -> tokenMaskField.setText(""));

        HBox tokenRow = new HBox(8, tokenMaskField, tokenPlainField, showTokenCheck, generateTokenBtn, clearTokenBtn);
        tokenRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox allowUploadsCheck = new CheckBox("Allow file attachments & drag-and-drop uploads (📎)");
        allowUploadsCheck.setSelected(initial.allowUploads());

        Label maxMbLabel = new Label("Max Upload Size (MB):");
        TextField maxMbField = new TextField(String.valueOf(initial.maxUploadMb()));
        maxMbField.setPrefWidth(90);
        maxMbField.disableProperty().bind(allowUploadsCheck.selectedProperty().not());

        HBox uploadRow = new HBox(12, allowUploadsCheck, maxMbLabel, maxMbField);
        uploadRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox enableCronCheck = new CheckBox("Enable live '🕒 Scheduled Tasks' cron stream tab in browser");
        enableCronCheck.setSelected(initial.enableCronStream());

        securityCard.getChildren().addAll(securityHeader, authDesc, tokenRow, uploadRow, enableCronCheck);

        // ---------------------------------------------------------------------
        // 4. Save & Feedback Bar
        // ---------------------------------------------------------------------
        Label feedbackLabel = new Label();
        feedbackLabel.getStyleClass().add("settings-info-label");
        feedbackLabel.setWrapText(true);

        Button saveApplyBtn = new Button("💾 Save & Apply Web Interface Settings");
        saveApplyBtn.getStyleClass().add("chat-send-btn");

        HBox saveBar = new HBox(12, saveApplyBtn, feedbackLabel);
        saveBar.setAlignment(Pos.CENTER_LEFT);

        VBox scrollBody = new VBox(16, statusCard, networkCard, securityCard, saveBar);
        scrollBody.setPadding(new Insets(4));

        ScrollPane scrollPane = new ScrollPane(scrollBody);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        outer.setCenter(scrollPane);

        // ---------------------------------------------------------------------
        // Status Refresh & Event Wiring
        // ---------------------------------------------------------------------
        Runnable refreshStatusUi = () -> {
            WebInterfaceServer srv = runtime.getServer();
            WebInterfaceConfig cfg = runtime.loadCurrentConfig();
            boolean running = srv != null && srv.isRunning();
            startBtn.setDisable(running);
            stopBtn.setDisable(!running);

            if (running) {
                WebInterfaceConfig activeCfg = srv.getActiveConfig();
                String primaryUrl = srv.getPrimaryUrl();
                int clients = srv.getConnectedClientCount();
                String protoBadge = activeCfg.tlsEnabled() ? "HTTPS/TLS" : "HTTP";
                statusBadge.setText("● Running (" + protoBadge + ") on " + primaryUrl
                        + "  (" + clients + " browser tab" + (clients == 1 ? "" : "s") + " connected)");
                statusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");

                String tlsSummary = !activeCfg.tlsEnabled()
                        ? "Plain HTTP"
                        : (activeCfg.hasCustomKeystore() ? "🔐 Custom Keystore TLS" : "🔐 Auto Self-Signed TLS");
                List<String> lanUrls = srv.getLanUrls();
                if (!lanUrls.isEmpty()) {
                    urlInfoLabel.setText("LAN Access URLs: " + String.join("  |  ", lanUrls)
                            + "  ·  " + tlsSummary
                            + (activeCfg.isAuthRequired() ? "  ·  🔒 Token Protected" : "  ·  ⚠ No Access Token Set"));
                } else {
                    urlInfoLabel.setText("Bound to Localhost (" + activeCfg.host() + ")  ·  " + tlsSummary
                            + (activeCfg.isAuthRequired() ? "  ·  🔒 Access Token Active" : "  ·  Localhost Open Access"));
                }
            } else {
                String err = (srv != null) ? srv.getLastErrorMessage() : "";
                if (err != null && !err.isBlank()) {
                    statusBadge.setText("⚠ Server Error: " + err);
                    statusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                } else {
                    statusBadge.setText("○ Stopped (Configured for " + cfg.scheme() + "://" + cfg.host() + ":" + cfg.port() + ")");
                    statusBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #95a5a6;");
                }
                urlInfoLabel.setText("Click '▶ Start' or enable startup binding below to accept browser connections.");
            }
        };

        runtime.addStatusListener(() -> {
            if (Platform.isFxApplicationThread()) {
                refreshStatusUi.run();
            } else {
                Platform.runLater(refreshStatusUi);
            }
        });
        refreshStatusUi.run();

        regenCertBtn.setOnAction(e -> {
            try {
                Path certPath = runtime.regenerateSelfSignedCertificate();
                feedbackLabel.setText("🔐 Regenerated self-signed TLS keystore: " + certPath.getFileName());
            } catch (Exception ex) {
                feedbackLabel.setText("⚠ Failed regenerating certificate: " + ex.getMessage());
            }
            refreshStatusUi.run();
        });

        startBtn.setOnAction(e -> {
            boolean ok = runtime.startServerNow();
            feedbackLabel.setText(ok ? "Web Interface server started." : "Failed to start server—check port or keystore settings.");
            refreshStatusUi.run();
        });

        stopBtn.setOnAction(e -> {
            runtime.stopServerNow();
            feedbackLabel.setText("Web Interface server stopped.");
            refreshStatusUi.run();
        });

        restartBtn.setOnAction(e -> {
            boolean ok = runtime.startServerNow();
            feedbackLabel.setText(ok ? "Web Interface server restarted." : "Failed to restart server.");
            refreshStatusUi.run();
        });

        copyUrlBtn.setOnAction(e -> {
            WebInterfaceServer srv = runtime.getServer();
            WebInterfaceConfig cfg = runtime.loadCurrentConfig();
            String url = srv != null ? srv.getPrimaryUrl() : (cfg.scheme() + "://127.0.0.1:" + cfg.port());
            ClipboardContent cc = new ClipboardContent();
            cc.putString(url);
            Clipboard.getSystemClipboard().setContent(cc);
            feedbackLabel.setText("Copied " + url + " to clipboard.");
        });

        openBrowserBtn.setOnAction(e -> {
            WebInterfaceServer srv = runtime.getServer();
            if (srv == null || !srv.isRunning()) {
                runtime.startServerNow();
                srv = runtime.getServer();
            }
            WebInterfaceConfig cfg = runtime.loadCurrentConfig();
            String url = srv != null ? srv.getPrimaryUrl() : (cfg.scheme() + "://127.0.0.1:" + cfg.port());
            openUrlInSystemBrowser(url);
            feedbackLabel.setText("Opened " + url + " in system browser.");
            refreshStatusUi.run();
        });

        saveApplyBtn.setOnAction(e -> {
            int parsedPort;
            try {
                parsedPort = Integer.parseInt(portField.getText().trim());
                if (parsedPort < 1024 || parsedPort > 65535) {
                    feedbackLabel.setText("⚠ Port must be between 1024 and 65535.");
                    return;
                }
            } catch (NumberFormatException ex) {
                feedbackLabel.setText("⚠ Invalid port number.");
                return;
            }

            int parsedMaxMb = WebInterfaceConfig.DEFAULT_MAX_UPLOAD_MB;
            try {
                parsedMaxMb = WebInterfaceConfig.clampUploadMb(Integer.parseInt(maxMbField.getText().trim()));
                maxMbField.setText(String.valueOf(parsedMaxMb));
            } catch (NumberFormatException ignored) {
                maxMbField.setText(String.valueOf(parsedMaxMb));
            }

            String rawHostSelection = hostCombo.getEditor().getText() != null && !hostCombo.getEditor().getText().isBlank()
                    ? hostCombo.getEditor().getText().trim()
                    : hostCombo.getValue();
            String resolvedHost = parseHostSelection(rawHostSelection);
            boolean useHttps = PROTOCOL_HTTPS.equals(protocolCombo.getValue());

            WebInterfaceConfig updated = new WebInterfaceConfig(
                    enabledCheck.isSelected(),
                    useHttps,
                    resolvedHost,
                    parsedPort,
                    keystorePathField.getText(),
                    ksPassMask.getText(),
                    tokenMaskField.getText(),
                    pageTitleField.getText(),
                    allowUploadsCheck.isSelected(),
                    parsedMaxMb,
                    enableCronCheck.isSelected()
            );

            boolean ok = runtime.saveAndApplyConfig(updated);
            if (ok) {
                feedbackLabel.setText("✅ Saved " + updated.scheme().toUpperCase() + " settings to nadia.properties and applied live.");
            } else {
                WebInterfaceServer srv = runtime.getServer();
                String reason = (srv != null && !srv.getLastErrorMessage().isBlank()) ? srv.getLastErrorMessage() : ("port " + parsedPort);
                feedbackLabel.setText("⚠ Settings saved, but server failed to bind: " + reason);
            }
            refreshStatusUi.run();
        });

        return outer;
    }

    private static String parseHostSelection(String raw) {
        if (raw == null || raw.isBlank()) {
            return WebInterfaceConfig.DEFAULT_HOST;
        }
        if (raw.startsWith("127.0.0.1")) {
            return "127.0.0.1";
        }
        if (raw.startsWith("0.0.0.0")) {
            return "0.0.0.0";
        }
        return raw.trim();
    }

    private static VBox createCard() {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: -window-bg; -fx-border-color: -window-border; "
                + "-fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 15px;");
        return card;
    }

    private static Label createCardTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -title-color;");
        return lbl;
    }

    private static void openUrlInSystemBrowser(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
        } catch (Exception ignored) {
        }
    }
}
