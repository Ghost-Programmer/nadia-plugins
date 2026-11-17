# nadia-plugins

Official multi-project Gradle repository for **PF4J plugins** targeting the **Nadia Cognitive Agent Operating Environment** (`com.nadia:nadia-plugin-api`).

Plugins in this repository can extend Nadia at runtime with new **AI agent tools** (`@NadiaTool`), **BentoFX dockable desktop windows**, **Settings tabs**, **Left Side Panel widgets**, **background services & scheduled tasks**, **outbound event listeners**, and **procedural skills / staff personas**—all packaged as lightweight `.jar` archives loaded from `~/.nadia/plugins/`.

---

## Table of Contents

1. [Available Plugins](#1-available-plugins)
2. [Prerequisites & Repository Layout](#2-prerequisites--repository-layout)
3. [Building, Versioning & Installing Plugins](#3-building-versioning--installing-plugins)
4. [Developer Guide: How to Build a Plugin for Nadia](#4-developer-guide-how-to-build-a-plugin-for-nadia)
   - [Step 1: Scaffold & Register a New Subproject](#step-1-scaffold--register-a-new-subproject)
   - [Step 2: Implement the Plugin Lifecycle Class (`NadiaPlugin`)](#step-2-implement-the-plugin-lifecycle-class-nadiaplugin)
   - [Step 3: Implement PF4J `@Extension` Points](#step-3-implement-pf4j-extension-points)
   - [Step 4: Interact with Nadia via `NadiaPluginContext`](#step-4-interact-with-nadia-via-nadiaplugincontext)
   - [Step 5: Critical Classloading, Packaging & Windows File-Lock Rules](#step-5-critical-classloading-packaging--windows-file-lock-rules)
   - [Step 6: Testing Your Plugin](#step-6-testing-your-plugin)
5. [Installing & Managing Plugins in Nadia](#5-installing--managing-plugins-in-nadia)
6. [CI/CD & Automated GitHub Releases](#6-cicd--automated-github-releases)

---

## 1. Available Plugins

| Subproject | Plugin ID | Description |
| :--- | :--- | :--- |
| [`world-map-viewer`](./world-map-viewer) | `world-map-viewer` | Interactive Leaflet.js World Map Viewer (`MapWindowView`) with GIS plotting tools (`MapTool`), live OpenSky Network flight tracking (`AviationTool`), FAA Civil Aviation Registry lookup (`FaaRegistryService`), N2YO orbital satellite tracking (`SatelliteTool`), and live USGS earthquake tracking with 1–7 day lookback (`EarthquakeTool`). |

---

## 2. Prerequisites & Repository Layout

- **Java Toolchain**: **Java 25** (automatically provisioned by Gradle via Foojay Toolchain Resolver if not already installed; bytecode is compiled with `--release 23` and `-parameters`).
- **Nadia Plugin SDK (`com.nadia:nadia-plugin-api`)**:
  - A copy of `nadia-plugin-api` is bundled in `./maven-repo/` so this repository builds standalone out of the box (including in GitHub Actions).
  - When developing locally alongside the `../nadia` repository, `mavenLocal()` takes precedence. To publish local SDK changes from `nadia`:
    ```powershell
    cd ..\nadia
    .\gradlew.bat :nadia-plugin-api:publishToMavenLocal :nadia-plugin-api:syncToSiblingPluginsRepo
    ```

```text
nadia-plugins/
├── .github/workflows/release.yml   # Automated semver bump, build & GitHub Release pipeline
├── dist/                           # Generated final plugin JARs (<plugin-id>-<version>.jar)
├── maven-repo/                     # Embedded com.nadia:nadia-plugin-api Maven artifacts
├── world-map-viewer/               # Reference full-stack UI + Tools PF4J plugin
├── build.gradle                    # Shared Java 25, PF4J processor, packagePlugins & installPlugin config
├── gradle.properties               # Global & per-plugin version configuration
├── settings.gradle                 # Multi-project subproject registry
├── build.bat                       # Windows build, version & install script
└── build.sh                        # macOS / Linux build, version & install script
```

---

## 3. Building, Versioning & Installing Plugins

Default versions are managed in [`gradle.properties`](./gradle.properties):
- `pluginsVersion=1.0.0` — Default version inherited by all plugin subprojects.
- `pluginApiVersion=1.0.0` — Target `com.nadia:nadia-plugin-api` version.
- `<subproject-name>.version=X.Y.Z` — Optional per-plugin version override (e.g., `world-map-viewer.version=1.3.0`).

### Using `build.bat` (Windows) / `build.sh` (macOS & Linux)

```powershell
# Build all plugin JARs into dist/ (skips unit tests for fast packaging):
.\build.bat                          # Windows
./build.sh                           # macOS / Linux

# Run all unit & integration tests and install built JARs into ~/.nadia/plugins/:
.\build.bat --with-tests --install
./build.sh --with-tests --install

# Build with a one-off version override (does not modify gradle.properties):
.\build.bat --version 1.2.0

# Persist a new global pluginsVersion to gradle.properties and build:
.\build.bat --set-version 1.2.0

# Persist an independent version for a single plugin subproject and build:
.\build.bat --plugin world-map-viewer --set-version 1.3.0
```

### Direct Gradle Tasks

```powershell
.\gradlew.bat packagePlugins                  # Build all plugins and sync JARs into dist/
.\gradlew.bat installPlugin                   # Install all plugins into ~/.nadia/plugins/ (removes older versions)
.\gradlew.bat :world-map-viewer:installPlugin # Build & install a single plugin
.\gradlew.bat test                            # Run JUnit 5 test suites across all plugins
```

---

## 4. Developer Guide: How to Build a Plugin for Nadia

Every Nadia plugin is a standard PF4J plugin JAR built as a Gradle subproject in this repository (or in an external Gradle project referencing `com.nadia:nadia-plugin-api`).

### Step 1: Scaffold & Register a New Subproject

1. Add your subproject directory (e.g., `weather-radar/`) to [`settings.gradle`](./settings.gradle):
   ```groovy
   include 'world-map-viewer'
   include 'weather-radar'
   ```
2. Create `weather-radar/build.gradle`. The root [`build.gradle`](./build.gradle) automatically configures Java 25, `--release 23`, `-parameters`, `com.nadia:nadia-plugin-api`, the PF4J `@Extension` annotation processor (`META-INF/extensions.idx`), JUnit 5 + Mockito, and `installPlugin`. Your subproject `build.gradle` only needs plugin-specific `compileOnly` dependencies and the PF4J JAR manifest:

   ```groovy
   plugins {
       // Include ONLY if your plugin contributes JavaFX UI views
       id 'org.openjfx.javafxplugin'
   }

   description = 'Live NOAA NEXRAD weather radar viewer and forecast tools for Nadia.'

   // If using JavaFX, ALWAYS keep configuration = 'compileOnly' so JavaFX is not fat-jarred
   javafx {
       version = '23'
       modules = [ 'javafx.controls', 'javafx.fxml', 'javafx.web' ]
       configuration = 'compileOnly'
   }

   dependencies {
       // Host libraries already shipped in Nadia MUST be compileOnly
       compileOnly 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
   }

   tasks.named('jar') {
       archiveBaseName = 'weather-radar'
       manifest {
           attributes(
               'Plugin-Id': 'weather-radar',
               'Plugin-Version': project.version.toString(),
               'Plugin-Provider': 'Your Name or Organization',
               'Plugin-Description': project.description,
               'Plugin-License': 'Apache-2.0',
               'Plugin-Class': 'com.nadia.plugin.radar.WeatherRadarPlugin'
           )
       }
   }
   ```

---

### Step 2: Implement the Plugin Lifecycle Class (`NadiaPlugin`)

Create the class referenced by `Plugin-Class` in your manifest by extending `com.nadia.app.plugin.api.NadiaPlugin`. Use `onStart(NadiaPluginContext)` to initialize shared plugin services, register `nadia.prop.*` keys, or extract static resources, and `onStop(NadiaPluginContext)` to cleanly shut down threads and listeners:

```java
package com.nadia.plugin.radar;

import com.nadia.app.plugin.api.NadiaPlugin;
import com.nadia.app.plugin.api.NadiaPluginContext;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.List;

public class WeatherRadarPlugin extends NadiaPlugin {

    public WeatherRadarPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected void onStart(NadiaPluginContext context) {
        // Register configuration keys so they appear in Nadia -> System Settings -> Properties
        context.registerProperties(
                List.of("nadia.prop.radar.apiKey", "nadia.prop.radar.defaultStation"),
                "Plugin: weather-radar"
        );

        // Dedicated persistent storage directory: ~/.nadia/plugins/data/weather-radar/
        Path dataDir = context.getPluginDataDir("weather-radar");
        logger.info("Weather Radar plugin initialized with data dir: {}", dataDir);
    }

    @Override
    protected void onStop(NadiaPluginContext context) {
        // Stop executors, timers, sockets, or background streams here
        logger.info("Weather Radar plugin stopped cleanly.");
    }
}
```

---

### Step 3: Implement PF4J `@Extension` Points

Annotate classes with `@org.pf4j.Extension` and implement one or more of Nadia's four extension point interfaces. At compile time, PF4J's annotation processor automatically indexes all `@Extension` classes into `META-INF/extensions.idx`.

#### A. Agent Tools (`NadiaToolExtension` + `@NadiaTool`)

Expose callable tools to Nadia, all specialist Staff personas, Spring AI, and Antigravity by implementing `NadiaToolExtension` and annotating public methods with `@NadiaTool` and `@NadiaToolParam`:

```java
package com.nadia.plugin.radar;

import com.nadia.app.annotation.NadiaTool;
import com.nadia.app.annotation.NadiaToolParam;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaToolExtension;
import org.pf4j.Extension;

@Extension
public class WeatherRadarTool implements NadiaToolExtension {

    private final NadiaPluginContext context;

    // NadiaSpringExtensionFactory automatically injects NadiaPluginContext!
    public WeatherRadarTool(NadiaPluginContext context) {
        this.context = context;
    }

    @NadiaTool(
        description = "Fetches current NEXRAD radar precipitation summary for a station and opens the Radar view.",
        properties = { "nadia.prop.radar.apiKey" }
    )
    public String getRadarSummary(
            @NadiaToolParam(description = "4-letter ICAO radar station code (e.g., KTLX, KORD)") String stationId,
            @NadiaToolParam(description = "Range radius in kilometers (default 100)", required = false) Integer radiusKm
    ) {
        String apiKey = context.getProperty("nadia.prop.radar.apiKey", "");
        int effectiveRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 100;

        // Open the plugin's dockable window on Nadia's desktop canvas if available
        context.openDockable("weather-radar");

        return "{\"success\": true, \"station\": \"" + stationId + "\", \"radius_km\": " + effectiveRadius + "}";
    }
}
```
> **Dynamic Tools**: If tool schemas aren't known until runtime, you can also override `List<AgentTool> getDynamicTools()` on `NadiaToolExtension`.

#### B. Desktop UI Windows, Settings Tabs & Side Panels (`NadiaUiExtension`)

Contribute BentoFX dockable workspace windows (automatically listed under Nadia's **View** menu), custom tabs inside **System Settings**, or widgets in the **Left Side Panel**:

```java
package com.nadia.plugin.radar;

import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.NadiaUiExtension;
import com.nadia.app.plugin.api.PluginDockableDescriptor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import org.pf4j.Extension;

import java.util.Optional;

@Extension
public class WeatherRadarUiExtension implements NadiaUiExtension {

    @Override
    public Optional<PluginDockableDescriptor> createDockableWindow(NadiaPluginContext context) {
        return Optional.of(new PluginDockableDescriptor(
                "weather-radar",          // Unique dockable ID (used by context.openDockable("weather-radar"))
                "🌦 Weather Radar",        // Window tab & View menu title
                "center-right",           // Target BentoFX dock region (e.g., "center-right", "bottom-left")
                () -> new StackPane(new Label("Live Radar Canvas")) // Invoked on JavaFX Application Thread
        ));
    }

    @Override
    public Optional<Tab> createSettingsTab(NadiaPluginContext context) {
        Tab tab = new Tab("Weather Radar", new StackPane(new Label("Radar Settings")));
        tab.setClosable(false);
        return Optional.of(tab);
    }

    @Override
    public Optional<Node> createSidePanelSection(NadiaPluginContext context) {
        return Optional.empty();
    }
}
```

#### C. Background Services, Event Bus & Scheduled Tasks (`NadiaBackgroundServiceExtension`)

Subscribe to Nadia's outbound system event bus or schedule recurring background tasks executed on virtual threads while your plugin is enabled:

```java
package com.nadia.plugin.radar;

import com.nadia.app.plugin.api.NadiaBackgroundServiceExtension;
import com.nadia.app.plugin.api.NadiaPluginContext;
import com.nadia.app.plugin.api.PluginScheduledTask;
import org.pf4j.Extension;

import java.time.Duration;
import java.util.List;

@Extension
public class WeatherAlertServiceExtension implements NadiaBackgroundServiceExtension {

    @Override
    public String getServiceName() {
        return "NOAA Severe Weather Watcher";
    }

    @Override
    public void onSystemEvent(String source, String eventType, String target, String payload, NadiaPluginContext context) {
        // React to outbound Nadia events (printer alerts, network events, camera triggers, etc.)
    }

    @Override
    public List<PluginScheduledTask> getScheduledTasks(NadiaPluginContext context) {
        return List.of(
                new PluginScheduledTask(
                        "radar-severe-sweep",
                        "Poll severe weather alerts",
                        Duration.ofMinutes(10),
                        () -> {
                            // Publish outbound alert to Nadia's Staff Event Bus if needed:
                            // context.publishEvent("plugin", "severe_weather", "KORD", "Tornado Watch issued");
                        }
                )
        );
    }
}
```

#### D. Procedural Skills & Specialist Staff (`NadiaSkillAndStaffExtension` or Bundled Resources)

Plugins can contribute procedural `SKILL.md` capabilities and `STAFF.md` personas in two ways (when a skill/staff belongs exclusively to a plugin; shared core skills live in `nadia-skills` and `nadia-staff`):
1. **Resource Bundling**: Place `skills/<category>/<skill-slug>/SKILL.md` or `staff/<StaffName>/STAFF.md` inside `src/main/resources/`—Nadia extracts and registers them automatically on plugin load.
2. **Programmatic Extension**: Implement `NadiaSkillAndStaffExtension` (`getBundledSkills()` returning `List<BundledSkillDescriptor>` and `getBundledStaff()` returning `List<BundledStaffDescriptor>`).

---

### Step 4: Interact with Nadia via `NadiaPluginContext`

`NadiaSpringExtensionFactory` instantiates every `@Extension` class and supports:
- **Constructor Injection**: `public MyExtension(NadiaPluginContext context)`
- **Field Injection**: Any `NadiaPluginContext` field on an `@Extension` class is automatically populated, and `@Autowired` fields are wired against Nadia's host Spring `ApplicationContext`.
- **Plugin Access**: Inside `NadiaPlugin`, call `getPluginContext()` (or use the `context` argument passed to `onStart`/`onStop`).

| `NadiaPluginContext` Method | Purpose |
| :--- | :--- |
| `getProperty(key, defaultValue)` | Reads a property from `~/.nadia/nadia.properties` (auto-normalizes `nadia.prop.*`). |
| `setProperty(key, value)` | Persists a configuration value to `~/.nadia/nadia.properties`. |
| `registerProperties(keys, source)` | Surfaces required/optional `nadia.prop.*` keys in **System Settings → Properties**. |
| `openDockable(dockableId)` | Opens or focuses a BentoFX dockable window on Nadia's workspace canvas. |
| `getPluginDataDir(pluginId)` | Returns `~/.nadia/plugins/data/<pluginId>/` (created automatically) for persistent plugin files/caches. |
| `getNadiaHomeDir()` | Returns `~/.nadia` (or custom `NADIA_HOME` / `-Dnadia.home`). |
| `publishEvent(source, type, target, details)` | Dispatches an event onto Nadia's autonomous `StaffEventService` trigger matrix. |
| `storeBrief(category, staffName, code, md)` | Writes an entry into Nadia's daily executive brief (`BriefService`). |
| `saveMemory(content, category, tags)` | Stores a semantic fact in Nadia's episodic vector store (`NadiaVectorStore`). |
| `searchMemory(query, topK)` | Performs cosine-similarity semantic recall against Nadia's episodic memory. |
| `getSpringBean(Class<T>)` | Safely looks up an optional Spring bean from Nadia's host `ApplicationContext`. |

---

### Step 5: Critical Classloading, Packaging & Windows File-Lock Rules

Follow these four rules to avoid classloader conflicts and file locks:

1. **Keep Host Dependencies `compileOnly`**:
   Nadia already provides `nadia-plugin-api`, PF4J, Spring Boot / Spring AI, Jackson (`jackson-databind`), SLF4J, and JavaFX 23 on the parent classloader. Always declare these as `compileOnly` (and `javafx { configuration = 'compileOnly' }`). Fat-jarring host libraries into your plugin `.jar` will cause `LinkageError` or `ClassCastException` across PF4J's `PluginClassLoader`.
2. **Bundle Only Plugin-Exclusive Libraries**:
   If your plugin needs a small third-party library that Nadia does *not* ship, include only that dependency in your plugin JAR.
3. **Never Load `jar:file:` URLs Directly in JavaFX `WebView` on Windows**:
   JavaFX WebKit holds OS file handles on `jar:file:...` resources, which prevents Windows from deleting or upgrading `~/.nadia/plugins/<plugin>.jar` while Nadia is running. Instead, extract HTML/JS/CSS assets into `context.getPluginDataDir(pluginId)` during `onStart()` and load them via `Path.toUri().toString()` (`file:///`). See [`WorldMapRuntime.java`](./world-map-viewer/src/main/java/com/nadia/plugin/worldmap/WorldMapRuntime.java) for a complete reference implementation.
4. **Release Threads & Connections in `onStop()`**:
   Shut down all `ScheduledExecutorService` pools, WebSockets, and HTTP polling loops inside `onStop(NadiaPluginContext)` (and `onPluginStopped`) so plugins can be hot-disabled, upgraded, and uninstalled cleanly at runtime.

---

### Step 6: Testing Your Plugin

Root [`build.gradle`](./build.gradle) configures `testImplementation.extendsFrom compileOnly`, so JUnit 5 tests have full access to `nadia-plugin-api`, Jackson, and JavaFX classes without bundling them into the production JAR:

1. **Unit test services and `@NadiaTool` classes** directly using mocked `NadiaPluginContext` (`org.mockito:mockito-core`).
2. **Verify PF4J extension indexing**: Confirm `getClass().getClassLoader().getResource("META-INF/extensions.idx")` contains all `@Extension` classes and that `NadiaPlugin` starts and stops cleanly with a temporary `getPluginDataDir` (`@TempDir`). See [`WorldMapPluginIntegrationTest.java`](./world-map-viewer/src/test/java/com/nadia/plugin/worldmap/WorldMapPluginIntegrationTest.java) for a complete example.
3. Run tests via:
   ```powershell
   .\gradlew.bat test
   ```

---

## 5. Installing & Managing Plugins in Nadia

Built plugin `.jar` files can be installed in any of four ways:

1. **Developer CLI Install**: Run `.\build.bat --install` (or `./build.sh --install`), which builds all plugins, removes older `<plugin-id>-*.jar` versions from `~/.nadia/plugins/`, and copies the latest JARs in place.
2. **Nadia Runtime UI (Local Disk)**: Open **⚙️ System Settings → Plugins → Install from Disk...** and select `dist/<plugin-id>-<version>.jar`—Nadia loads and activates tools and UI windows immediately without restarting.
3. **Nadia Runtime UI (GitHub Release URL)**: Open **⚙️ System Settings → Plugins → Install from URL...** and enter `https://github.com/Ghost-Programmer/nadia-plugins` (or a direct `.jar` release asset URL).
4. **Standalone Nadia ZIP Drop Folder**: Place any `<plugin-id>-<version>.jar` into the `plugins/` folder inside an extracted `nadia-<version>.zip` distribution; `run.bat` / `run.sh` syncs them into `~/.nadia/plugins/` on launch.

---

## 6. CI/CD & Automated GitHub Releases

[`.github/workflows/release.yml`](./.github/workflows/release.yml) runs automatically on every push to `main` (and supports manual **Run workflow** dispatch):

1. Increments `pluginsVersion` in [`gradle.properties`](./gradle.properties) (`patch` by default on push, or `patch` / `minor` / `major` / explicit `custom_version` via `workflow_dispatch`).
2. Executes `./build.sh --set-version <X.Y.Z> --with-tests` on Java 25, running all JUnit suites and assembling `dist/*.jar`.
3. Commits the updated `gradle.properties` (`chore(release): v<X.Y.Z> [skip ci]`), tags `v<X.Y.Z>`, and publishes all built `.jar` files to **GitHub Releases**.
