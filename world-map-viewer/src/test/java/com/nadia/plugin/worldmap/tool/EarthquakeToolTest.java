package com.nadia.plugin.worldmap.tool;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.plugin.worldmap.WorldMapRuntime;
import com.nadia.plugin.worldmap.service.EarthquakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EarthquakeToolTest {

    private EarthquakeService earthquakeService;
    private NadiaPluginContext context;
    private List<String> openedDockables;
    private EarthquakeTool earthquakeTool;

    @BeforeEach
    public void setUp() {
        earthquakeService = mock(EarthquakeService.class);
        when(earthquakeService.getLookbackHours()).thenReturn(24);
        when(earthquakeService.getLookbackDays()).thenReturn(1);
        context = mock(NadiaPluginContext.class);
        openedDockables = new ArrayList<>();
        doAnswer(inv -> {
            openedDockables.add(inv.getArgument(0));
            return null;
        }).when(context).openDockable(anyString());
        earthquakeTool = new EarthquakeTool(earthquakeService, context);
    }

    @Test
    public void testTrackEarthquakesWithDaysAndDefaults() {
        when(earthquakeService.trackEarthquakesInCurrentView(2.5, 24))
                .thenReturn("{\"success\":true,\"earthquakes_found\":4}");

        String res = earthquakeTool.trackEarthquakes(null, null, null);
        assertTrue(res.contains("\"earthquakes_found\":4"));
        assertEquals(List.of(WorldMapRuntime.DOCKABLE_ID), openedDockables);
        verify(earthquakeService).trackEarthquakesInCurrentView(2.5, 24);

        when(earthquakeService.trackEarthquakesInCurrentView(3.5, 120))
                .thenReturn("{\"success\":true,\"earthquakes_found\":11}");
        String fiveDayRes = earthquakeTool.trackEarthquakes(3.5, 5, null);
        assertTrue(fiveDayRes.contains("\"earthquakes_found\":11"));
        verify(earthquakeService).setLookbackDays(5);
        verify(earthquakeService).trackEarthquakesInCurrentView(3.5, 120);
    }

    @Test
    public void testSetEarthquakeLookbackDays() {
        when(earthquakeService.setLookbackDays(7)).thenReturn(7);
        String res = earthquakeTool.setEarthquakeLookbackDays(7);
        assertTrue(res.contains("\"success\": true"));
        assertTrue(res.contains("\"lookback_days\": 7"));
        assertTrue(res.contains("\"lookback_hours\": 168"));
        verify(earthquakeService).setLookbackDays(7);
    }

    @Test
    public void testTrackGlobalEarthquakes() {
        when(earthquakeService.fetchAndPlotGlobalEarthquakes(5.0, 72, 25))
                .thenReturn("{\"success\":true,\"earthquakes_found\":3}");

        String res = earthquakeTool.trackGlobalEarthquakes(5.0, 3, null, 25);
        assertTrue(res.contains("\"earthquakes_found\":3"));
        assertEquals(List.of(WorldMapRuntime.DOCKABLE_ID), openedDockables);
        verify(earthquakeService).fetchAndPlotGlobalEarthquakes(5.0, 72, 25);
    }

    @Test
    public void testContinuousEarthquakeTrackingLifecycle() {
        when(earthquakeService.getTrackingInterval()).thenReturn(60);
        when(earthquakeService.getMinMagnitude()).thenReturn(3.0);
        when(earthquakeService.getLookbackDays()).thenReturn(4);
        when(earthquakeService.getLookbackHours()).thenReturn(96);
        when(earthquakeService.isTrackingActive()).thenReturn(true);

        String startRes = earthquakeTool.startContinuousEarthquakeTracking(60, 3.0, 4, null);
        assertTrue(startRes.contains("\"success\": true"));
        verify(earthquakeService).startContinuousTracking(60, 3.0, 96);

        String statusRes = earthquakeTool.getEarthquakeTrackingStatus();
        assertTrue(statusRes.contains("\"active\": true"));
        assertTrue(statusRes.contains("\"lookback_days\": 4"));
        assertTrue(statusRes.contains("\"min_magnitude\": 3.0"));

        String stopRes = earthquakeTool.stopContinuousEarthquakeTracking();
        assertTrue(stopRes.contains("\"success\": true"));
        verify(earthquakeService).stopContinuousTracking();
    }
}
