package com.nadia.plugin.worldmap.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nadia.plugin.worldmap.model.FaaAircraftRecord;
import com.nadia.plugin.worldmap.service.AviationService;
import com.nadia.plugin.worldmap.service.EarthquakeService;
import com.nadia.plugin.worldmap.service.FaaRegistryService;
import com.nadia.plugin.worldmap.service.MapService;
import com.nadia.plugin.worldmap.service.MapService.Location;
import com.nadia.plugin.worldmap.service.MapService.MapFlag;
import com.nadia.plugin.worldmap.service.MapService.MapObject;
import com.nadia.plugin.worldmap.service.SatelliteService;
import com.nadia.plugin.worldmap.util.IcaoNNumberConverter;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive Leaflet.js World Map Viewer component rendered inside a JavaFX
 * {@link WebView}.
 *
 * <p>
 * Plots real-time aviation flights (OpenSky + FAA Civil Aviation Registry),
 * orbital satellites (N2YO),
 * custom object paths, and stationary flags, with satellite imagery,
 * topographic terrain, solar terminator,
 * live traffic, public transit, cycling trails, air quality, active wildfires,
 * Windy atmospheric streamlines,
 * and OpenSeaMap nautical seamark overlays.
 * </p>
 *
 * <p>
 * <b>Lifecycle &amp; Threading:</b>
 * Instantiated on the JavaFX Application Thread when contributed via
 * {@code WorldMapUiExtension} and
 * registered with {@link MapService} for thread-safe viewport updates.
 * </p>
 */
