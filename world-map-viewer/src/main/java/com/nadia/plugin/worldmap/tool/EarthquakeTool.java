package com.nadia.plugin.worldmap.tool;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaToolExtension;
import com.nadia.plugin.worldmap.WorldMapRuntime;
import com.nadia.plugin.worldmap.service.EarthquakeService;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * PF4J {@link NadiaToolExtension} enabling LLM agents to query real-time USGS seismic telemetry,
 * configure the 1-to-7 day earthquake lookback window, plot magnitude-scaled earthquake epicenters
 * on the World Map Viewer, and manage continuous background earthquake monitoring.
 */
@Extension
public class EarthquakeTool implements NadiaToolExtension {

    private static final Logger logger = LoggerFactory.getLogger(EarthquakeTool.class);

    private final EarthquakeService earthquakeService;
    private NadiaPluginContext context;

    /**
     * Constructs an {@code EarthquakeTool} extension wired to the active {@link WorldMapRuntime}.
     *
     * @param context host {@link NadiaPluginContext}
     */
    public EarthquakeTool(NadiaPluginContext context) {
        this.context = context;
        this.earthquakeService = WorldMapRuntime.getOrCreate(context).getEarthquakeService();
    }

    /**
     * Constructs an {@code EarthquakeTool} with explicit {@link EarthquakeService} and {@link NadiaPluginContext} (for testing).
     *
     * @param earthquakeService explicit earthquake tracking service
     * @param context           optional plugin context
     */
    public EarthquakeTool(EarthquakeService earthquakeService, NadiaPluginContext context) {
        this.earthquakeService = earthquakeService;
        this.context = context;
    }

    private int resolveLookbackHours(Integer lookbackDays, Integer lookbackHours, int defaultHours) {
        if (lookbackDays != null) {
            return EarthquakeService.clampLookbackDays(lookbackDays) * 24;
        }
        if (lookbackHours != null) {
            return lookbackHours;
        }
        return defaultHours;
    }

