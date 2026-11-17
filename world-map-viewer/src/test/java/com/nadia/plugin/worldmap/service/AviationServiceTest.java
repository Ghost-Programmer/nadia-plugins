package com.nadia.plugin.worldmap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AviationServiceTest {

    private MapService mapService;
    private ObjectMapper objectMapper;
    private AviationService aviationService;

    @BeforeEach
    public void setUp() {
        mapService = mock(MapService.class);
        objectMapper = new ObjectMapper();
        aviationService = new AviationService(mapService, objectMapper, 10);
    }

    @AfterEach
    public void tearDown() {
        aviationService.shutdown();
    }

    @Test
    public void testContinuousTrackingState() {
        assertFalse(aviationService.isTrackingActive());

        aviationService.startContinuousTracking(15);
        assertTrue(aviationService.isTrackingActive());
        assertEquals(15, aviationService.getTrackingInterval());

        aviationService.stopContinuousTracking();
        assertFalse(aviationService.isTrackingActive());
    }

    @Test
    public void testParsingInvalidBoundsJson() {
        when(mapService.getCurrentBounds()).thenReturn("null");
        String result = aviationService.trackPlanesInCurrentView();
        assertTrue(result.contains("error") && result.contains("bounds"));

        when(mapService.getCurrentBounds()).thenReturn("{invalid-json}");
        result = aviationService.trackPlanesInCurrentView();
        assertTrue(result.contains("error") && result.contains("Failed to parse"));
    }
}
