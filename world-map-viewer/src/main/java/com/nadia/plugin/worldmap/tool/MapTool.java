package com.nadia.plugin.worldmap.tool;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaToolExtension;
import com.nadia.plugin.worldmap.WorldMapRuntime;
import com.nadia.plugin.worldmap.service.MapService;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PF4J {@link NadiaToolExtension} providing GIS mapping commands to control the
 * interactive World Map UI viewer,
 * plot geographical coordinates and moving entities, place marker flags, clear
 * maps, and toggle base/overlay layers.
 */
@Extension
public class MapTool implements NadiaToolExtension {

    private static final Logger logger = LoggerFactory.getLogger(MapTool.class);

    private final MapService mapService;
    private NadiaPluginContext context;

    /**
     * Constructs a {@code MapTool} extension wired to the active
     * {@link WorldMapRuntime}.
     *
     * @param context host {@link NadiaPluginContext} injected by Nadia's PF4J
     *                extension factory
     */
    public MapTool(NadiaPluginContext context) {
        this.context = context;
        this.mapService = WorldMapRuntime.getOrCreate(context).getMapService();
    }

    /**
     * Constructs a {@code MapTool} with explicit {@link MapService} and
     * {@link NadiaPluginContext} (for testing).
     *
     * @param mapService explicit map service instance
     * @param context    optional plugin context
     */
    public MapTool(MapService mapService, NadiaPluginContext context) {
        this.mapService = mapService;
        this.context = context;
    }

    private void openMapDockable() {
        if (context != null) {
            context.openDockable(WorldMapRuntime.DOCKABLE_ID);
        }
    }

