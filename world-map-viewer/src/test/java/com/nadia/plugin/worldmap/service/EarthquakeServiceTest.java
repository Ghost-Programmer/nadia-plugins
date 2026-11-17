package com.nadia.plugin.worldmap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.plugin.worldmap.service.MapService.MapObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EarthquakeServiceTest {

    private MapService mapService;
    private ObjectMapper objectMapper;
    private EarthquakeService earthquakeService;

    @BeforeEach
    public void setUp() {
        mapService = new MapService();
        objectMapper = new ObjectMapper();
        earthquakeService = new EarthquakeService(mapService, objectMapper, 10);
    }

    @AfterEach
    public void tearDown() {
        earthquakeService.shutdown();
    }

    @Test
    public void testContinuousTrackingStateAndClamping() {
        assertFalse(earthquakeService.isTrackingActive());
        assertEquals(1, earthquakeService.getLookbackDays());

        earthquakeService.startContinuousTracking(5, 3.2, 72);
        assertTrue(earthquakeService.isTrackingActive());
        assertEquals(15, earthquakeService.getTrackingInterval(), "Interval should clamp to minimum 15s");
        assertEquals(3.2, earthquakeService.getMinMagnitude(), 0.001);
        assertEquals(72, earthquakeService.getLookbackHours());
        assertEquals(3, earthquakeService.getLookbackDays());

        // Update lookback days while active (clamping 1..7)
        assertEquals(7, earthquakeService.setLookbackDays(14));
        assertEquals(7, earthquakeService.getLookbackDays());
        assertEquals(168, earthquakeService.getLookbackHours());

        assertEquals(1, earthquakeService.setLookbackDays(0));
        assertEquals(1, earthquakeService.getLookbackDays());
        assertEquals(24, earthquakeService.getLookbackHours());

        earthquakeService.stopContinuousTracking();
        assertFalse(earthquakeService.isTrackingActive());
    }

    @Test
    public void testParsingInvalidBoundsJson() {
        MapService mockedMap = mock(MapService.class);
        EarthquakeService svc = new EarthquakeService(mockedMap, objectMapper, 10);
        try {
            when(mockedMap.getCurrentBounds()).thenReturn("null");
            String result = svc.trackEarthquakesInCurrentView(2.5, 24);
            assertTrue(result.contains("error") && result.contains("bounds"));

            when(mockedMap.getCurrentBounds()).thenReturn("{bad-json}");
            result = svc.trackEarthquakesInCurrentView(2.5, 24);
            assertTrue(result.contains("error") && result.contains("Failed to parse"));
        } finally {
            svc.shutdown();
        }
    }

    @Test
    public void testParseAndPlotUsgsGeoJsonReplacesOldQuakesWhilePreservingPlanes() {
        // Plot an aircraft first — must remain untouched when earthquakes refresh
        mapService.plotObject("DAL101", 33.6407, -84.4277, 90.0, false, "plane");

        String sevenDayGeoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "us7000quake1",
                  "properties": {
                    "mag": 6.4,
                    "magType": "mww",
                    "place": "42 km WSW of Ferndale, California",
                    "time": 1731840000000,
                    "tsunami": 1,
                    "alert": "orange",
                    "felt": 412,
                    "sig": 890,
                    "status": "reviewed",
                    "url": "https://earthquake.usgs.gov/earthquakes/eventpage/us7000quake1"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-124.423, 40.525, 17.9]
                  }
                },
                {
                  "type": "Feature",
                  "id": "nc7500quake2",
                  "properties": {
                    "mag": 3.1,
                    "magType": "ml",
                    "place": "8 km NE of The Geysers, CA",
                    "time": 1731836000000,
                    "tsunami": 0,
                    "alert": null,
                    "felt": 5,
                    "sig": 149,
                    "status": "automatic",
                    "url": "https://earthquake.usgs.gov/earthquakes/eventpage/nc7500quake2"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.758, 38.831, 2.4]
                  }
                }
              ]
            }
            """;

        String jsonSummary = earthquakeService.parseAndPlotGeoJson(sevenDayGeoJson, 2.5, 168);
        assertTrue(jsonSummary.contains("\"success\":true"));
        assertTrue(jsonSummary.contains("\"earthquakes_found\":2"));
        assertTrue(jsonSummary.contains("\"lookback_days\":7"));
        assertTrue(jsonSummary.contains("\"max_magnitude\":6.4"));
        assertEquals(3, mapService.getObjects().size(), "1 plane + 2 earthquakes expected");

        // Now narrow window to 1 day (only 1 quake returned) -> older quake must be removed while plane stays
        String oneDayGeoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "us7000quake1",
                  "properties": {
                    "mag": 6.4,
                    "magType": "mww",
                    "place": "42 km WSW of Ferndale, California",
                    "time": 1731840000000,
                    "tsunami": 1,
                    "alert": "orange",
                    "felt": 412,
                    "sig": 890,
                    "status": "reviewed",
                    "url": "https://earthquake.usgs.gov/earthquakes/eventpage/us7000quake1"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-124.423, 40.525, 17.9]
                  }
                }
              ]
            }
            """;

        String oneDaySummary = earthquakeService.parseAndPlotGeoJson(oneDayGeoJson, 2.5, 24);
        assertTrue(oneDaySummary.contains("\"earthquakes_found\":1"));
        assertTrue(oneDaySummary.contains("\"lookback_days\":1"));

        List<MapObject> plotted = mapService.getObjects();
        assertEquals(2, plotted.size(), "DAL101 plane + 1 remaining earthquake expected");
        assertEquals("DAL101", plotted.get(0).name);

        MapObject majorQuake = plotted.get(1);
        assertEquals("M6.4 (us7000quake1)", majorQuake.name);
        assertEquals(1, majorQuake.path.size());
        assertEquals("earthquake", majorQuake.path.get(0).iconType);
        assertEquals(true, majorQuake.metadata.get("tsunami"));
        assertEquals("orange", majorQuake.metadata.get("alert"));
    }
}
