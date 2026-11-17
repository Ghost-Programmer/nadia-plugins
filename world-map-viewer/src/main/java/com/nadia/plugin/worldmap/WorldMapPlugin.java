package com.nadia.plugin.worldmap;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

/**
 * PF4J {@link NadiaPlugin} entry point for the Nadia World Map Viewer plugin
 * ({@code world-map-viewer}).
 *
 * <p>
 * Initializes the shared {@link WorldMapRuntime} (Leaflet WebView assets,
 * {@code MapService},
 * OpenSky {@code AviationService}, FAA Civil Aviation Registry lookup, and N2YO
 * {@code SatelliteService})
 * on startup and cleanly terminates background tracking executors on plugin
 * stop or unload.
 * </p>
 */
public class WorldMapPlugin extends NadiaPlugin {

    /**
     * Constructs the {@code WorldMapPlugin} bound to the given PF4J
     * {@link PluginWrapper}.
     *
     * @param wrapper the PF4J plugin wrapper
     */
    public WorldMapPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        WorldMapRuntime.getOrCreate(context);
        logger.info("[WorldMapPlugin] Started World Map Viewer plugin (id='{}').", WorldMapRuntime.PLUGIN_ID);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        WorldMapRuntime.shutdownCurrent();
        logger.info("[WorldMapPlugin] Stopped World Map Viewer plugin and terminated background trackers.");
    }
}
