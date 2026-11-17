package com.nadia.plugin.worldmap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SatelliteServiceTest {

    private MapService mapService;
    private ObjectMapper objectMapper;
    private SatelliteService satelliteService;

    @BeforeEach
    public void setUp() {
        mapService = mock(MapService.class);
        objectMapper = new ObjectMapper();
        satelliteService = new SatelliteService(mapService, objectMapper, 10);
    }

    @AfterEach
    public void tearDown() {
        satelliteService.shutdown();
    }

    @Test
    public void testContinuousTrackingState() {
        assertFalse(satelliteService.isTrackingActive());

        satelliteService.startContinuousTracking(30, 2);
        assertTrue(satelliteService.isTrackingActive());
        assertEquals(30, satelliteService.getTrackingInterval());
        assertEquals(2, satelliteService.getTrackingCategoryId());

        satelliteService.stopContinuousTracking();
        assertFalse(satelliteService.isTrackingActive());
    }

    @Test
    public void testParsingInvalidBoundsJson() {
        when(mapService.getCurrentBounds()).thenReturn("null");
        String result = satelliteService.trackSatellitesInCurrentView(0);
        assertTrue(result.contains("error") && result.contains("bounds"));

        when(mapService.getCurrentBounds()).thenReturn("{invalid-json}");
        result = satelliteService.trackSatellitesInCurrentView(0);
        assertTrue(result.contains("error") && result.contains("Failed to parse"));
    }
}
