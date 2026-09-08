# osm2xp Project Architecture

## Overview
OSM2XP converts OpenStreetMap data into flight simulator scenery. Originally built for X-Plane (9/10/11), now also supports FlightGear (BUILDING_LIST mode, Phase 1).

## Module Structure

| Module | Type | Description |
|---|---|---|
| `com.osm2xp.core` | eclipse-plugin (Tycho) | Core interfaces: IParser, IOSMDataVisitor, ITranslator, model classes (Node, Way, Relation, Tag) |
| `com.osm2xp.generation` | eclipse-plugin (Tycho) | Translators, options, geometry utils, building analysis. This is where most new code goes. |
| `com.osm2xp.classification.core` | eclipse-plugin (Tycho) | OSM classification logic |
| `com.osm2xp.console` | jar (plain Maven) | CLI entry point (`App.main()`). Depends on above as JARs, not via Tycho. |
| `com.osm2xp.application` | eclipse-plugin (Tycho) | Eclipse RCP UI application |
| `com.osm2xp.product` | eclipse-plugin (Tycho) | Eclipse product definition |
| `com.osm2xp.classification` | eclipse-plugin (Tycho) | Classification UI |
| `releng-core` | pom (Tycho parent) | Reactor parent for the Tycho modules (core, generation, classification.core) |
| `releng-console` | pom (Maven parent) | Parent for the console module |
| `releng-app` | pom (Tycho parent) | Parent for Eclipse RCP modules |
| `releng` | pom | Root parent that includes all releng-* sub-reactors |

## Build System

- **JDK 11 required** (LibericaJDK-11 at `C:\Program Files\BellSoft\LibericaJDK-11`). JDK 21 fails because Tycho 1.5.0 can't resolve JavaSE-21.
- **Maven 3.9.12** with Tycho 1.5.0 for eclipse-plugin modules.
- Two separate Maven hierarchies:
  - `releng-core/` — Tycho reactor: builds `com.osm2xp.core`, `com.osm2xp.generation`, `com.osm2xp.classification.core`
  - `releng-console/` — plain Maven: builds `com.osm2xp.console`
- Console module depends on eclipse-plugin modules as Maven JARs (`1.0.0-SNAPSHOT` for core/generation, `4.6.2-SNAPSHOT` for classification.core).
- After changing code in an eclipse-plugin module, must run `mvn install -f releng-core/pom.xml -DskipTests` first so the console module picks up the new JAR.

### Build Commands
```powershell
# Full Tycho build (core + generation)
$env:JAVA_HOME = "C:\Program Files\BellSoft\LibericaJDK-11"
mvn install -f releng-core/pom.xml -DskipTests

# Run console integration tests
mvn test -f com.osm2xp.console/pom.xml -Dtest=ConsoleAppIntegrationTest

# Full reactor build (both Tycho + console in one go)
mvn install -f releng/pom.xml -DskipTests
```

## Translator Architecture

### Pipeline
```
OSM PBF → TranslatingBinaryParser → IOSMDataVisitor (MultiTileDataConverter or GeneralTranslatingConverter)
  → TileTranslationAdapter.processWays() → ITranslator.processPolyline()
```

### Interface Hierarchy
- **`IBasicTranslator`** — base: `processNode()`, `init()`, `complete()`, `mustStoreNode()`, `mustProcessPolyline()`, `processBoundingBox()`
- **`ITranslator`** extends `IBasicTranslator` — adds `processPolyline(OsmPolyline)` and `getMaxHoleCount()`
- **`ISpecificTranslator`** extends `IBasicTranslator` — adds `processWays()` for geometry-based dispatch
- **`IPolyHandler`** — independent interface for entity-specific handlers with `handlePoly()`, `translationComplete()`, `getId()`, `isTerminating()`

### Translator Registration
- `TranslatorBuilder` static block registers factory classes by output mode string.
- Factories implement `ITileTranslatorFactory` and/or `ITranslatorProviderFactory`.
- `ITranslatorProviderFactory` → `DefaultTranslatorProvider` → multi-tile parsing with proper tile coordinates.
- `mustProcessPolyline()` gate: must return `true` for the translator to receive any way data.

### X-Plane Translators

All located in `com.osm2xp.generation/src/main/java/com/osm2xp/translators/xplane/`.

**Per-version entry points:**
- `XPlaneTranslatorImpl` — core X-Plane translator (base for XP9/10/11). Orchestrates all sub-translators.
- `Xplane9TranslatorImpl` — XP9 variant; adds street light processing in `processPolyline()`.
- `Xplane10TranslatorImpl` — XP10/11 variant; uses `XP10OutputFormat`.

