package com.nadia.plugin.worldmap.tool;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.worldmap.WorldMapRuntime;
import com.nadia.plugin.worldmap.service.MapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MapToolTest {

    private MapService mapService;
    private List<String> openedDockables;
    private NadiaPluginContext context;
    private MapTool mapTool;

    @BeforeEach
    public void setUp() {
        mapService = new MapService();
        openedDockables = new ArrayList<>();
        context = mock(NadiaPluginContext.class);
        doAnswer(inv -> {
            openedDockables.add(inv.getArgument(0));
            return null;
        }).when(context).openDockable(anyString());
        mapTool = new MapTool(mapService, context);
    }

    @Test
    public void testShowMap() {
        String result = mapTool.showMap();
        assertTrue(result.contains("\"success\": true"));
        assertEquals(List.of(WorldMapRuntime.DOCKABLE_ID), openedDockables);
    }

    @Test
    public void testPlotObjectAndClear() {
        String plotRes = mapTool.plotObject("flight-1", 30.2672, -97.7431);
        assertTrue(plotRes.contains("\"success\": true"));
        assertEquals(1, mapService.getObjects().size());
        assertEquals("flight-1", mapService.getObjects().get(0).name);

        String clearRes = mapTool.clearMap();
        assertTrue(clearRes.contains("\"success\": true"));
        assertEquals(0, mapService.getObjects().size());
    }

    @Test
    public void testSetFlag() {
        String flagRes = mapTool.setFlag(40.7128, -74.0060, "blue");
        assertTrue(flagRes.contains("\"success\": true"));
        assertEquals(1, mapService.getFlags().size());
        assertEquals("blue", mapService.getFlags().get(0).color);
    }

    @Test
    public void testSetMapSatelliteView() {
        assertFalse(mapService.isSatelliteView());

        String enableRes = mapTool.setMapSatelliteView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"satelliteView\": true"));
        assertTrue(mapService.isSatelliteView());

        String disableRes = mapTool.setMapSatelliteView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"satelliteView\": false"));
        assertFalse(mapService.isSatelliteView());
    }

    @Test
    public void testSetMapTerrainView() {
        assertFalse(mapService.isTerrainView());

        String enableRes = mapTool.setMapTerrainView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"terrainView\": true"));
        assertTrue(mapService.isTerrainView());

        String disableRes = mapTool.setMapTerrainView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"terrainView\": false"));
        assertFalse(mapService.isTerrainView());
    }

    @Test
    public void testSetMapDayNightView() {
        assertTrue(mapService.isDayNightOverlay());

        String disableRes = mapTool.setMapDayNightView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"dayNightView\": false"));
        assertFalse(mapService.isDayNightOverlay());

        String enableRes = mapTool.setMapDayNightView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"dayNightView\": true"));
        assertTrue(mapService.isDayNightOverlay());
    }

    @Test
    public void testSetMapTrafficView() {
        assertTrue(mapService.isTrafficOverlay());

        String disableRes = mapTool.setMapTrafficView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"trafficView\": false"));
        assertFalse(mapService.isTrafficOverlay());

        String enableRes = mapTool.setMapTrafficView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"trafficView\": true"));
        assertTrue(mapService.isTrafficOverlay());
    }

    @Test
    public void testSetMapTransitView() {
        assertFalse(mapService.isTransitOverlay());

        String enableRes = mapTool.setMapTransitView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"transitView\": true"));
        assertTrue(mapService.isTransitOverlay());

        String disableRes = mapTool.setMapTransitView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"transitView\": false"));
        assertFalse(mapService.isTransitOverlay());
    }

    @Test
    public void testSetMapBikingView() {
        assertFalse(mapService.isBikingOverlay());

        String enableRes = mapTool.setMapBikingView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"bikingView\": true"));
        assertTrue(mapService.isBikingOverlay());

        String disableRes = mapTool.setMapBikingView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"bikingView\": false"));
        assertFalse(mapService.isBikingOverlay());
    }

    @Test
    public void testSetMapAirQualityView() {
        assertFalse(mapService.isAirQualityOverlay());

        String enableRes = mapTool.setMapAirQualityView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"airQualityView\": true"));
        assertTrue(mapService.isAirQualityOverlay());

        String disableRes = mapTool.setMapAirQualityView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"airQualityView\": false"));
        assertFalse(mapService.isAirQualityOverlay());
    }

    @Test
    public void testSetMapWildfiresView() {
        assertFalse(mapService.isWildfiresOverlay());

        String enableRes = mapTool.setMapWildfiresView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"wildfiresView\": true"));
        assertTrue(mapService.isWildfiresOverlay());

        String disableRes = mapTool.setMapWildfiresView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"wildfiresView\": false"));
        assertFalse(mapService.isWildfiresOverlay());
    }

    @Test
    public void testSetMapNauticalView() {
        assertFalse(mapService.isNauticalOverlay());

        String enableRes = mapTool.setMapNauticalView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"nauticalView\": true"));
        assertTrue(mapService.isNauticalOverlay());

        String disableRes = mapTool.setMapNauticalView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"nauticalView\": false"));
        assertFalse(mapService.isNauticalOverlay());
    }

    @Test
    public void testSetMapWindyView() {
        assertFalse(mapService.isWindyOverlay());

        String enableRes = mapTool.setMapWindyView(true);
        assertTrue(enableRes.contains("\"success\": true"));
        assertTrue(enableRes.contains("\"windyView\": true"));
        assertTrue(mapService.isWindyOverlay());

        String disableRes = mapTool.setMapWindyView(false);
        assertTrue(disableRes.contains("\"success\": true"));
        assertTrue(disableRes.contains("\"windyView\": false"));
        assertFalse(mapService.isWindyOverlay());
    }
}
