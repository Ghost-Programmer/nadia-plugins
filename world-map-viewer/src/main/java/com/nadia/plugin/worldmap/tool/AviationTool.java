package com.nadia.plugin.worldmap.tool;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaToolExtension;
import com.nadia.plugin.worldmap.WorldMapRuntime;
import com.nadia.plugin.worldmap.service.AviationService;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PF4J {@link NadiaToolExtension} enabling LLM agents to query live OpenSky
 * aircraft telemetry
 * and plot flights on the World Map Viewer.
 */
@Extension
public class AviationTool implements NadiaToolExtension {

    private static final Logger logger = LoggerFactory.getLogger(AviationTool.class);

    private final AviationService aviationService;
    private NadiaPluginContext context;

    /**
     * Constructs an {@code AviationTool} extension wired to the active
     * {@link WorldMapRuntime}.
     *
     * @param context host {@link NadiaPluginContext}
     */
    public AviationTool(NadiaPluginContext context) {
        this.context = context;
        this.aviationService = WorldMapRuntime.getOrCreate(context).getAviationService();
    }

    /**
     * Constructs an {@code AviationTool} with explicit {@link AviationService} and
     * {@link NadiaPluginContext} (for testing).
     *
     * @param aviationService explicit aviation service instance
     * @param context         optional plugin context
     */
    public AviationTool(AviationService aviationService, NadiaPluginContext context) {
        this.aviationService = aviationService;
        this.context = context;
    }

    /**
     * One-time fetch of current live aircraft positions within the map's current
     * viewport bounds,
     * plotting them as path points on the World Map.
     *
     * @return JSON response detailing count and positions of tracked flights
     */
    @NadiaTool(name = "trackPlanes", description = "One-time fetch of current live aircraft positions within the map's current viewport bounds, plotting them as path points on the World Map.", properties = {
            "nadia.prop.opensky.username", "nadia.prop.opensky.password" })
    public String trackPlanes() {
        logger.info("[AviationTool] trackPlanes called");
        try {
            if (context != null) {
                context.openDockable(WorldMapRuntime.DOCKABLE_ID);
            }
            return aviationService.trackPlanesInCurrentView();
        } catch (Exception e) {
            logger.error("[AviationTool] Error tracking planes", e);
            return "{\"error\": \"Failed to track planes: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Starts a background daemon thread that periodically queries and updates
     * flight positions
     * in the current viewport every N seconds.
     *
     * @param intervalSeconds polling interval in seconds (minimum 5s, recommended
     *                        15-30s)
     * @return JSON response confirming tracking status
     */
    @NadiaTool(name = "startContinuousPlaneTracking", description = "Starts a background daemon thread that periodically queries and updates flight positions in the current viewport every N seconds (minimum 5s, recommended 15-30s to respect rate limits).", properties = {
            "nadia.prop.opensky.username", "nadia.prop.opensky.password" })
    public String startContinuousPlaneTracking(
            @NadiaToolParam(description = "Polling interval in seconds (minimum 5s, recommended 15-30s)") int intervalSeconds) {
        logger.info("[AviationTool] startContinuousPlaneTracking called with interval: {}s", intervalSeconds);
        try {
            aviationService.startContinuousTracking(intervalSeconds);
            return String.format(
                    "{\"success\": true, \"message\": \"Successfully started background flight tracking every %d seconds.\"}",
                    aviationService.getTrackingInterval());
        } catch (Exception e) {
            logger.error("[AviationTool] Error starting continuous plane tracking", e);
            return "{\"error\": \"Failed to start plane tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Stops the background flight tracking scheduler task.
     *
     * @return JSON response confirming tracking termination
     */
    @NadiaTool(name = "stopContinuousPlaneTracking", description = "Stops the background flight tracking scheduler task.")
    public String stopContinuousPlaneTracking() {
        logger.info("[AviationTool] stopContinuousPlaneTracking called");
        try {
            aviationService.stopContinuousTracking();
            return "{\"success\": true, \"message\": \"Successfully stopped background flight tracking.\"}";
        } catch (Exception e) {
            logger.error("[AviationTool] Error stopping flight tracking", e);
            return "{\"error\": \"Failed to stop flight tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Checks whether background plane tracking is active and returns its current
     * configuration.
     *
     * @return JSON string with active boolean and interval seconds
     */
    @NadiaTool(name = "getPlaneTrackingStatus", description = "Checks whether background plane tracking is active and returns its current configuration.")
    public String getPlaneTrackingStatus() {
        logger.info("[AviationTool] getPlaneTrackingStatus called");
        boolean active = aviationService.isTrackingActive();
        int interval = aviationService.getTrackingInterval();
        return String.format("{\"active\": %b, \"interval_seconds\": %d}", active, interval);
    }
}
