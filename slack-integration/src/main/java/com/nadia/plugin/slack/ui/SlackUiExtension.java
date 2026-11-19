package com.nadia.plugin.slack.ui;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaUiExtension;
import com.nadia.plugin.slack.SlackRuntime;
import javafx.scene.control.Tab;
import org.pf4j.Extension;

import java.util.Optional;

/**
 * PF4J {@link NadiaUiExtension} contributing the localized
 * {@code Slack Integration} configuration tab
 * into Nadia's {@code SettingsWindowView}.
 */
@Extension
public class SlackUiExtension implements NadiaUiExtension {

    @Override
    public Optional<Tab> createSettingsTab(NadiaPluginContext context) {
        return Optional.of(SlackSettingsTab.create(SlackRuntime.getInstance(), context));
    }
}
