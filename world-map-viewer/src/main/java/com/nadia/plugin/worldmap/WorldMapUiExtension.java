package com.nadia.plugin.worldmap;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaUiExtension;
import com.nadia.app.plugin.api.PluginDockableDescriptor;
import com.nadia.plugin.worldmap.ui.MapWindowView;
import org.pf4j.Extension;

import java.util.Optional;

/**
 * PF4J {@link NadiaUiExtension} contributing the interactive
 * {@link MapWindowView} BentoFX dockable
 * workspace window ({@code "map"} / {@code "🗺️ World Map Viewer"}) to Nadia's
 * desktop shell and View menu.
 */
@Extension
public class WorldMapUiExtension implements NadiaUiExtension {

    /**
     * Default no-arg constructor for PF4J extension instantiation.
     */
    public WorldMapUiExtension() {
    }

    @Override
    public Optional<PluginDockableDescriptor> createDockableWindow(NadiaPluginContext context) {
        WorldMapRuntime runtime = WorldMapRuntime.getOrCreate(context);
        return Optional.of(new PluginDockableDescriptor(
                WorldMapRuntime.DOCKABLE_ID,
                "🗺️ World Map Viewer",
                "left-top",
                () -> new MapWindowView(
                        runtime.getMapService(),
                        runtime.getAviationService(),
                        runtime.getSatelliteService(),
                        runtime.getEarthquakeService(),
                        runtime.getExtractedMapHtmlUri())));
    }
}