public class MapWindowView extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(MapWindowView.class);

    private final MapService mapService;
    private final AviationService aviationService;
    private final SatelliteService satelliteService;
    private final EarthquakeService earthquakeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebView webView;
    private final WebEngine webEngine;
    private AviationBridge aviationBridge;
    private boolean isLoaded = false;
    private boolean satelliteViewActive = false;
    private boolean terrainViewActive = false;
    private boolean dayNightActive = true;
    private boolean trafficActive = true;
    private boolean transitActive = false;
    private boolean bikingActive = false;
    private boolean airQualityActive = false;
    private boolean wildfiresActive = false;
    private boolean windyActive = false;
    private boolean nauticalActive = false;

    private MenuButton layersMenuBtn;
    private CheckBox planesCheckBox;
    private CheckBox satellitesCheckBox;
    private CheckBox earthquakesCheckBox;
    private ComboBox<Integer> earthquakeDaysCombo;
    private Label earthquakeDaysMenuLabel;
    private boolean updatingQuakeDaysControl = false;
    private CheckBox satelliteCheckBox;
    private CheckBox terrainCheckBox;
    private CheckBox dayNightCheckBox;
    private CheckBox trafficCheckBox;
    private CheckBox transitCheckBox;
    private CheckBox bikingCheckBox;
    private CheckBox airQualityCheckBox;
    private CheckBox wildfiresCheckBox;
    private CheckBox windyCheckBox;
    private CheckBox nauticalCheckBox;
    private Button clearMapBtn;
    private Label statusBadge;

    /**
     * Constructs a {@code MapWindowView} with basic map service.
     *
     * @param mapService the core map plotting service
     */
    public MapWindowView(MapService mapService) {
        this(mapService, null, null, null);
    }

    /**
     * Constructs a {@code MapWindowView} with live flight and satellite tracking
     * integration.
     *
     * @param mapService       the core map plotting service
     * @param aviationService  the aviation flight tracking service
     * @param satelliteService the satellite tracking service
     */
    public MapWindowView(MapService mapService,
            AviationService aviationService,
            SatelliteService satelliteService) {
        this(mapService, aviationService, satelliteService, null);
    }

    /**
     * Constructs a {@code MapWindowView} with live tracking services and an
     * optional extracted {@code map.html} URI.
     *
     * @param mapService          the core map plotting service
     * @param aviationService     the aviation flight tracking service
     * @param satelliteService    the satellite tracking service
     * @param extractedMapHtmlUri optional {@code file://} URI of {@code map.html}
     *                            extracted to plugin data directory
     */
    public MapWindowView(MapService mapService,
            AviationService aviationService,
            SatelliteService satelliteService,
            String extractedMapHtmlUri) {
        this(mapService, aviationService, satelliteService, null, extractedMapHtmlUri);
    }

    /**
     * Constructs a {@code MapWindowView} with aviation, satellite, and USGS earthquake tracking services.
     *
     * @param mapService          the core map plotting service
     * @param aviationService     the aviation flight tracking service
     * @param satelliteService    the satellite tracking service
     * @param earthquakeService   the USGS earthquake tracking service
     * @param extractedMapHtmlUri optional {@code file://} URI of {@code map.html} extracted to plugin data directory
     */
    public MapWindowView(MapService mapService,
            AviationService aviationService,
            SatelliteService satelliteService,
            EarthquakeService earthquakeService,
            String extractedMapHtmlUri) {
        this.mapService = mapService;
        this.aviationService = aviationService;
        this.satelliteService = satelliteService;
        this.earthquakeService = earthquakeService;

        this.dayNightActive = mapService.isDayNightOverlay();
        this.satelliteViewActive = mapService.isSatelliteView();
        this.terrainViewActive = mapService.isTerrainView();
        this.trafficActive = mapService.isTrafficOverlay();
        this.transitActive = mapService.isTransitOverlay();
        this.bikingActive = mapService.isBikingOverlay();
        this.airQualityActive = mapService.isAirQualityOverlay();
        this.wildfiresActive = mapService.isWildfiresOverlay();
        this.windyActive = mapService.isWindyOverlay();
        this.nauticalActive = mapService.isNauticalOverlay();

        this.mapService.registerMapView(this);
        getStyleClass().add("settings-tab-content");
        setStyle("-fx-background-color: #121212;");

        HBox toolbar = createTrackingToolbar();
        setTop(toolbar);

        webView = new WebView();
        webEngine = webView.getEngine();
        webView.setContextMenuEnabled(false);

        webEngine.setOnAlert(event -> logger.info("[WebView Map] {}", event.getData()));

        if (extractedMapHtmlUri != null && !extractedMapHtmlUri.isBlank()) {
            webEngine.load(extractedMapHtmlUri);
        } else {
            URL mapUrl = getClass().getResource("/map.html");
            if (mapUrl != null) {
                webEngine.load(mapUrl.toExternalForm());
            } else {
                logger.error("Failed to load map.html resource!");
            }
        }

        setCenter(webView);

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                logger.info("[MapWindowView] WebView successfully loaded map.html");
                isLoaded = true;
                try {
                    this.aviationBridge = new AviationBridge(this,
                            aviationService != null ? aviationService.getFaaRegistryService() : null);
                    Object window = webEngine.executeScript("window");
                    if (window != null) {
                        window.getClass().getMethod("setMember", String.class, Object.class)
                                .invoke(window, "aviationBridge", this.aviationBridge);
                    }
                } catch (Exception e) {
                    logger.warn("[MapWindowView] Failed to register aviationBridge: {}", e.getMessage());
                }
                applySatelliteView();
                applyTerrainView();
                applyDayNightView();
                applyTrafficView();
                applyTransitView();
                applyBikingView();
                applyAirQualityView();
                applyWildfiresView();
                applyWindyView();
                applyNauticalView();
                replayHistory();
            } else if (newState == Worker.State.FAILED) {
                logger.error("[MapWindowView] WebView failed to load map.html");
            }
        });

        mapService.setOnChangeCallback(this::replayHistory);
        if (this.earthquakeService != null) {
            this.earthquakeService.setOnConfigChangeCallback(this::updateTrackingButtonsState);
        }
    }

    private HBox createTrackingToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.setStyle("-fx-background-color: #181b24; -fx-border-color: #283144; -fx-border-width: 0 0 1 0;");

        Label titleLabel = new Label("🗺 World Map:");
        titleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 12px;");

        layersMenuBtn = new MenuButton("🗺 Layers & Tracking");
        layersMenuBtn.setTooltip(new Tooltip(
                "Configure live trackers, map layers, overlays, environmental feeds, and nautical navigation"));
        layersMenuBtn.setStyle("-fx-background-color: #27272a; -fx-text-fill: #cbd5e1; -fx-cursor: hand; " +
                "-fx-background-radius: 4; -fx-padding: 5 12; -fx-font-weight: bold; -fx-font-size: 12px;");

        planesCheckBox = createLayerCheckBox("✈ Planes (OpenSky)",
                "Toggle continuous real-time plane & flight tracking (OpenSky Network, 15s interval)",
                aviationService != null && aviationService.isTrackingActive(),
                e -> handleTogglePlanes());

        satellitesCheckBox = createLayerCheckBox("🛰 Satellites (N2YO)",
                "Toggle continuous real-time satellite tracking (N2YO API, 30s interval)",
                satelliteService != null && satelliteService.isTrackingActive(),
                e -> handleToggleSatellites());

        earthquakesCheckBox = createLayerCheckBox("🌋 Earthquakes (USGS)",
                "Toggle continuous real-time USGS earthquake tracking (M2.5+, past 1–7 days, 60s interval)",
                earthquakeService != null && earthquakeService.isTrackingActive(),
                e -> handleToggleEarthquakes());

        satelliteCheckBox = createLayerCheckBox("🌍 Satellite Imagery",
                "Switch to high-resolution satellite imagery with hybrid road and border boundaries",
                satelliteViewActive, e -> handleToggleSatelliteView());

        terrainCheckBox = createLayerCheckBox("⛰ Terrain (Topography)",
                "Switch to Google Terrain topographic relief and elevation contour map",
                terrainViewActive, e -> handleToggleTerrain());

        dayNightCheckBox = createLayerCheckBox("🌓 Day/Night Terminator",
                "Toggle real-time solar terminator illumination shadow and zenith/nadir markers",
                dayNightActive, e -> handleToggleDayNight());

        trafficCheckBox = createLayerCheckBox("🚗 Live Traffic",
                "Toggle real-time live traffic congestion flow overlay (zoomed into roads)",
                trafficActive, e -> handleToggleTraffic());

        transitCheckBox = createLayerCheckBox("🚊 Public Transit",
                "Toggle Google public transit network, subway/train lines, and transit stops",
                transitActive, e -> handleToggleTransit());

        bikingCheckBox = createLayerCheckBox("🚲 Biking & Trails",
                "Toggle Google cycling network, dedicated bike lanes, and trail paths",
                bikingActive, e -> handleToggleBiking());

        airQualityCheckBox = createLayerCheckBox("💨 Air Quality (AQI)",
                "Toggle real-time global Air Quality Index (AQI) heatmap overlay and monitoring stations",
                airQualityActive, e -> handleToggleAirQuality());

        wildfiresCheckBox = createLayerCheckBox("🔥 Wildfires & Thermal",
                "Toggle NASA FIRMS active wildfire hotspots and thermal anomaly detections",
                wildfiresActive, e -> handleToggleWildfires());

        windyCheckBox = createLayerCheckBox("💨 Wind Patterns (Windy.com)",
                "Toggle real-time global animated wind streamline particles and atmospheric flow overlay (api.windy.com)",
                windyActive, e -> handleToggleWindy());

        nauticalCheckBox = createLayerCheckBox("⚓ Nautical (OpenSeaMap)",
                "Toggle OpenSeaMap nautical navigation seamarks, buoys, beacons, and marine aids",
                nauticalActive, e -> handleToggleNautical());

        CustomMenuItem planesItem = new CustomMenuItem(createLayerRow(planesCheckBox), false);
        planesItem.setHideOnClick(false);

        CustomMenuItem satellitesItem = new CustomMenuItem(createLayerRow(satellitesCheckBox), false);
        satellitesItem.setHideOnClick(false);

        CustomMenuItem earthquakesItem = new CustomMenuItem(createEarthquakeLayerRow(earthquakesCheckBox), false);
        earthquakesItem.setHideOnClick(false);

        CustomMenuItem satelliteItem = new CustomMenuItem(createLayerRow(satelliteCheckBox), false);
        satelliteItem.setHideOnClick(false);

        CustomMenuItem terrainItem = new CustomMenuItem(createLayerRow(terrainCheckBox), false);
        terrainItem.setHideOnClick(false);

        CustomMenuItem dayNightItem = new CustomMenuItem(createLayerRow(dayNightCheckBox), false);
        dayNightItem.setHideOnClick(false);

        CustomMenuItem trafficItem = new CustomMenuItem(createLayerRow(trafficCheckBox), false);
        trafficItem.setHideOnClick(false);

        CustomMenuItem transitItem = new CustomMenuItem(createLayerRow(transitCheckBox), false);
        transitItem.setHideOnClick(false);

        CustomMenuItem bikingItem = new CustomMenuItem(createLayerRow(bikingCheckBox), false);
        bikingItem.setHideOnClick(false);

        CustomMenuItem airQualityItem = new CustomMenuItem(createLayerRow(airQualityCheckBox), false);
        airQualityItem.setHideOnClick(false);

        CustomMenuItem wildfiresItem = new CustomMenuItem(createLayerRow(wildfiresCheckBox), false);
        wildfiresItem.setHideOnClick(false);

        CustomMenuItem windyItem = new CustomMenuItem(createLayerRow(windyCheckBox), false);
        windyItem.setHideOnClick(false);

        CustomMenuItem nauticalItem = new CustomMenuItem(createLayerRow(nauticalCheckBox), false);
        nauticalItem.setHideOnClick(false);

        layersMenuBtn.getItems().addAll(
                planesItem,
                satellitesItem,
                earthquakesItem,
                new SeparatorMenuItem(),
                satelliteItem,
                terrainItem,
                new SeparatorMenuItem(),
                dayNightItem,
                trafficItem,
                transitItem,
                bikingItem,
                airQualityItem,
                wildfiresItem,
                windyItem,
                nauticalItem);

        int initialQuakeDays = earthquakeService != null ? earthquakeService.getLookbackDays() : 1;
        earthquakeDaysCombo = new ComboBox<>();
        earthquakeDaysCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7);
        earthquakeDaysCombo.setValue(initialQuakeDays);
        earthquakeDaysCombo.setTooltip(new Tooltip("Select USGS earthquake history window (past 1 to 7 days)"));
        earthquakeDaysCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer days) {
                int d = days != null ? days : 1;
                return "🌋 Past " + d + (d == 1 ? " Day" : " Days");
            }

            @Override
            public Integer fromString(String s) {
                return 1;
            }
        });
        earthquakeDaysCombo.setStyle("-fx-background-color: #27272a; -fx-text-fill: #cbd5e1; -fx-cursor: hand; " +
                "-fx-background-radius: 4; -fx-font-size: 12px;");
        earthquakeDaysCombo.setOnAction(e -> {
            if (updatingQuakeDaysControl) {
                return;
            }
            Integer selected = earthquakeDaysCombo.getValue();
            if (selected != null) {
                handleChangeEarthquakeDays(selected);
            }
        });

        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setStyle("-fx-background-color: #283144; -fx-opacity: 0.6;");

        clearMapBtn = new Button("🗑 Clear");
        clearMapBtn.setTooltip(new Tooltip("Clear all plotted objects and tracks from the map"));
        clearMapBtn.setStyle("-fx-background-color: #27272a; -fx-text-fill: #cbd5e1; -fx-cursor: hand; " +
                "-fx-background-radius: 4; -fx-padding: 5 10;");
        clearMapBtn.setOnAction(e -> mapService.clearMap());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBadge = new Label("● Standby");
        statusBadge.setStyle("-fx-background-color: #27272a; -fx-text-fill: #94a3b8; -fx-font-size: 11px; " +
                "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold;");

        bar.getChildren().addAll(
                titleLabel,
                layersMenuBtn,
                earthquakeDaysCombo,
                sep,
                clearMapBtn,
                spacer,
                statusBadge);

        updateTrackingButtonsState();

        return bar;
    }

    private void handleTogglePlanes() {
        if (aviationService == null) {
            logger.warn("[MapWindowView] AviationService is not available.");
            if (planesCheckBox != null)
                planesCheckBox.setSelected(false);
            return;
        }

        boolean shouldTrack = planesCheckBox != null ? planesCheckBox.isSelected()
                : !aviationService.isTrackingActive();
        if (shouldTrack) {
            aviationService.startContinuousTracking(15);
            logger.info("[MapWindowView] Started continuous plane tracking (15s interval).");
        } else {
            aviationService.stopContinuousTracking();
            logger.info("[MapWindowView] Stopped continuous plane tracking.");
        }
        updateTrackingButtonsState();
    }

    private void handleToggleSatellites() {
        if (satelliteService == null) {
            logger.warn("[MapWindowView] SatelliteService is not available.");
            if (satellitesCheckBox != null)
                satellitesCheckBox.setSelected(false);
            return;
        }

        boolean shouldTrack = satellitesCheckBox != null ? satellitesCheckBox.isSelected()
                : !satelliteService.isTrackingActive();
        if (shouldTrack) {
            satelliteService.startContinuousTracking(30, 0);
            logger.info("[MapWindowView] Started continuous satellite tracking (30s interval).");
        } else {
            satelliteService.stopContinuousTracking();
            logger.info("[MapWindowView] Stopped continuous satellite tracking.");
        }
        updateTrackingButtonsState();
    }

    private void handleToggleEarthquakes() {
        if (earthquakeService == null) {
            logger.warn("[MapWindowView] EarthquakeService is not available.");
            if (earthquakesCheckBox != null)
                earthquakesCheckBox.setSelected(false);
            return;
        }

        boolean shouldTrack = earthquakesCheckBox != null ? earthquakesCheckBox.isSelected()
                : !earthquakeService.isTrackingActive();
        if (shouldTrack) {
            int days = earthquakeDaysCombo != null && earthquakeDaysCombo.getValue() != null
                    ? earthquakeDaysCombo.getValue()
                    : earthquakeService.getLookbackDays();
            int lookbackHours = EarthquakeService.clampLookbackDays(days) * 24;
            earthquakeService.startContinuousTracking(60, 2.5, lookbackHours);
            logger.info("[MapWindowView] Started continuous USGS earthquake tracking (60s interval, M2.5+, {}d).", days);
        } else {
            earthquakeService.stopContinuousTracking();
            logger.info("[MapWindowView] Stopped continuous USGS earthquake tracking.");
        }
        updateTrackingButtonsState();
    }

    private void handleChangeEarthquakeDays(int requestedDays) {
        int clamped = EarthquakeService.clampLookbackDays(requestedDays);
        if (earthquakeService != null) {
            earthquakeService.setLookbackDays(clamped);
            logger.info("[MapWindowView] Updated earthquake lookback window to {} day(s).", clamped);
        }
        updateTrackingButtonsState();
    }

    private CheckBox createLayerCheckBox(String text, String tooltip, boolean initialSelected,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(initialSelected);
        cb.setTooltip(new Tooltip(tooltip));
        cb.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 12px; -fx-cursor: hand;");
        cb.setOnAction(handler);
        return cb;
    }

    private HBox createLayerRow(CheckBox cb) {
        HBox row = new HBox(8, cb);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 10, 5, 10));
        row.setPrefWidth(230);
        row.setStyle("-fx-cursor: hand;");
        row.setOnMouseClicked(e -> {
            if (e.getTarget() != cb) {
                cb.setSelected(!cb.isSelected());
                cb.fireEvent(new javafx.event.ActionEvent());
            }
        });
        return row;
    }

    private HBox createEarthquakeLayerRow(CheckBox cb) {
        int initDays = earthquakeService != null ? earthquakeService.getLookbackDays() : 1;

        Button minusBtn = new Button("−");
        minusBtn.setTooltip(new Tooltip("Show fewer past days of earthquakes (min 1 day)"));
        minusBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: #e2e8f0; -fx-font-size: 10px; " +
                "-fx-font-weight: bold; -fx-padding: 1 6; -fx-background-radius: 3; -fx-cursor: hand;");
        minusBtn.setOnAction(e -> {
            e.consume();
            int cur = earthquakeService != null ? earthquakeService.getLookbackDays()
                    : (earthquakeDaysCombo != null && earthquakeDaysCombo.getValue() != null ? earthquakeDaysCombo.getValue() : 1);
            if (cur > EarthquakeService.MIN_LOOKBACK_DAYS) {
                handleChangeEarthquakeDays(cur - 1);
            }
        });

        earthquakeDaysMenuLabel = new Label(initDays + "d");
        earthquakeDaysMenuLabel.setMinWidth(22);
        earthquakeDaysMenuLabel.setAlignment(Pos.CENTER);
        earthquakeDaysMenuLabel.setStyle("-fx-text-fill: #fdba74; -fx-font-size: 11px; -fx-font-weight: bold;");

        Button plusBtn = new Button("+");
        plusBtn.setTooltip(new Tooltip("Show more past days of earthquakes (up to 7 days)"));
        plusBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: #e2e8f0; -fx-font-size: 10px; " +
                "-fx-font-weight: bold; -fx-padding: 1 6; -fx-background-radius: 3; -fx-cursor: hand;");
        plusBtn.setOnAction(e -> {
            e.consume();
            int cur = earthquakeService != null ? earthquakeService.getLookbackDays()
                    : (earthquakeDaysCombo != null && earthquakeDaysCombo.getValue() != null ? earthquakeDaysCombo.getValue() : 1);
            if (cur < EarthquakeService.MAX_LOOKBACK_DAYS) {
                handleChangeEarthquakeDays(cur + 1);
            }
        });

        HBox stepper = new HBox(3, minusBtn, earthquakeDaysMenuLabel, plusBtn);
        stepper.setAlignment(Pos.CENTER_RIGHT);

        Region rowSpacer = new Region();
        HBox.setHgrow(rowSpacer, Priority.ALWAYS);

        HBox row = new HBox(6, cb, rowSpacer, stepper);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 10, 5, 10));
        row.setPrefWidth(260);
        row.setStyle("-fx-cursor: hand;");
        row.setOnMouseClicked(e -> {
            Object target = e.getTarget();
            if (target != cb && target != minusBtn && target != plusBtn && target != earthquakeDaysMenuLabel) {
                cb.setSelected(!cb.isSelected());
                cb.fireEvent(new javafx.event.ActionEvent());
            }
        });
        return row;
    }

    private void handleToggleSatelliteView() {
        satelliteViewActive = satelliteCheckBox != null ? satelliteCheckBox.isSelected() : !satelliteViewActive;
        if (satelliteViewActive && terrainViewActive) {
            terrainViewActive = false;
            mapService.setTerrainView(false);
            if (terrainCheckBox != null)
                terrainCheckBox.setSelected(false);
        }
        mapService.setSatelliteView(satelliteViewActive);
        applySatelliteView();
        updateTrackingButtonsState();
    }

    private void handleToggleTerrain() {
        terrainViewActive = terrainCheckBox != null ? terrainCheckBox.isSelected() : !terrainViewActive;
        if (terrainViewActive && satelliteViewActive) {
            satelliteViewActive = false;
            mapService.setSatelliteView(false);
            if (satelliteCheckBox != null)
                satelliteCheckBox.setSelected(false);
        }
        mapService.setTerrainView(terrainViewActive);
        applyTerrainView();
        updateTrackingButtonsState();
    }

    private void handleToggleDayNight() {
        dayNightActive = dayNightCheckBox != null ? dayNightCheckBox.isSelected() : !dayNightActive;
        mapService.setDayNightOverlay(dayNightActive);
        applyDayNightView();
        updateTrackingButtonsState();
    }

    private void handleToggleTraffic() {
        trafficActive = trafficCheckBox != null ? trafficCheckBox.isSelected() : !trafficActive;
        mapService.setTrafficOverlay(trafficActive);
        applyTrafficView();
        updateTrackingButtonsState();
    }

    private void handleToggleTransit() {
        transitActive = transitCheckBox != null ? transitCheckBox.isSelected() : !transitActive;
        mapService.setTransitOverlay(transitActive);
        applyTransitView();
        updateTrackingButtonsState();
    }

    private void handleToggleBiking() {
        bikingActive = bikingCheckBox != null ? bikingCheckBox.isSelected() : !bikingActive;
        mapService.setBikingOverlay(bikingActive);
        applyBikingView();
        updateTrackingButtonsState();
    }

    private void handleToggleAirQuality() {
        airQualityActive = airQualityCheckBox != null ? airQualityCheckBox.isSelected() : !airQualityActive;
        mapService.setAirQualityOverlay(airQualityActive);
        applyAirQualityView();
        updateTrackingButtonsState();
    }

    private void handleToggleWildfires() {
        wildfiresActive = wildfiresCheckBox != null ? wildfiresCheckBox.isSelected() : !wildfiresActive;
        mapService.setWildfiresOverlay(wildfiresActive);
        applyWildfiresView();
        updateTrackingButtonsState();
    }

    private void handleToggleWindy() {
        windyActive = windyCheckBox != null ? windyCheckBox.isSelected() : !windyActive;
        mapService.setWindyOverlay(windyActive);
        applyWindyView();
        updateTrackingButtonsState();
    }

    private void handleToggleNautical() {
        nauticalActive = nauticalCheckBox != null ? nauticalCheckBox.isSelected() : !nauticalActive;
        mapService.setNauticalOverlay(nauticalActive);
        applyNauticalView();
        updateTrackingButtonsState();
    }

    /**
     * Toggles between standard street map tiles and high-resolution satellite
     * imagery tiles.
     *
     * @param enabled {@code true} to display satellite tiles, {@code false} for
     *                standard street map
     */
    public void setSatelliteView(boolean enabled) {
        this.satelliteViewActive = enabled;
        if (enabled && this.terrainViewActive) {
            this.terrainViewActive = false;
            if (terrainCheckBox != null)
                terrainCheckBox.setSelected(false);
        }
        if (satelliteCheckBox != null && satelliteCheckBox.isSelected() != enabled) {
            satelliteCheckBox.setSelected(enabled);
        }
        applySatelliteView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if satellite tile layer is currently active.
     *
     * @return {@code true} if satellite imagery is active, {@code false} otherwise
     */
    public boolean isSatelliteView() {
        return satelliteViewActive;
    }

    /**
     * Returns the satellite view layer checkbox.
     *
     * @return the satellite {@link CheckBox}
     */
    public CheckBox getSatelliteCheckBox() {
        return satelliteCheckBox;
    }

    /**
     * Toggles Google Terrain topographic relief and elevation contour tile imagery.
     *
     * @param enabled {@code true} to display terrain tiles, {@code false} for
     *                standard roadmap
     */
    public void setTerrainView(boolean enabled) {
        this.terrainViewActive = enabled;
        if (enabled && this.satelliteViewActive) {
            this.satelliteViewActive = false;
            if (satelliteCheckBox != null)
                satelliteCheckBox.setSelected(false);
        }
        if (terrainCheckBox != null && terrainCheckBox.isSelected() != enabled) {
            terrainCheckBox.setSelected(enabled);
        }
        applyTerrainView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if terrain tile layer is currently active.
     *
     * @return {@code true} if terrain mode is active, {@code false} otherwise
     */
    public boolean isTerrainView() {
        return terrainViewActive;
    }

    /**
     * Returns the terrain view layer checkbox.
     *
     * @return the terrain {@link CheckBox}
     */
    public CheckBox getTerrainCheckBox() {
        return terrainCheckBox;
    }

    /**
     * Enables or disables the real-time solar terminator day/night illumination
     * shadow overlay.
     *
     * @param enabled {@code true} to display day/night shadow, {@code false} to
     *                hide
     */
    public void setDayNightView(boolean enabled) {
        this.dayNightActive = enabled;
        if (dayNightCheckBox != null && dayNightCheckBox.isSelected() != enabled) {
            dayNightCheckBox.setSelected(enabled);
        }
        applyDayNightView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if day/night shadow tracking is active.
     *
     * @return {@code true} if day/night illumination is active, {@code false}
     *         otherwise
     */
    public boolean isDayNightView() {
        return dayNightActive;
    }

    /**
     * Returns the day/night tracking overlay checkbox.
     *
     * @return the day/night overlay {@link CheckBox}
     */
    public CheckBox getDayNightCheckBox() {
        return dayNightCheckBox;
    }

    /**
     * Enables or disables the real-time live traffic congestion overlay on the map.
     *
     * @param enabled {@code true} to display traffic overlay when zoomed in,
     *                {@code false} to hide
     */
    public void setTrafficView(boolean enabled) {
        this.trafficActive = enabled;
        if (trafficCheckBox != null && trafficCheckBox.isSelected() != enabled) {
            trafficCheckBox.setSelected(enabled);
        }
        applyTrafficView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if live traffic overlay is active.
     *
     * @return {@code true} if traffic conditions overlay is active, {@code false}
     *         otherwise
     */
    public boolean isTrafficView() {
        return trafficActive;
    }

    /**
     * Returns the traffic overlay checkbox.
     *
     * @return the traffic overlay {@link CheckBox}
     */
    public CheckBox getTrafficCheckBox() {
        return trafficCheckBox;
    }

    /**
     * Enables or disables the Google public transit network overlay on the map.
     *
     * @param enabled {@code true} to display transit lines and stations,
     *                {@code false} to hide
     */
    public void setTransitView(boolean enabled) {
        this.transitActive = enabled;
        if (transitCheckBox != null && transitCheckBox.isSelected() != enabled) {
            transitCheckBox.setSelected(enabled);
        }
        applyTransitView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if public transit overlay is active.
     *
     * @return {@code true} if transit overlay is active, {@code false} otherwise
     */
    public boolean isTransitView() {
        return transitActive;
    }

    /**
     * Returns the public transit overlay checkbox.
     *
     * @return the public transit {@link CheckBox}
     */
    public CheckBox getTransitCheckBox() {
        return transitCheckBox;
    }

    /**
     * Enables or disables the Google biking and cycling trail network overlay on
     * the map.
     *
     * @param enabled {@code true} to display cycling routes and trails,
     *                {@code false} to hide
     */
    public void setBikingView(boolean enabled) {
        this.bikingActive = enabled;
        if (bikingCheckBox != null && bikingCheckBox.isSelected() != enabled) {
            bikingCheckBox.setSelected(enabled);
        }
        applyBikingView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if biking trails overlay is active.
     *
     * @return {@code true} if biking overlay is active, {@code false} otherwise
     */
    public boolean isBikingView() {
        return bikingActive;
    }

    /**
     * Returns the biking trails overlay checkbox.
     *
     * @return the biking {@link CheckBox}
     */
    public CheckBox getBikingCheckBox() {
        return bikingCheckBox;
    }

    /**
     * Enables or disables the global Air Quality Index (AQI) heatmap overlay and
     * monitoring stations.
     *
     * @param enabled {@code true} to display air quality heatmap, {@code false} to
     *                hide
     */
    public void setAirQualityView(boolean enabled) {
        this.airQualityActive = enabled;
        if (airQualityCheckBox != null && airQualityCheckBox.isSelected() != enabled) {
            airQualityCheckBox.setSelected(enabled);
        }
        applyAirQualityView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if global Air Quality Index overlay is active.
     *
     * @return {@code true} if air quality overlay is active, {@code false}
     *         otherwise
     */
    public boolean isAirQualityView() {
        return airQualityActive;
    }

    /**
     * Returns the air quality overlay checkbox.
     *
     * @return the air quality {@link CheckBox}
     */
    public CheckBox getAirQualityCheckBox() {
        return airQualityCheckBox;
    }

    /**
     * Enables or disables the NASA FIRMS active wildfire hotspots and thermal
     * anomaly overlay.
     *
     * @param enabled {@code true} to display active wildfire hotspots,
     *                {@code false} to hide
     */
    public void setWildfiresView(boolean enabled) {
        this.wildfiresActive = enabled;
        if (wildfiresCheckBox != null && wildfiresCheckBox.isSelected() != enabled) {
            wildfiresCheckBox.setSelected(enabled);
        }
        applyWildfiresView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if NASA FIRMS active wildfire overlay is active.
     *
     * @return {@code true} if wildfire overlay is active, {@code false} otherwise
     */
    public boolean isWildfiresView() {
        return wildfiresActive;
    }

    /**
     * Returns the wildfires overlay checkbox.
     *
     * @return the wildfires {@link CheckBox}
     */
    public CheckBox getWildfiresCheckBox() {
        return wildfiresCheckBox;
    }

    /**
     * Enables or disables the real-time animated global wind streamline particles
     * overlay (powered by api.windy.com).
     *
     * @param enabled {@code true} to display dynamic wind streamlines,
     *                {@code false} to hide
     */
    public void setWindyView(boolean enabled) {
        this.windyActive = enabled;
        if (windyCheckBox != null && windyCheckBox.isSelected() != enabled) {
            windyCheckBox.setSelected(enabled);
        }
        applyWindyView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if the Windy.com wind pattern overlay is active.
     *
     * @return {@code true} if wind pattern overlay is active, {@code false}
     *         otherwise
     */
    public boolean isWindyView() {
        return windyActive;
    }

    /**
     * Returns the Windy.com wind pattern layer checkbox.
     *
     * @return the wind pattern {@link CheckBox}
     */
    public CheckBox getWindyCheckBox() {
        return windyCheckBox;
    }

    /**
     * Enables or disables the OpenSeaMap nautical seamarks and marine navigation
     * overlay.
     *
     * @param enabled {@code true} to display nautical seamarks, {@code false} to
     *                hide
     */
    public void setNauticalView(boolean enabled) {
        this.nauticalActive = enabled;
        if (nauticalCheckBox != null && nauticalCheckBox.isSelected() != enabled) {
            nauticalCheckBox.setSelected(enabled);
        }
        applyNauticalView();
        updateTrackingButtonsState();
    }

    /**
     * Checks if the OpenSeaMap nautical seamarks overlay is active.
     *
     * @return {@code true} if nautical seamarks overlay is active, {@code false}
     *         otherwise
     */
    public boolean isNauticalView() {
        return nauticalActive;
    }

    /**
     * Returns the nautical layer checkbox.
     *
     * @return the nautical layer {@link CheckBox}
     */
    public CheckBox getNauticalCheckBox() {
        return nauticalCheckBox;
    }

    /**
     * Returns the planes tracking checkbox.
     *
     * @return the planes tracking {@link CheckBox}
     */
    public CheckBox getPlanesCheckBox() {
        return planesCheckBox;
    }

    /**
     * Returns the satellites tracking checkbox.
     *
     * @return the satellites tracking {@link CheckBox}
     */
    public CheckBox getSatellitesCheckBox() {
        return satellitesCheckBox;
    }

    /**
     * Returns the USGS earthquakes tracking checkbox.
     *
     * @return the earthquakes tracking {@link CheckBox}
     */
    public CheckBox getEarthquakesCheckBox() {
        return earthquakesCheckBox;
    }

    /**
     * Returns the USGS earthquake lookback window selector (1 to 7 days).
     *
     * @return the earthquake days {@link ComboBox}
     */
    public ComboBox<Integer> getEarthquakeDaysCombo() {
        return earthquakeDaysCombo;
    }

    /**
     * Returns the map layers dropdown menu button.
     *
     * @return the layers {@link MenuButton}
     */
    public MenuButton getLayersMenuButton() {
        return layersMenuBtn;
    }

    private void applySatelliteView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setSatelliteView(%b);", satelliteViewActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying satellite view in WebView", e);
            }
        });
    }

    private void applyTerrainView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setTerrainView(%b);", terrainViewActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying terrain view in WebView", e);
            }
        });
    }

    private void applyDayNightView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setDayNightView(%b);", dayNightActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying day/night view in WebView", e);
            }
        });
    }

    private void applyTrafficView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setTrafficView(%b);", trafficActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying traffic view in WebView", e);
            }
        });
    }

    private void applyTransitView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setTransitView(%b);", transitActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying transit view in WebView", e);
            }
        });
    }

    private void applyBikingView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setBikingView(%b);", bikingActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying biking view in WebView", e);
            }
        });
    }

    private void applyAirQualityView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setAirQualityView(%b);", airQualityActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying air quality view in WebView", e);
            }
        });
    }

    private void applyWildfiresView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setWildfiresView(%b);", wildfiresActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying wildfires view in WebView", e);
            }
        });
    }

    private void applyWindyView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                String key = mapService != null ? mapService.getWindyApiKey() : "pQl6w8kuhxhv8plg3GjbATVEkVj3TdOJ";
                webEngine.executeScript(
                        String.format(Locale.US, "setWindyView(%b, '%s');", windyActive, escapeJavaScript(key)));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying windy wind patterns view in WebView", e);
            }
        });
    }

    private void applyNauticalView() {
        if (!isLoaded)
            return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(String.format(Locale.US, "setNauticalView(%b);", nauticalActive));
            } catch (Exception e) {
                logger.error("[MapWindowView] Error applying nautical view in WebView", e);
            }
        });
    }

    /**
     * Updates the UI button labels, CSS styles, and badges based on the active
     * state of flight and satellite trackers.
     */
    public void updateTrackingButtonsState() {
        Platform.runLater(() -> {
            boolean planesActive = aviationService != null && aviationService.isTrackingActive();
            boolean satellitesActive = satelliteService != null && satelliteService.isTrackingActive();
            boolean earthquakesActive = earthquakeService != null && earthquakeService.isTrackingActive();

            if (planesCheckBox != null && planesCheckBox.isSelected() != planesActive) {
                planesCheckBox.setSelected(planesActive);
            }
            if (satellitesCheckBox != null && satellitesCheckBox.isSelected() != satellitesActive) {
                satellitesCheckBox.setSelected(satellitesActive);
            }
            if (earthquakesCheckBox != null && earthquakesCheckBox.isSelected() != earthquakesActive) {
                earthquakesCheckBox.setSelected(earthquakesActive);
            }

            int activeQuakeDays = earthquakeService != null ? earthquakeService.getLookbackDays()
                    : (earthquakeDaysCombo != null && earthquakeDaysCombo.getValue() != null ? earthquakeDaysCombo.getValue() : 1);
            if (earthquakeDaysMenuLabel != null) {
                earthquakeDaysMenuLabel.setText(activeQuakeDays + "d");
            }
            if (earthquakeDaysCombo != null && !Integer.valueOf(activeQuakeDays).equals(earthquakeDaysCombo.getValue())) {
                updatingQuakeDaysControl = true;
                try {
                    earthquakeDaysCombo.setValue(activeQuakeDays);
                } finally {
                    updatingQuakeDaysControl = false;
                }
            }

            int activeLayers = 0;
            if (planesActive)
                activeLayers++;
            if (satellitesActive)
                activeLayers++;
            if (earthquakesActive)
                activeLayers++;
            if (satelliteViewActive)
                activeLayers++;
            if (terrainViewActive)
                activeLayers++;
            if (dayNightActive)
                activeLayers++;
            if (trafficActive)
                activeLayers++;
            if (transitActive)
                activeLayers++;
            if (bikingActive)
                activeLayers++;
            if (airQualityActive)
                activeLayers++;
            if (wildfiresActive)
                activeLayers++;
            if (windyActive)
                activeLayers++;
            if (nauticalActive)
                activeLayers++;

            if (layersMenuBtn != null) {
                layersMenuBtn.setText("🗺 Layers & Tracking (" + activeLayers + ")");
                if (activeLayers > 0) {
                    layersMenuBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: #ffffff; " +
                            "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 5 12; " +
                            "-fx-effect: dropshadow(three-pass-box, rgba(37, 99, 235, 0.4), 6, 0, 0, 1);");
                } else {
                    layersMenuBtn.setStyle("-fx-background-color: #27272a; -fx-text-fill: #a1a1aa; " +
                            "-fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 5 12;");
                }
            }

            if (satelliteCheckBox != null && satelliteCheckBox.isSelected() != satelliteViewActive) {
                satelliteCheckBox.setSelected(satelliteViewActive);
            }
            if (terrainCheckBox != null && terrainCheckBox.isSelected() != terrainViewActive) {
                terrainCheckBox.setSelected(terrainViewActive);
            }
            if (dayNightCheckBox != null && dayNightCheckBox.isSelected() != dayNightActive) {
                dayNightCheckBox.setSelected(dayNightActive);
            }
            if (trafficCheckBox != null && trafficCheckBox.isSelected() != trafficActive) {
                trafficCheckBox.setSelected(trafficActive);
            }
            if (transitCheckBox != null && transitCheckBox.isSelected() != transitActive) {
                transitCheckBox.setSelected(transitActive);
            }
            if (bikingCheckBox != null && bikingCheckBox.isSelected() != bikingActive) {
                bikingCheckBox.setSelected(bikingActive);
            }
            if (airQualityCheckBox != null && airQualityCheckBox.isSelected() != airQualityActive) {
                airQualityCheckBox.setSelected(airQualityActive);
            }
            if (wildfiresCheckBox != null && wildfiresCheckBox.isSelected() != wildfiresActive) {
                wildfiresCheckBox.setSelected(wildfiresActive);
            }
            if (windyCheckBox != null && windyCheckBox.isSelected() != windyActive) {
                windyCheckBox.setSelected(windyActive);
            }
            if (nauticalCheckBox != null && nauticalCheckBox.isSelected() != nauticalActive) {
                nauticalCheckBox.setSelected(nauticalActive);
            }

            if (statusBadge != null) {
                int objCount = mapService.getObjects().size();
                int activeTrackers = (planesActive ? 1 : 0) + (satellitesActive ? 1 : 0) + (earthquakesActive ? 1 : 0);
                if (activeTrackers > 1) {
                    statusBadge.setText("● Tracking Active (" + objCount + " objects)");
                    statusBadge
                            .setStyle("-fx-background-color: #065f46; -fx-text-fill: #6ee7b7; -fx-font-size: 11px; " +
                                    "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold;");
                } else if (planesActive) {
                    statusBadge.setText("● Planes Active (" + objCount + " objects)");
                    statusBadge
                            .setStyle("-fx-background-color: #065f46; -fx-text-fill: #6ee7b7; -fx-font-size: 11px; " +
                                    "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold;");
                } else if (satellitesActive) {
                    statusBadge.setText("● Satellites Active (" + objCount + " objects)");
                    statusBadge
                            .setStyle("-fx-background-color: #581c87; -fx-text-fill: #d8b4fe; -fx-font-size: 11px; " +
                                    "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold;");
                } else if (earthquakesActive) {
                    statusBadge.setText("● Earthquakes (" + activeQuakeDays + "d · " + objCount + " objects)");
                    statusBadge
                            .setStyle("-fx-background-color: #7c2d12; -fx-text-fill: #fdba74; -fx-font-size: 11px; " +
                                    "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold;");
                } else {
                    statusBadge.setText("● Standby (" + objCount + " objects)");
                    statusBadge
                            .setStyle("-fx-background-color: #27272a; -fx-text-fill: #94a3b8; -fx-font-size: 11px; " +
                                    "-fx-padding: 4 10; -fx-background-radius: 12; -fx-font-weight: bold;");
                }
            }
        });
    }

    private void replayHistory() {
        if (!isLoaded)
            return;

        Platform.runLater(() -> {
            try {
                webEngine.executeScript("clearMap();");

                List<MapObject> objects = mapService.getObjects();
                for (MapObject obj : objects) {
                    String color = getHexColor(obj.name);
                    for (Location loc : obj.path) {
                        Map<String, Object> meta = (loc.metadata != null && !loc.metadata.isEmpty()) ? loc.metadata
                                : obj.metadata;
                        String metaJson = "null";
                        if (meta != null && !meta.isEmpty()) {
                            try {
                                metaJson = objectMapper.writeValueAsString(meta);
                            } catch (Exception e) {
                                logger.warn("Failed to serialize location metadata", e);
                            }
                        }
                        String script = String.format(Locale.US, "plotObject('%s', %f, %f, %f, '%s', %b, '%s', %s);",
                                escapeJavaScript(obj.name), loc.latitude, loc.longitude, loc.heading, color,
                                loc.shouldPan, escapeJavaScript(loc.iconType), metaJson);
                        webEngine.executeScript(script);
                    }
                }

                List<MapFlag> flags = mapService.getFlags();
                for (MapFlag flag : flags) {
                    String script = String.format(Locale.US, "setFlag(%f, %f, '%s');",
                            flag.latitude, flag.longitude, escapeJavaScript(flag.color));
                    webEngine.executeScript(script);
                }

                updateTrackingButtonsState();
            } catch (Exception e) {
                logger.error("[MapWindowView] Error executing map script", e);
            }
        });
    }

    private String getHexColor(String name) {
        int hash = name.hashCode();
        int index = Math.abs(hash) % 6;
        return switch (index) {
            case 0 -> "#00ffff";
            case 1 -> "#adff2f";
            case 2 -> "#ffa500";
            case 3 -> "#ff00ff";
            case 4 -> "#ff69b4";
            default -> "#00ff7f";
        };
    }

    private String escapeJavaScript(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Queries the active map coordinate bounds (SouthWest and NorthEast) from the
     * JavaScript Leaflet engine.
     *
     * @return a JSON or string representation of the current bounding box
     *         coordinates, or {@code null} if uninitialized
     */
    public String getMapBounds() {
        if (!isLoaded)
            return null;
        try {
            Object result = webEngine.executeScript("getMapBounds();");
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            logger.error("Error executing getMapBounds in WebView", e);
        }
        return null;
    }

    /**
     * Bridges resolved FAA aircraft registration data directly to the active
     * Leaflet WebView.
     *
     * @param planeName the aircraft callsign or entity identifier
     * @param record    the resolved FAA registration record
     */
    public void notifyFaaLoaded(String planeName, FaaAircraftRecord record) {
        if (record == null)
            return;
        Platform.runLater(() -> {
            try {
                String json = objectMapper.writeValueAsString(record.toMap());
                String script = String.format(Locale.US,
                        "if (typeof updatePlaneCard === 'function') { updatePlaneCard('%s', %s); }",
                        escapeJavaScript(planeName), json);
                webEngine.executeScript(script);
            } catch (Exception e) {
                logger.warn("[MapWindowView] Error executing updatePlaneCard script: {}", e.getMessage());
            }
        });
    }

    /**
     * JavaFX WebView JavaScript bridge for asynchronous FAA Aviation Registry
     * queries and web actions.
     */
    public static class AviationBridge {
        private final MapWindowView mapView;
        private final FaaRegistryService faaRegistryService;

        /**
         * Constructs an {@code AviationBridge} with parent map view and FAA registry
         * service.
         *
         * @param mapView            parent map view component
         * @param faaRegistryService FAA civil aviation registry service
         */
        public AviationBridge(MapWindowView mapView, FaaRegistryService faaRegistryService) {
            this.mapView = mapView;
            this.faaRegistryService = faaRegistryService;
        }

        /**
         * Asynchronously queries the FAA Aviation Registry for an aircraft and notifies
         * the map view upon completion.
         *
         * @param planeName callsign or identifier
         * @param nNumber   FAA tail registration number
         * @param icaoHex   Mode S 24-bit hexadecimal address
         */
        public void queryFaa(String planeName, String nNumber, String icaoHex) {
            if (faaRegistryService == null) {
                return;
            }

            String targetN = nNumber;
            if (targetN == null || targetN.isEmpty() || targetN.equalsIgnoreCase("null")
                    || targetN.equalsIgnoreCase("undefined")) {
                targetN = IcaoNNumberConverter.extractNNumber(planeName, icaoHex);
            }

            if (targetN != null && !targetN.isEmpty()) {
                faaRegistryService.queryNNumberAsync(targetN)
                        .thenAccept(record -> mapView.notifyFaaLoaded(planeName, record));
            } else if (icaoHex != null && !icaoHex.isEmpty() && !icaoHex.equalsIgnoreCase("null")) {
                faaRegistryService.queryIcaoAsync(icaoHex)
                        .thenAccept(record -> mapView.notifyFaaLoaded(planeName, record));
            }
        }

        /**
         * Opens the official FAA inquiry or documentation URL in the host operating
         * system's browser.
         *
         * @param url destination web URL
         */
        public void openExternalUrl(String url) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Exception e) {
                logger.warn("[AviationBridge] Failed to open external browser for {}: {}", url, e.getMessage());
            }
        }
    }
}
