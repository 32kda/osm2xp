# FlightGear Airfield Terrain Cut (BTG) — Spec

## 1. Overview

Bake an airfield into the FlightGear terrain **by cutting a hole** in the terrain BTG mesh and filling it with a flat airfield plate, rather than the current "merge geometry on top" approach. The airfield sits on a level plate at a single elevation (max terrain height in its area), with a transition skirt down to the surrounding terrain.

**Experimental** — a new mode alongside the existing merge-on-top path.

## 2. Background / existing infrastructure

| Concern | Existing code |
|---|---|
| Terrain download | `tools/DownloadFgTerrain.java` (bbox → 1° cells → `.dirindex` manifest → `.btg.gz`) |
| Elevation probing | `FlightGearElevationIndex` (in-process kd-tree), `FlightGearTerrainElevationProber` (BTG face interpolation) |
| Triangulation | `PolygonTriangulator` (JTS `ConformingDelaunayTriangulationBuilder`, hole-aware) |
| CSG boolean | `eu.mihosoft.jcsg` 0.5.7 (on classpath + `Color` stub) |
| BTG model (Java) | `com.osm2xp.core.parsers.btg` (`Btg.read/write`, `BtgTile`, `BtgFace`) |
| BTG model (Python) | `example/Blender-Flightgear-BTG-Import-Export-main/src/fg_btg_btgio.py` (`parse_btg`) |
| Airfield geometry | `AirfieldGeometryBuilder` (runways/taxiways/aprons/helipads/grass, local east/north metres) |
| Wiring | `FlightGearAirfieldTranslationAdapter.complete()` → `FlightGearAirportBtgWriter.write()` (gated by `generateAirfieldsBtg`) |

## 3. New option — DECIDED

- **`generateAirfieldsBtgCut`** — boolean, default `false`.
- Add to `FlightGearOptions` (`@XmlType propOrder`, field, getters/setters), `testdata/flightgear/FlightGearOptions.xml`, `FlightGearOptionsProvider`.
- `FlightGearAirfieldTranslationAdapter.complete()`: when on → cut-and-fill writer; otherwise the existing `FlightGearAirportBtgWriter`.

## 4. Steps

### Step 0 — Terrain download (both modes)
- Standalone: keep `tools/DownloadFgTerrain.java`.
- Integrated: new `FlightGearTerrainDownloader` (`.dirindex` logic, `HttpClient`, concurrency, size-verify + backoff retry); region = OSM bbox from `processBoundingBox → visit(Box2D)`; downloads missing tiles into `flightGearSceneryPath`.
- A **rectangle (bbox)** suffices — no point grid needed.

### Step 1 — Convex polygon, split per tile
- 2D convex hull of the airfield footprint via JTS `org.locationtech.jts.algorithm.ConvexHull`.
- Split per sub-bucket with the existing tile-rect `intersection` logic. Convex ∩ rectangle = convex.

### Step 2 — Buffer — DECIDED
- Buffer = `max(100m, 0.10 * diameter)`; JTS `buffer()`; the buffered convex polygon is the **cut/hole** shape.

### Step 3 — Probe heights, select max
- Probe inside/on the buffered polygon across all involved tiles (`FlightGearElevationIndex`/`FlightGearTerrainElevationProber`); take the **global max** → flat airfield elevation (5.0).

### Step 4 — Cut hole with JCSG `difference` (per tile)
1. `BtgTile` → jcsg `Polygon`s (ECEF `center + vertex`, normals); tag `source = "terrain"`.
2. Localize to a bounding box around the airfield part.
3. Close into a solid: side walls + bottom (tag `source = "helper"`).
4. Closed prism CSG = `Extrude` of buffered convex part from `elev − margin` to `elev + margin`.
5. `terrainSolid.difference(prism)`.
6. Strip: keep only `source = "terrain"` with up-facing normal (`dot(normal, up) > 0.5`).
7. Convert survivors → `BtgFace` (recompute normals), splice into the tile.