    /**
     * One-time fetch of recent USGS earthquakes within the World Map's current viewport bounds,
     * plotting magnitude-scaled epicenters on the map.
     *
     * @param minMagnitude  optional minimum Richter magnitude filter (default 2.5)
     * @param lookbackDays  optional lookback window in days from 1 to 7 (overrides lookbackHours when provided)
     * @param lookbackHours optional lookback window in hours (defaults to active lookback window, e.g. 24h)
     * @return JSON response detailing count and strongest earthquake plotted
     */
    @NadiaTool(
            name = "trackEarthquakes",
            description = "One-time fetch of recent USGS earthquakes within the World Map's current viewport bounds over the past 1 to 7 days, plotting magnitude-scaled seismic epicenters on the map."
    )
    public String trackEarthquakes(
            @NadiaToolParam(description = "Optional minimum Richter magnitude threshold (default 2.5)", required = false) Double minMagnitude,
            @NadiaToolParam(description = "Optional lookback window in days from 1 to 7", required = false) Integer lookbackDays,
            @NadiaToolParam(description = "Optional lookback window in hours (default uses configured 1-7 day window)", required = false) Integer lookbackHours) {
        double mag = minMagnitude != null ? minMagnitude : 2.5;
        int defaultHours = earthquakeService.getLookbackHours() > 0 ? earthquakeService.getLookbackHours() : 24;
        int hours = resolveLookbackHours(lookbackDays, lookbackHours, defaultHours);
        if (lookbackDays != null) {
            earthquakeService.setLookbackDays(lookbackDays);
        }
        logger.info("[EarthquakeTool] trackEarthquakes called: minMagnitude={}, lookbackHours={}", mag, hours);
        try {
            if (context != null) {
                context.openDockable(WorldMapRuntime.DOCKABLE_ID);
            }
            return earthquakeService.trackEarthquakesInCurrentView(mag, hours);
        } catch (Exception e) {
            logger.error("[EarthquakeTool] Error tracking earthquakes in current view", e);
            return "{\"error\": \"Failed to track earthquakes: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Sets the World Map earthquake history window to the past 1 to 7 days and refreshes plotted earthquakes.
     *
     * @param days number of past days to show (1 to 7)
     * @return JSON confirmation string with the applied day count
     */
    @NadiaTool(
            name = "setEarthquakeLookbackDays",
            description = "Sets the World Map Viewer earthquake history window to show earthquakes from the past 1 to 7 days and refreshes active earthquake markers."
    )
    public String setEarthquakeLookbackDays(
            @NadiaToolParam(description = "Number of past days of earthquakes to display (1 to 7)") int days) {
        logger.info("[EarthquakeTool] setEarthquakeLookbackDays called: days={}", days);
        try {
            int appliedDays = earthquakeService.setLookbackDays(days);
            if (context != null) {
                context.openDockable(WorldMapRuntime.DOCKABLE_ID);
            }
            return String.format(Locale.US,
                    "{\"success\": true, \"lookback_days\": %d, \"lookback_hours\": %d, \"message\": \"World Map earthquake window set to past %d %s.\"}",
                    appliedDays,
                    appliedDays * 24,
                    appliedDays,
                    appliedDays == 1 ? "day" : "days");
        } catch (Exception e) {
            logger.error("[EarthquakeTool] Error setting earthquake lookback days", e);
            return "{\"error\": \"Failed to set earthquake lookback days: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Fetches and plots recent significant USGS earthquakes worldwide across the entire globe,
     * independent of the current map viewport zoom level.
     *
     * @param minMagnitude  optional minimum Richter magnitude filter (default 4.5)
     * @param lookbackDays  optional lookback window in days from 1 to 7 (default 7 days)
     * @param lookbackHours optional lookback window in hours (default 168 for 7 days, max 720)
     * @param limit         optional maximum number of earthquakes to plot (default 50, max 500)
     * @return JSON response detailing count and strongest global earthquake plotted
     */
    @NadiaTool(
            name = "trackGlobalEarthquakes",
            description = "Fetches and plots recent significant USGS earthquakes across the entire globe regardless of current viewport zoom over the past 1 to 7 days (default M4.5+ over 7 days)."
    )
    public String trackGlobalEarthquakes(
            @NadiaToolParam(description = "Optional minimum Richter magnitude threshold (default 4.5)", required = false) Double minMagnitude,
            @NadiaToolParam(description = "Optional lookback window in days from 1 to 7 (default 7)", required = false) Integer lookbackDays,
            @NadiaToolParam(description = "Optional lookback window in hours (default 168 for 7 days, max 720)", required = false) Integer lookbackHours,
            @NadiaToolParam(description = "Optional maximum number of earthquakes to return (default 50, max 500)", required = false) Integer limit) {
        double mag = minMagnitude != null ? minMagnitude : 4.5;
        int hours = resolveLookbackHours(lookbackDays, lookbackHours, 168);
        int maxEvents = limit != null ? limit : 50;
        logger.info("[EarthquakeTool] trackGlobalEarthquakes called: minMagnitude={}, lookbackHours={}, limit={}", mag, hours, maxEvents);
        try {
            if (context != null) {
                context.openDockable(WorldMapRuntime.DOCKABLE_ID);
            }
            return earthquakeService.fetchAndPlotGlobalEarthquakes(mag, hours, maxEvents);
        } catch (Exception e) {
            logger.error("[EarthquakeTool] Error tracking global earthquakes", e);
            return "{\"error\": \"Failed to track global earthquakes: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Starts a background scheduler task that periodically queries and updates USGS earthquakes
     * in the active World Map viewport.
     *
     * @param intervalSeconds polling interval in seconds (minimum 15s, recommended 60s)
     * @param minMagnitude    optional minimum Richter magnitude threshold (default 2.5)
     * @param lookbackDays    optional lookback window in days from 1 to 7 (default uses configured 1-7 day window)
     * @param lookbackHours   optional lookback window in hours
     * @return JSON confirmation string
     */
    @NadiaTool(
            name = "startContinuousEarthquakeTracking",
            description = "Starts a background scheduler task that periodically queries and updates USGS earthquakes in the active World Map viewport every N seconds over a 1 to 7 day window."
    )
    public String startContinuousEarthquakeTracking(
            @NadiaToolParam(description = "Polling interval in seconds (minimum 15s, recommended 60s)") int intervalSeconds,
            @NadiaToolParam(description = "Optional minimum Richter magnitude threshold (default 2.5)", required = false) Double minMagnitude,
            @NadiaToolParam(description = "Optional lookback window in days from 1 to 7", required = false) Integer lookbackDays,
            @NadiaToolParam(description = "Optional lookback window in hours (default uses configured 1-7 day window)", required = false) Integer lookbackHours) {
        double mag = minMagnitude != null ? minMagnitude : 2.5;
        int defaultHours = earthquakeService.getLookbackHours() > 0 ? earthquakeService.getLookbackHours() : 24;
        int hours = resolveLookbackHours(lookbackDays, lookbackHours, defaultHours);
        logger.info("[EarthquakeTool] startContinuousEarthquakeTracking called: interval={}s, minMag={}, hours={}", intervalSeconds, mag, hours);
        try {
            if (context != null) {
                context.openDockable(WorldMapRuntime.DOCKABLE_ID);
            }
            earthquakeService.startContinuousTracking(intervalSeconds, mag, hours);
            return String.format(Locale.US,
                    "{\"success\": true, \"message\": \"Successfully started background USGS earthquake tracking every %d seconds (M%.1f+, %d-day / %dh lookback).\"}",
                    earthquakeService.getTrackingInterval(),
                    earthquakeService.getMinMagnitude(),
                    earthquakeService.getLookbackDays(),
                    earthquakeService.getLookbackHours());
        } catch (Exception e) {
            logger.error("[EarthquakeTool] Error starting continuous earthquake tracking", e);
            return "{\"error\": \"Failed to start earthquake tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Stops the active background USGS earthquake tracking scheduler task.
     *
     * @return JSON confirmation string
     */
    @NadiaTool(
            name = "stopContinuousEarthquakeTracking",
            description = "Stops the background USGS earthquake tracking scheduler task."
    )
    public String stopContinuousEarthquakeTracking() {
        logger.info("[EarthquakeTool] stopContinuousEarthquakeTracking called");
        try {
            earthquakeService.stopContinuousTracking();
            return "{\"success\": true, \"message\": \"Successfully stopped background earthquake tracking.\"}";
        } catch (Exception e) {
            logger.error("[EarthquakeTool] Error stopping earthquake tracking", e);
            return "{\"error\": \"Failed to stop earthquake tracking: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Checks whether background USGS earthquake tracking is active and returns its current configuration.
     *
     * @return JSON status string
     */
    @NadiaTool(
            name = "getEarthquakeTrackingStatus",
            description = "Checks whether background USGS earthquake tracking is active and returns its current interval, magnitude filter, and lookback window (days and hours)."
    )
    public String getEarthquakeTrackingStatus() {
        logger.info("[EarthquakeTool] getEarthquakeTrackingStatus called");
        boolean active = earthquakeService.isTrackingActive();
        int interval = earthquakeService.getTrackingInterval();
        double minMag = earthquakeService.getMinMagnitude();
        int days = earthquakeService.getLookbackDays();
        int hours = earthquakeService.getLookbackHours();
        return String.format(Locale.US,
                "{\"active\": %b, \"interval_seconds\": %d, \"min_magnitude\": %.1f, \"lookback_days\": %d, \"lookback_hours\": %d}",
                active, interval, minMag, days, hours);
    }
}
