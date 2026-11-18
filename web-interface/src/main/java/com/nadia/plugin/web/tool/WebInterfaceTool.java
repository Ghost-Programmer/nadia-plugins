package com.nadia.plugin.web.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.plugin.api.NadiaToolExtension;
import com.nadia.plugin.web.WebInterfaceRuntime;
import com.nadia.plugin.web.config.WebInterfaceConfig;
import com.nadia.plugin.web.server.WebInterfaceServer;
import org.pf4j.Extension;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PF4J {@link NadiaToolExtension} exposing status and URL discovery tools for the Nadia Web Interface.
 */
@Extension
public class WebInterfaceTool implements NadiaToolExtension {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns the live operational status, bound URL, LAN addresses, and configuration summary
     * of Nadia's browser-based Web Chat Interface.
     *
     * @return JSON status summary
     */
    @NadiaTool(
            description = "Returns the current status, browser URL, LAN URLs, port, authentication requirement, and connected browser count of the Nadia Web Chat Interface."
    )
    public String getWebInterfaceStatus() {
        WebInterfaceRuntime runtime = WebInterfaceRuntime.getInstance();
        WebInterfaceServer server = runtime.getServer();
        WebInterfaceConfig cfg = runtime.loadCurrentConfig();

        Map<String, Object> result = new LinkedHashMap<>();
        boolean running = server != null && server.isRunning();
        result.put("running", running);
        result.put("protocol", cfg.scheme());
        result.put("tlsMode", !cfg.tlsEnabled() ? "none" : (cfg.hasCustomKeystore() ? "custom-keystore" : "auto-self-signed"));
        result.put("enabledOnStartup", cfg.enabled());
        result.put("host", cfg.host());
        result.put("port", running ? server.getActualBoundPort() : cfg.port());
        result.put("url", server != null ? server.getPrimaryUrl() : (cfg.scheme() + "://127.0.0.1:" + cfg.port()));
        result.put("lanUrls", server != null ? server.getLanUrls() : java.util.List.of());
        result.put("authRequired", cfg.isAuthRequired());
        result.put("allowUploads", cfg.allowUploads());
        result.put("maxUploadMb", cfg.maxUploadMb());
        result.put("enableCronStream", cfg.enableCronStream());
        result.put("connectedBrowserTabs", server != null ? server.getConnectedClientCount() : 0);
        if (server != null && !server.getLastErrorMessage().isBlank()) {
            result.put("lastError", server.getLastErrorMessage());
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"running\":" + running + "}";
        }
    }
}