### Step 5 — Airfield geometry
- 5.0: elevation = global max (same for every tile).
- 5.a: triangulate the **original** convex polygon as a flat top at max elevation; overlay runway/taxiway/apron/grass surfaces, fill gaps (`PolygonTriangulator`).
- 5.b: triangulate the **ring** between original convex edge and buffered-hole rim (skirt) as a polygon-with-hole (`PolygonTriangulator`).

## 5. Libraries

| Task | Library |
|---|---|
| Hole filling / triangulation (5.a, 5.b) | JTS `ConformingDelaunayTriangulationBuilder` (`PolygonTriangulator`) |
| Convex hull (Step 1) | JTS `ConvexHull` |
| Buffer (Step 2) | JTS `buffer()` |
| Boolean difference (Step 4) | JCSG (`CSG.fromPolygons`, `difference`, `Extrude`) |
| Terrain download (Step 0) | `java.net.http.HttpClient` |

## 6. Problematic / heavy operations

1. **JCSG on open terrain** — heightfield is an open surface; JCSG needs closed solids → close the localized region, strip helper/cavity after the boolean.
2. **Performance** — tens of thousands of triangles/tile → localize before the boolean.
3. **Numerical robustness** — BSP on large near-coplanar/non-manifold terrain → cracks/T-junctions/degenerate polys; mitigate with localization + tagging + normal filter.
4. **Classification heuristic** — `source` tag + up-facing normal; steep/vertical terrain may need threshold tuning.

## 7. Files

New:
- `FlightGearTerrainDownloader`
- `FlightGearAirportCutWriter` (steps 1–5)
- `BtgCsgConverter` (BtgTile ↔ jcsg Polygon, tagging, normal filter)

Changed:
- `FlightGearOptions` / `FlightGearOptions.xml` / `FlightGearOptionsProvider`
- `FlightGearAirfieldTranslationAdapter`

## 8. Testing

### 8.1 Unit — geometry
- Convex hull + buffer + ring triangulation on a synthetic footprint.
- `BtgCsgConverter` round-trip on a small synthetic `BtgTile` (square heightfield): cut a prism, assert the top has a hole and helper/cavity geometry is stripped.

### 8.2 Integration — elevation probe (gated)
- Probe max height on a known airfield (skip via `assumeTrue` when terrain absent).

### 8.3 End-to-end integration — actual scenario generation
New `AirfieldBtgCutIntegrationTest` in `com.osm2xp.console`, modeled on `TransportationIntegrationTest`:
1. Locate `testdata` root (same `isTestRootFolder` walk-up).
2. Use the same PBF (`H:\tmp\planet_77.418,51.277_88.406,56.055.osm.pbf`), or a smaller region with known airfields; set `generateAirfieldsBtgCut(true)` and `flightGearSceneryPath` to a TerraSync root that already has terrain tiles (downloaded via Step 0).
3. Run `App.main(...)`; assert target dir created.
4. Assert `.btg.gz` files produced under `Terrain/`.
5. **Readability — Java**: for each produced BTG, `com.osm2xp.core.parsers.btg.Btg.read(File)` returns a non-null `BtgTile` with `getVertexCount() > 0` and `getFaceCount() > 0`, no exception.
6. **Readability — Python**: run a subprocess (`python3`/`python`) that imports `example/Blender-Flightgear-BTG-Import-Export-main/src/fg_btg_btgio.py` and calls `parse_btg(filepath)` on each produced BTG; assert exit code 0 and non-empty `BTGData`. Skip via `assumeTrue` if no Python interpreter is available.
7. Optional correctness assert: the produced tile contains airfield-plate faces at/above the flat elevation (e.g. some vertices near the max probed height).

Notes:
- The Java parser is `Btg.read`; the Python parser is `fg_btg_btgio.parse_btg` (Blender addon BTG I/O module). Both must accept the generated files, proving the cut writer emits well-formed BTG.
- Marked `@Test` but tolerate environment differences (terrain path, Python presence) via `assumeTrue`, matching the `TransportationIntegrationTest` style.

## 9. Decisions (all resolved)
- Option name: `generateAirfieldsBtgCut` (boolean, default `false`).
- Buffer formula: `max(100m, 0.10 * diameter)`.
- Normal threshold: `0.5` (default).