**Poly Handlers** (registered in `XPlaneTranslatorImpl` constructor, dispatched by `processByHandlers()`):

| Handler | Entity | OSM Tags |
|---|---|---|
| `XPBarrierTranslator` | Walls/fences | `barrier=wall`, `barrier=fence` |
| `XPRoadTranslator` | Roads | `highway=*` (filtered by allowed types in options) |
| `XPRailTranslator` | Railways | `railway=rail` |
| `XPPowerlineTranslator` | Powerlines | `power=line` |
| `XPCoolingTowerTranslator` | Cooling towers | `man_made=cooling_tower`, `tower:type=cooling` |
| `XPChimneyTranslator` | Chimneys | `man_made=chimney` |
| `XPDrapedPolyTranslator` | Draped landcover | User-defined polygon rules |
| `XPForestTranslator` | Forests/woods | User-defined forest tag rules |
| `XP3DObjectByRuleTranslator` | 3D objects by tag | User-defined object tag rules |
| `XPPolyTo3DObjectTranslator` | 3D building models by size | Building polygons; selects model matching footprint |

**Path-based handlers** (road, rail, powerline) share `XPPathTranslator` base class which handles:
- Node accumulation for crossing detection (ways sharing a node)
- Segment splitting at crossings
- Bridge detection (`bridge=yes`, `layer` tag)
- Ramp generation for bridge entrances
- Writing DSF path strings with type index

**Special object handlers** (chimney, cooling tower) use `XPSpecObjectTranslator` base which selects `.obj` file by closest size match from `spec_objects/` folder.

**Street lights:**
- XP10+: `XPRoadTranslator.processLights()` + `XPStringLightTranslator` — generates light strings offset from road centerline for highways, `lit=yes` roads, 3+ lane roads.
- XP9: direct `processStreetLights()` in `Xplane9TranslatorImpl` — places individual `OBJECT` entries along residential roads.

### Airfield Generation (X-Plane)

Handled by `XPAirfieldTranslationAdapter` (an `ISpecificTranslator`), not through the normal poly handler chain.

**Collection phase:**
- `processPolyline()` routes by `aeroway` tag: `aerodrome`/`heliport` → airfield, `runway` → runway list, `apron`/`taxiway` → apron/taxi areas, `helipad` → heli areas.

**Binding phase** (`complete()`):
- Runways bound to airfields via spatial containment (`containsPolyline()`)
- Apron areas, taxi lanes, helipads assigned to airfields
- Synthetic airfields created for unassigned runways near each other (~2km)
- Elevation resolved via `ElevationProvidingService` (REST/cache)
- Names resolved via `GeonameProvidingService` (REST) or local `geo/index.dat`

**Output:** `XPAirfieldOutput` writes X-Plane `apt.dat` format (per XP-APT1050 spec) with airport header, runways, helipads, apron surfaces, boundary, flattening.

### X-Plane Building Generation

Priority-ordered fallback chain in `XPlaneTranslatorImpl.processPolyline()`:
1. **Poly handlers** — check for roads, railways, barriers, etc.
2. **3D object by rule** — match user-defined OSM tag rules to 3D objects
3. **3D model by size** — `XPPolyTo3DObjectTranslator` matches footprint dimensions to models in `/objects/` (e.g., `house_10x10.obj`). If matched: outputs `OBJECT` command.
4. **Facade building** — `processBuilding()` generates facade-based building. If no 3D model matched: generates DSF facade polygon.
5. **Forest** — falls through if nothing else matched.

**Building validation** (`processBuilding()`):
- `OsmUtils.isBuilding()` passes; not excluded by user rules
- Not a special excluded object (tanks, gasometers, small perimeters)
- 3-512 vertices, max segment length within range, min area threshold

**Height computation** (`computeBuildingHeight()`):
- OSM `height` tag → type-based height (`BuildingClassifier.tryGetHeightByType()`, e.g., garages → levelHeight, tanks → diameter) → perimeter-based heuristic → user min/max clamping

**Facade index selection** (`computeFacadeIndex()`):
- User-defined facade rules → special building type (storage tank, gasometer, garage) → sloped roof facade (if enabled, simple polygon, height<20m) → standard house facade (simple rectangle) → complex-shape facade

**Building classification:** `BuildingClassifier` determines `BuildingType` (RESIDENTIAL, COMMERCIAL, INDUSTRIAL) and `SpecialFacadeType` (TANK, GARAGE).

### FlightGear BUILDING_LIST Pipeline (Phase 1)