    /**
     * Opens the World Map Viewer window in the desktop workspace.
     *
     * @return JSON string indicating operation outcome
     */
    @NadiaTool(name = "showMap", description = "Opens the World Map Viewer window in the workspace.", properties = {
            "nadia.prop.windy.api-key", "nadia.prop.webcams.windy.api-key" })
    public String showMap() {
        logger.info("[MapTool] showMap called");
        try {
            openMapDockable();
            return "{\"success\": true, \"message\": \"Successfully opened World Map Viewer window.\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error opening map", e);
            return "{\"error\": \"Failed to open map: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Plots an object's current coordinates (latitude and longitude) on the World
     * Map.
     * Sequential plots for the same object name connect with a path.
     *
     * @param name      the unique name or identifier of the object being tracked
     * @param latitude  the target latitude coordinate in decimal degrees
     * @param longitude the target longitude coordinate in decimal degrees
     * @return JSON string indicating plotting result
     */
    @NadiaTool(name = "plotObject", description = "Plots an object's current coordinates (latitude and longitude) on the World Map. Sequential plots for the same object name connect with a path.")
    public String plotObject(
            @NadiaToolParam(description = "Unique name or identifier of the object being tracked") String name,
            @NadiaToolParam(description = "Target latitude coordinate in decimal degrees") double latitude,
            @NadiaToolParam(description = "Target longitude coordinate in decimal degrees") double longitude) {
        logger.info("[MapTool] plotObject called for {}: lat={}, lon={}", name, latitude, longitude);
        if (name == null || name.trim().isEmpty()) {
            return "{\"error\": \"Object name 'name' is required.\"}";
        }
        try {
            mapService.plotObject(name, latitude, longitude);
            openMapDockable();
            return "{\"success\": true, \"message\": \"Successfully plotted object " + name + " at coordinate ("
                    + latitude + ", " + longitude + ")\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error plotting object", e);
            return "{\"error\": \"Failed to plot object: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Sets a colored marker flag on the World Map at the specified latitude and
     * longitude.
     *
     * @param latitude  the latitude coordinate for the flag marker
     * @param longitude the longitude coordinate for the flag marker
     * @param color     the color name or hex code for the flag marker (defaults to
     *                  red)
     * @return JSON string indicating marker result
     */
    @NadiaTool(name = "setFlag", description = "Sets a colored marker flag on the World Map at the specified latitude and longitude.")
    public String setFlag(
            @NadiaToolParam(description = "Latitude coordinate for the flag marker") double latitude,
            @NadiaToolParam(description = "Longitude coordinate for the flag marker") double longitude,
            @NadiaToolParam(description = "Color name or hex code for the flag marker (defaults to red)", required = false) String color) {
        logger.info("[MapTool] setFlag called at lat={}, lon={} with color: {}", latitude, longitude, color);
        String finalColor = (color == null || color.trim().isEmpty()) ? "red" : color.trim();
        try {
            mapService.setFlag(latitude, longitude, finalColor);
            openMapDockable();
            return "{\"success\": true, \"message\": \"Successfully set " + finalColor + " flag at coordinate ("
                    + latitude + ", " + longitude + ")\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting flag", e);
            return "{\"error\": \"Failed to set flag: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Clears all plotted paths, objects, and marker flags from the World Map.
     *
     * @return JSON string indicating clear status
     */
    @NadiaTool(name = "clearMap", description = "Clears all plotted paths, objects, and marker flags from the World Map.")
    public String clearMap() {
        logger.info("[MapTool] clearMap called");
        try {
            mapService.clearMap();
            return "{\"success\": true, \"message\": \"Successfully cleared World Map.\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error clearing map", e);
            return "{\"error\": \"Failed to clear map: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Switches the World Map base layer between Roadmap (false) and Satellite
     * Imagery (true).
     *
     * @param enableSatellite {@code true} for satellite imagery layer,
     *                        {@code false} for standard roadmap
     * @return JSON string indicating view mode change
     */
    @NadiaTool(name = "setMapSatelliteView", description = "Switches the World Map base layer between Roadmap (false) and Satellite Imagery (true).")
    public String setMapSatelliteView(
            @NadiaToolParam(description = "True for satellite imagery layer, false for standard roadmap") boolean enableSatellite) {
        logger.info("[MapTool] setMapSatelliteView called: enableSatellite={}", enableSatellite);
        try {
            mapService.setSatelliteView(enableSatellite);
            openMapDockable();
            return "{\"success\": true, \"satelliteView\": " + enableSatellite
                    + ", \"message\": \"World map switched to " + (enableSatellite ? "Satellite" : "Roadmap")
                    + " view.\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting satellite view", e);
            return "{\"error\": \"Failed to set satellite view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Switches the World Map base layer between Roadmap (false) and Google Terrain
     * topographic relief (true).
     *
     * @param enableTerrain {@code true} for topographic terrain layer,
     *                      {@code false} for standard roadmap
     * @return JSON string indicating terrain view mode status
     */
    @NadiaTool(name = "setMapTerrainView", description = "Switches the World Map base layer between Roadmap (false) and Google Terrain topographic relief with elevation contours (true).")
    public String setMapTerrainView(
            @NadiaToolParam(description = "True for topographic terrain layer, false for standard roadmap") boolean enableTerrain) {
        logger.info("[MapTool] setMapTerrainView called: enableTerrain={}", enableTerrain);
        try {
            mapService.setTerrainView(enableTerrain);
            openMapDockable();
            return "{\"success\": true, \"terrainView\": " + enableTerrain + ", \"message\": \"World map switched to "
                    + (enableTerrain ? "Terrain" : "Roadmap") + " view.\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting terrain view", e);
            return "{\"error\": \"Failed to set terrain view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the real-time solar terminator day/night illumination shadow on the
     * World Map.
     *
     * @param enableDayNight {@code true} to display the day/night shadow overlay,
     *                       {@code false} to hide it
     * @return JSON string indicating day/night mode status
     */
    @NadiaTool(name = "setMapDayNightView", description = "Toggles the real-time solar terminator day/night illumination shadow on the World Map.")
    public String setMapDayNightView(
            @NadiaToolParam(description = "True to display the day/night shadow overlay, false to hide it") boolean enableDayNight) {
        logger.info("[MapTool] setMapDayNightView called: enableDayNight={}", enableDayNight);
        try {
            mapService.setDayNightOverlay(enableDayNight);
            openMapDockable();
            return "{\"success\": true, \"dayNightView\": " + enableDayNight
                    + ", \"message\": \"World map day/night tracking " + (enableDayNight ? "enabled" : "disabled")
                    + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting day/night view", e);
            return "{\"error\": \"Failed to set day/night view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the real-time live traffic conditions and congestion flow overlay on
     * the World Map.
     *
     * @param enableTraffic {@code true} to display real-time traffic overlay on
     *                      roads, {@code false} to hide it
     * @return JSON string indicating traffic overlay status
     */
    @NadiaTool(name = "setMapTrafficView", description = "Toggles the real-time live traffic congestion overlay on the World Map (visible when zoomed in to roads).")
    public String setMapTrafficView(
            @NadiaToolParam(description = "True to display real-time traffic overlay on roads, false to hide it") boolean enableTraffic) {
        logger.info("[MapTool] setMapTrafficView called: enableTraffic={}", enableTraffic);
        try {
            mapService.setTrafficOverlay(enableTraffic);
            openMapDockable();
            return "{\"success\": true, \"trafficView\": " + enableTraffic + ", \"message\": \"World map traffic layer "
                    + (enableTraffic ? "enabled" : "disabled") + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting traffic view", e);
            return "{\"error\": \"Failed to set traffic view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the Google Public Transit routes and stations overlay on the World
     * Map.
     *
     * @param enableTransit {@code true} to display transit network lines and stops,
     *                      {@code false} to hide
     * @return JSON string indicating transit overlay status
     */
    @NadiaTool(name = "setMapTransitView", description = "Toggles the Google Public Transit network, subway/train lines, and transit stops overlay on the World Map.")
    public String setMapTransitView(
            @NadiaToolParam(description = "True to display transit network lines and stops, false to hide") boolean enableTransit) {
        logger.info("[MapTool] setMapTransitView called: enableTransit={}", enableTransit);
        try {
            mapService.setTransitOverlay(enableTransit);
            openMapDockable();
            return "{\"success\": true, \"transitView\": " + enableTransit + ", \"message\": \"World map transit layer "
                    + (enableTransit ? "enabled" : "disabled") + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting transit view", e);
            return "{\"error\": \"Failed to set transit view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the Google Biking and cycling trails overlay on the World Map.
     *
     * @param enableBiking {@code true} to display cycling network and trails,
     *                     {@code false} to hide
     * @return JSON string indicating biking overlay status
     */
    @NadiaTool(name = "setMapBikingView", description = "Toggles the Google cycling network, dedicated bike lanes, and trail paths overlay on the World Map.")
    public String setMapBikingView(
            @NadiaToolParam(description = "True to display cycling network and trails, false to hide") boolean enableBiking) {
        logger.info("[MapTool] setMapBikingView called: enableBiking={}", enableBiking);
        try {
            mapService.setBikingOverlay(enableBiking);
            openMapDockable();
            return "{\"success\": true, \"bikingView\": " + enableBiking + ", \"message\": \"World map biking layer "
                    + (enableBiking ? "enabled" : "disabled") + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting biking view", e);
            return "{\"error\": \"Failed to set biking view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the real-time global Air Quality Index (AQI) heatmap overlay on the
     * World Map.
     *
     * @param enableAirQuality {@code true} to display air quality heatmap and
     *                         stations, {@code false} to hide
     * @return JSON string indicating air quality overlay status
     */
    @NadiaTool(name = "setMapAirQualityView", description = "Toggles the real-time global Air Quality Index (AQI) heatmap and monitoring station overlay on the World Map.")
    public String setMapAirQualityView(
            @NadiaToolParam(description = "True to display air quality heatmap and stations, false to hide") boolean enableAirQuality) {
        logger.info("[MapTool] setMapAirQualityView called: enableAirQuality={}", enableAirQuality);
        try {
            mapService.setAirQualityOverlay(enableAirQuality);
            openMapDockable();
            return "{\"success\": true, \"airQualityView\": " + enableAirQuality
                    + ", \"message\": \"World map air quality layer " + (enableAirQuality ? "enabled" : "disabled")
                    + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting air quality view", e);
            return "{\"error\": \"Failed to set air quality view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the NASA FIRMS active wildfire hotspots and thermal anomaly
     * detections overlay on the World Map.
     *
     * @param enableWildfires {@code true} to display active wildfire hotspots,
     *                        {@code false} to hide
     * @return JSON string indicating wildfires overlay status
     */
    @NadiaTool(name = "setMapWildfiresView", description = "Toggles the NASA FIRMS active wildfire hotspots and thermal anomaly detections overlay on the World Map.")
    public String setMapWildfiresView(
            @NadiaToolParam(description = "True to display active wildfire hotspots, false to hide") boolean enableWildfires) {
        logger.info("[MapTool] setMapWildfiresView called: enableWildfires={}", enableWildfires);
        try {
            mapService.setWildfiresOverlay(enableWildfires);
            openMapDockable();
            return "{\"success\": true, \"wildfiresView\": " + enableWildfires
                    + ", \"message\": \"World map wildfires layer " + (enableWildfires ? "enabled" : "disabled")
                    + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting wildfires view", e);
            return "{\"error\": \"Failed to set wildfires view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the real-time global wind pattern particles and atmospheric flow
     * overlay on the World Map.
     *
     * @param enableWindy {@code true} to display dynamic wind streamlines,
     *                    {@code false} to hide
     * @return JSON string indicating wind patterns overlay status
     */
    @NadiaTool(name = "setMapWindyView", description = "Toggles the real-time global wind pattern particles and atmospheric flow overlay (powered by api.windy.com) on the World Map.", properties = {
            "nadia.prop.windy.api-key", "nadia.prop.webcams.windy.api-key" })
    public String setMapWindyView(
            @NadiaToolParam(description = "True to display dynamic wind streamlines, false to hide") boolean enableWindy) {
        logger.info("[MapTool] setMapWindyView called: enableWindy={}", enableWindy);
        try {
            mapService.setWindyOverlay(enableWindy);
            openMapDockable();
            return "{\"success\": true, \"windyView\": " + enableWindy
                    + ", \"message\": \"World map wind patterns layer " + (enableWindy ? "enabled" : "disabled")
                    + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting windy view", e);
            return "{\"error\": \"Failed to set windy view: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Toggles the OpenSeaMap nautical seamarks and marine navigation overlay on the
     * World Map.
     *
     * @param enableNautical {@code true} to display OpenSeaMap nautical seamarks,
     *                       buoys, beacons, and marine aids, {@code false} to hide
     * @return JSON string indicating nautical overlay status
     */
    @NadiaTool(name = "setMapNauticalView", description = "Toggles the OpenSeaMap nautical seamarks and marine navigation overlay on the World Map.")
    public String setMapNauticalView(
            @NadiaToolParam(description = "True to display OpenSeaMap nautical seamarks, false to hide") boolean enableNautical) {
        logger.info("[MapTool] setMapNauticalView called: enableNautical={}", enableNautical);
        try {
            mapService.setNauticalOverlay(enableNautical);
            openMapDockable();
            return "{\"success\": true, \"nauticalView\": " + enableNautical
                    + ", \"message\": \"World map nautical layer " + (enableNautical ? "enabled" : "disabled") + ".\"}";
        } catch (Exception e) {
            logger.error("[MapTool] Error setting nautical view", e);
            return "{\"error\": \"Failed to set nautical view: " + e.getMessage() + "\"}";
        }
    }
}
