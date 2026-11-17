package com.nadia.plugin.worldmap.tool;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaToolExtension;
import com.nadia.plugin.worldmap.WorldMapRuntime;
import com.nadia.plugin.worldmap.service.SatelliteService;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PF4J {@link NadiaToolExtension} providing satellite tracking capabilities,
 * live viewport query plotting,
 * background recurring tracking, status checks, and satellite category
 * exploration using N2YO.
 */
@Extension
public class SatelliteTool implements NadiaToolExtension {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteTool.class);

    private final SatelliteService satelliteService;
    private NadiaPluginContext context;

    /**
     * Constructs a {@code SatelliteTool} extension wired to the active
     * {@link WorldMapRuntime}.
     *
     * @param context host {@link NadiaPluginContext}
     */
    public SatelliteTool(NadiaPluginContext context) {
        this.context = context;
        this.satelliteService = WorldMapRuntime.getOrCreate(context).getSatelliteService();
    }

    /**
     * Constructs a {@code SatelliteTool} with explicit {@link SatelliteService} and
     * {@link NadiaPluginContext} (for testing).
     *
     * @param satelliteService explicit satellite service instance
     * @param context          optional plugin context
     */
    public SatelliteTool(SatelliteService satelliteService, NadiaPluginContext context) {
        this.satelliteService = satelliteService;
        this.context = context;
    }

    /**
     * One-time fetch of current live satellite positions within the map's current
     * viewport bounds.
     *
     * @param categoryId optional satellite category filter (e.g. 0 for All, 2 for
     *                   ISS, 30 for Military)
     * @return JSON string of tracked satellite positions plotted on the map
     */
    @NadiaTool(name = "trackSatellites", description = "One-time fetch of current live satellite positions within the map's current viewport bounds, plotting them as path points on the World Map. Optionally filter by categoryId (default 0 for All, 2 for ISS, 18 for Amateur Radio, 30 for Military).", properties = {
            "nadia.prop.n2yo.apiKey" })
    public String trackSatellites(
            @NadiaToolParam(description = "Optional N2YO satellite category ID (0 for All, 2 for ISS, 18 for Amateur Radio, 30 for Military)", required = false) Integer categoryId) {
        int cat = categoryId == null ? 0 : categoryId;
        logger.info("[SatelliteTool] trackSatellites called with categoryId: {}", cat);
        try {
            if (context != null) {
                context.openDockable(WorldMapRuntime.DOCKABLE_ID);
            }
            return satelliteService.trackSatellitesInCurrentView(cat);
        } catch (Exception e) {
            logger.error("[SatelliteTool] Error tracking satellites", e);
            return "{\"error\": \"Failed to track satellites: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Starts a background scheduler task that periodically queries and updates
     * satellite positions in the current viewport.
     *
     * @param intervalSeconds polling interval in seconds (minimum 5s, recommended
     *                        30s)
     * @param categoryId      optional category filter ID
     * @return JSON string confirming tracking initiation
     */
    @NadiaTool(name = "startContinuousSatelliteTracking", description = "Starts a background scheduler task that periodically queries and updates satellite positions in the current viewport every N seconds (minimum 5s, recommended 30s to respect N2YO rate limits). Optionally filter by categoryId.", properties = {
            "nadia.prop.n2yo.apiKey" })
    public String startContinuousSatelliteTracking(
            @NadiaToolParam(description = "Polling interval in seconds (minimum 5s, recommended 30s)") int intervalSeconds,
            @NadiaToolParam(description = "Optional N2YO satellite category ID (default 0 for All)", required = false) Integer categoryId) {
        int cat = categoryId == null ? 0 : categoryId;
        logger.info("[SatelliteTool] startContinuousSatelliteTracking called with interval: {}s, categoryId: {}",
                intervalSeconds, cat);
        try {
            satelliteService.startContinuousTracking(intervalSeconds, cat);
            return String.format(
                    "{\"success\": true, \"message\": \"Successfully started background satellite tracking every %d seconds for category %d.\"}",
                    satelliteService.getTrackingInterval(), satelliteService.getTrackingCategoryId());
        } catch (Exception e) {
            logger.error("[SatelliteTool] Error starting continuous satellite tracking", e);
            return "{\"error\": \"Failed to start satellite tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Stops the active background satellite tracking scheduler task.
     *
     * @return JSON string confirming cancellation
     */
    @NadiaTool(name = "stopContinuousSatelliteTracking", description = "Stops the background satellite tracking scheduler task.")
    public String stopContinuousSatelliteTracking() {
        logger.info("[SatelliteTool] stopContinuousSatelliteTracking called");
        try {
            satelliteService.stopContinuousTracking();
            return "{\"success\": true, \"message\": \"Successfully stopped background satellite tracking.\"}";
        } catch (Exception e) {
            logger.error("[SatelliteTool] Error stopping satellite tracking", e);
            return "{\"error\": \"Failed to stop satellite tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Checks whether background satellite tracking is active and returns its
     * current configuration.
     *
     * @return JSON string with active flag, interval seconds, and category ID
     */
    @NadiaTool(name = "getSatelliteTrackingStatus", description = "Checks whether background satellite tracking is active and returns its current configuration.")
    public String getSatelliteTrackingStatus() {
        logger.info("[SatelliteTool] getSatelliteTrackingStatus called");
        boolean active = satelliteService.isTrackingActive();
        int interval = satelliteService.getTrackingInterval();
        int categoryId = satelliteService.getTrackingCategoryId();
        return String.format("{\"active\": %b, \"interval_seconds\": %d, \"category_id\": %d}", active, interval,
                categoryId);
    }

    /**
     * Returns the complete list of N2YO satellite category names and their
     * corresponding IDs.
     *
     * @return JSON string of satellite categories
     */
    @NadiaTool(name = "getSatelliteCategories", description = "Returns the complete list of N2YO satellite category names and their corresponding IDs, which can be used to filter satellite tracking queries.")
    public String getSatelliteCategories() {
        logger.info("[SatelliteTool] getSatelliteCategories called");
        return "{\"categories\": [" +
                "{\"id\": 0, \"name\": \"All\"}," +
                "{\"id\": 1, \"name\": \"Brightest\"}," +
                "{\"id\": 2, \"name\": \"ISS\"}," +
                "{\"id\": 6, \"name\": \"Earth resources\"}," +
                "{\"id\": 8, \"name\": \"Disaster monitoring\"}," +
                "{\"id\": 13, \"name\": \"Raduga\"}," +
                "{\"id\": 14, \"name\": \"Molniya\"}," +
                "{\"id\": 15, \"name\": \"Iridium\"}," +
                "{\"id\": 16, \"name\": \"Orbcomm\"}," +
                "{\"id\": 18, \"name\": \"Amateur radio\"}," +
                "{\"id\": 19, \"name\": \"Experimental\"}," +
                "{\"id\": 22, \"name\": \"Galileo\"}," +
                "{\"id\": 24, \"name\": \"Navy Navigation Satellite System\"}," +
                "{\"id\": 25, \"name\": \"Russian LEO Navigation\"}," +
                "{\"id\": 28, \"name\": \"Engineering\"}," +
                "{\"id\": 29, \"name\": \"Education\"}," +
                "{\"id\": 30, \"name\": \"Military\"}," +
                "{\"id\": 31, \"name\": \"Radar Calibration\"}," +
                "{\"id\": 32, \"name\": \"CubeSats\"}," +
                "{\"id\": 35, \"name\": \"Beidou Navigation System\"}," +
                "{\"id\": 38, \"name\": \"Parus\"}," +
                "{\"id\": 43, \"name\": \"O3B Networks\"}," +
                "{\"id\": 45, \"name\": \"Celestis\"}," +
                "{\"id\": 46, \"name\": \"IRNSS\"}," +
                "{\"id\": 47, \"name\": \"QZSS\"}," +
                "{\"id\": 48, \"name\": \"Flock\"}," +
                "{\"id\": 49, \"name\": \"Lemur\"}," +
                "{\"id\": 53, \"name\": \"OneWeb\"}," +
                "{\"id\": 54, \"name\": \"Chinese Space Station\"}," +
                "{\"id\": 55, \"name\": \"Qianfan\"}," +
                "{\"id\": 56, \"name\": \"Leo\"}," +
                "{\"id\": 57, \"name\": \"GeeSAT\"}" +
                "]}";
    }
}