`FlightGearBuildingAnalyzer.analyze(OsmPolygon)`:
1. Convexity check (rejects non-convex polygons)
2. PCA-oriented minimum rectangle (vertex covariance → principal axis → min/max projections) + area-ratio check (rejects `polygonArea / rectArea < 0.85` — L/T/U/X/H shapes, triangles)
3. Level analysis: `height` tag → `building:levels` tag → distribution table fallback
4. Roof shape: `roof:shape` tag → random distribution (flat=0.1, gabled=0.8, hipped=0.1)
5. Size classification: SMALL (min≥3m, max≥4.5m) / MEDIUM (min≥10m, max≥15m) / LARGE (min≥20m, max≥30m) / UNSUITABLE
6. Street angle from PCA principal axis, roof orientation, texture indices
7. Ground elevation: if fgelev probing is enabled, probes the outer-ring vertices and takes the minimum; buildings over water/`-9999`/`-1000` are rejected (see `FlightGearElevProber`)

Output written to `BuildingList_<index>.txt.gz` (gzip) and `.stg` file header.

**BUILDING_LIST data format:**
STG line: `BUILDING_LIST BuildingList_<index>.txt.gz OSMBuildings {lon_6f} {lat_6f} 0.00`

Data line (14 space-separated fields) — local Cartesian metres relative to the STG anchor (X south, Y east, Z up), round-Earth sagitta corrected:
`X Y Z streetAngle listType width depth facadeHeight roofHeight roofShape roofOrientation levels wallTexIdx roofTexIdx`
where `X = -north`, `Y = east`, `Z = groundElev - calcHorizonElevLocal(...)` (see `FlightGearCoordinateUtils`, mirrors OSM2City).

**Ground elevation (optional):** `FlightGearElevProber` locates the terrain sub-bucket tile for a lon/lat via `FlightGearBucket` (`<sceneryRoot>/Terrain/<band>/<cell>/<bucketIndex>.btg.gz`, the TerraSync on-disk layout) and spawns `fgelev --use-vpb --tile-file <tile>` subprocesses — one per sub-bucket tile, capped by an LRU pool (`MAX_PROCESSES=32`) — then queries them over stdin/stdout. This matches FlightGear ≥ 2024.1, whose `fgelev` is VPB-only and dropped `--tile-lon/--tile-lat`. Sentinels `-9999` (no reliable result) and `-1000` (hole/water); a missing tile also yields `NO_ELEV`. Configure `generateBuildingsElevation`, `fgelevPath`, `flightGearSceneryPath` in `FlightGearOptions.xml`. Terrain can be pre-fetched for a bounding box without running the sim via the cross-platform `tools/DownloadFgTerrain.java` tool (JDK 11+, single-file source launch). Without configuration OSM2XP falls back to elevation 0 (buildings at anchor level).

### FlightGear Transportation (LINE_FEATURE_LIST)

`FGRoadTranslator` / `FGRailTranslator` (`IPolyHandler`s, registered in `FlightGearTranslatorImpl` constructor, collected in `handlePoly` when `generateTransportation=true`) write real scenery at `translationComplete()`:
- One gzipped list file per (bucket, material) named `LineFeatureList_<material>_<index>.txt.gz` next to the STG, managed by `FlightGearBucketOutput.getLineFeatureListWriter(material)`; the STG gets one `LINE_FEATURE_LIST <file> <material>` token per material via `setLineFeatureListHeaderWritten`.
- Row format (per OSM way, mirrors osm2city `_process_line_feature_list`): `W {width:.2f} {isLit} 1 1 1 1 {lon:.6f} {lat:.6f} …` — width in metres, `isLit=0` always (no lit-area analysis yet), attributes `1 1 1 1`.
- Material mapping: `ws30Freeway` for `motorway`/`trunk` (+ `_link`), `ws30Road` otherwise; `ws30Railway` for all accepted `railway=*` values. Widths: `FGRoadTranslator.estimateWidth` (12/8/6/4), railways from `gauge` tag via osm2city's `gauge/1000*128/57`.
- Handlers resolve the bucket via `FlightGearBucketOutputProvider` (functional interface, wired in `FlightGearTranslatorImpl.init()` as `this::bucketOutputFor`).
- **VPB terrain pipeline only** (FlightGear ≥ 2020.3 / WS30): `LINE_FEATURE_LIST` is ignored on legacy WS20 tiles. No elevation probing — wires drape onto terrain.
- `FGPowerlineTranslator` places a shared pylon model (`OBJECT_SHARED_AGL Models/Power/…`) at every node of `power=line`/`power=minor_line` ways. Model selection mirrors osm2city `_calc_and_map_powerline` (wooden pole / H-frame / steel-single / generic 25m·50m), driven by way tags (`cables`, `height`, `material`, `design`) and the max segment length; heading follows the line (middle-angle at interior nodes). Cables/wires are not rendered yet (osm2city emits them as glTF).

