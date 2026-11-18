package com.nadia.plugin.web;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaUiExtension;
import com.nadia.plugin.web.ui.WebInterfaceSettingsTab;
import javafx.scene.control.Tab;
import org.pf4j.Extension;

import java.util.Optional;

/**
 * PF4J {@link NadiaUiExtension} contributing the {@code 🌐 Web Interface} configuration page
 * inside Nadia's {@code SettingsWindowView}.
 */
@Extension
public class WebInterfaceUiExtension implements NadiaUiExtension {

    @Override
    public Optional<Tab> createSettingsTab(NadiaPluginContext context) {
        try {
            return Optional.of(WebInterfaceSettingsTab.create(WebInterfaceRuntime.getInstance(), context));
        } catch (Throwable headlessWithoutJavaFx) {
            // Headless CLI or unit-test environment where JavaFX Toolkit is not initialized
            return Optional.empty();
        }
    }
}