All numeric formats must use `Locale.US` (period decimal separator).

## OSM Relation Processing

Handled by `AbstractOSMDataConverter.visit(Relation)` at `com.osm2xp.generation/.../converters/impl/AbstractOSMDataConverter.java`.

**Trigger:** Relation `type=multipolygon` AND tags pass `mustProcessPolyline()`.

**Processing flow:**
1. Separate members by role (`outer`/`inner`), fetching way point IDs from data sink.
2. `getPolygonsFrom()` assembles closed rings — handles single-way closed contours and multi-way chaining (matching start/end node IDs).
3. `doCleanup()` creates JTS `Polygon` objects:
   - Single outer ring: assigns all inners as holes
   - No inner rings: returns outers as simple polygons
   - Multiple outers with inners: uses JTS `covers()` to assign each inner to its containing outer
4. Tags resolved from relation itself (line 106: `relation.getTags()`), not from member ways.
5. Result dispatched to `translatePolys()` → creates `OsmPolygon` objects → `ITranslator.processPolyline()`.

## Options System

- JAXB-annotated POJOs loaded via `XmlHelper.loadFileFromXml()`.
- `XPlaneOptions` / `FlightGearOptions` — platform-specific settings.
- `GlobalOptions` — shared settings (level height, output format, path configs).
- `XPlaneOptionsProvider` / `FlightGearOptionsProvider` — singletons. Load from `{basicFolder}/xplane/XPlaneOptions.xml` / `{basicFolder}/flightgear/FlightGearOptions.xml`.
- Options file path uses `PathsService.getPathsProvider().getBasicFolder()` which can be changed at runtime via `-c` CLI flag.

## Libraries

### OSM Data Processing
- **Osmosis PBF library** (`org.openstreetmap.osmosis:osmbinary`) — binary PBF format parsing via `BinaryParser` and `BlockInputStream` in `TranslatingBinaryParser`.
- **JDK SAX** (`javax.xml.parsers.SAXParser`) — OSM XML parsing via `SaxParserImpl`.

### Geometry
- **JTS** (`org.locationtech.jts:jts-core:1.16.0`) — polygon operations (containment, buffering, fixing, covering).
- **javaGeom** (`math.geom2d:javaGeom:0.11.1`) — 2D geometry (LineSegment2D, Point2D, LinearRing2D, curves).

### Data Storage
- **H2** (`com.h2database:h2:1.3.161`) — embedded database for spatial data index (`IDataSink` implementations).
- **MapDB** (`org.mapdb:mapdb:3.0.7`) — alternative data sink for large datasets.

### Utilities
- **Guava** (`com.google.guava:27.0.1-jre`) — collections (Multimap, Lists, Sets).
- **Trove4j** (`net.sf.trove4j:3.0.3`) — primitive collections for memory efficiency.
- **Commons-IO** (`commons-io:2.6`) — file utilities.
- **Commons-Lang** (`commons-lang:2.6`) — StringUtils, ArrayUtils.
- **ICU4J** (`com.ibm.icu:icu4j:65.1`) — text transliteration (ICAO code generation for airfields).
- **JAXB** (`org.glassfish.jaxb:jaxb-runtime:2.3.1`) — XML binding for options files.
- **Geonames WS Client** (`org.geonames:geonames-ws-client:1.1.9`) — airport name resolution.
- **JSON-simple** (`com.googlecode.json-simple:1.1`) — JSON parsing.

## Test Data
- `testdata/volchikha.osm.pbf` — small airport near Volchikha, Russia. Contains: airport (runway, taxiway, helipads), ~6 building=yes polygons, roads, fence, parking. Airport buildings at ~52.025°N, 80.338°E in tile (80, 52).
- `testdata/xplane/` — X-Plane facade sets and configs.
- `testdata/flightgear/FlightGearOptions.xml` — minimal FlightGear config for testing.

## Conventions
- No comments in code unless explicitly asked.
- Use `java.util.Locale.US` for all `String.format()` calls producing FlightGear output.
- Random instances for roof shape must be seeded for deterministic tests (or accept non-determinism).
- Building analysis in separate service class, not inline in translator.

## Known Issues
- `ProcessExecutor.shutdown()` race condition in X-Plane test (`testGeneration`): executor terminated before async DSF conversion completes. Pre-existing, unrelated to FlightGear.
- `FlightGearTranslatorImpl.mustProcessPolyline()` was returning `false` (pre-existing bug, fixed for Phase 1).
