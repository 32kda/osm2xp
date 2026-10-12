# FlightGear Airfield Terrain Cut (BTG) — Implementation Report

Implementation of the spec `spec/airfield-btg-cut.md`: bake an airfield into the
FlightGear terrain by **cutting a hole** in the terrain BTG mesh and filling it
with a flat airfield plate plus a sloped transition skirt.

This document is the engineering log: what was built, every problem hit along
the way, how it was diagnosed and fixed, the tooling used, and the current
status.

---

## 1. Goal (from the spec)

Instead of the existing "merge geometry on top" approach
(`FlightGearAirportBtgWriter`), the new mode:

1. Computes the convex hull of the airfield footprint.
2. Buffers it by `max(100 m, 0.10 × diameter)` — the **cut/hole** shape.
3. Probes the terrain inside/on the buffered polygon and takes the **global max**
   elevation.
4. Cuts that hole out of each involved terrain tile.
5. Fills it with a **flat plate** (runways/taxiways/aprons/grass at the flat
   elevation) and a **sloped skirt** down to the surrounding terrain at the rim.

Gated behind a new boolean option `generateAirfieldsBtgCut` (default `false`).

---

## 2. Deliverables

### 2.1 New files

| File | Purpose |
|---|---|
| `com.osm2xp.generation/.../translators/airfield/btg/BtgCsgConverter.java` | BTG tile ↔ local flat-earth frame; terrain **2D clip**; seam (hole-boundary) extraction; shared-pool tile rebuild. |
| `com.osm2xp.generation/.../translators/airfield/btg/FlightGearAirportCutWriter.java` | Orchestrates steps 1–5 (hull, buffer, elevation probe, per-tile cut, plate + skirt, stitch, write). |
| `com.osm2xp.generation/.../translators/flightgear/FlightGearTerrainDownloader.java` | Integrated Step 0 terrain download (`.dirindex`, `HttpClient`, concurrency, size-verify + backoff). |
| `com.osm2xp.generation/src/test/java/.../btg/BtgCsgConverterTest.java` | Unit tests (clip leaves a hole, ring triangulation, BTG round-trip). |
| `com.osm2xp.console/src/test/java/.../AirfieldBtgCutIntegrationTest.java` | Gated end-to-end test (App.main, BTG readability via Java **and** Python parsers). |

### 2.2 Changed files

| File | Change |
|---|---|
| `FlightGearOptions.java` | Added `generateAirfieldsBtgCut` to `propOrder`, field (default `false`), getter/setter. |
| `testdata/flightgear/FlightGearOptions.xml` | Added `<generateAirfieldsBtgCut>false</generateAirfieldsBtgCut>`. |
| `FlightGearAirfieldTranslationAdapter.java` | Captures the bbox (`processBoundingBox`), runs the terrain downloader, routes to `FlightGearAirportCutWriter` when the option is on, else the existing writer. `catch (Throwable)` around download + each cut. |
| `PolygonTriangulator.java` | Added `triangulateSimple()` — Steiner-point-free ear clipping with hole **bridging** (no triangle bloat, no seam T-junctions from Steiner points). |
| `AbstractTileDataConverter.java` | **Systemic fix**: per-adapter `catch (Throwable)` + `finally { translatorProvider.close(); }` so buffered STG writers are always flushed. |
| `FlightGearTranslatorProvider.java` | JVM shutdown hook that flushes the bucket-output registry on abrupt termination. |

> Note: `Btg.java`, `FlightGearBuildingAnalyzer.java`, `FlightGearTranslatorImpl.java`,
> `XPStringLightTranslator.java`, `PolygonUtil.java` also show as modified in the
> working tree, but those are **pre-existing changes from other sessions**, not
> part of this feature.

---

## 3. Pipeline (as implemented)

`FlightGearAirportCutWriter.write(airfield, sceneryRoot)`:

1. **Setup** — resolve the terrain root from `flightGearSceneryPath`, create a
   `FlightGearTerrainElevationProber`.
2. **Airfield surfaces** — `AirfieldGeometryBuilder.build(airfield)` →
   pavement + grass skirt in the local east/north metre frame.
3. **Convex hull** (`ConvexHull`) of the footprint.
4. **Buffer** — `buffer = max(100, 0.10 × diameter)`, `hull.buffer(buffer)` →
   `cutPolygon` (the hole).
5. **Elevation probe** — sample the buffered polygon boundary + an 8×8 interior
   grid across all involved tiles (`FlightGearTerrainElevationProber`), take
   `elevation = max`, `minElevation = min`.
6. **Pass 1 — cut** every tile overlapping `cutPolygon`:
   - `BtgCsgConverter.partition(tile, region)` splits faces by 2D envelope.
   - `clipTerrain(...)` computes `triangle.difference(cutPolygon)` in 2D and lifts
     the surviving pieces back to their interpolated terrain heights; also
     returns the **seam vertices** (clipped points lying on the cut boundary).
   - Collect all seam vertices across tiles.
7. **Densify** `cutPolygon` by inserting the seam vertices into its edges, so the
   skirt rim and the clipped terrain share the exact same vertices.
8. **Pass 2 — fill** per tile:
   - Plate = airfield surfaces + `hull − surfaces` gap-fill grass, flat at
     `elevation`.
   - Skirt = `(stitchedCut − hull)` triangulated and lifted (slope from plate
     edge at `elevation` down to probed terrain height at the rim).
   - `BtgCsgConverter.rebuild(original, outsideFaces, cutTriangles, overlayTriangles)`
     assembles **cut terrain + overlay in one shared, deduplicated vertex pool**
     (this is what welds the seam).
9. **Write** `Terrain/<band>/<cell>/<index>.btg.gz` + `<index>.stg`
   (`OBJECT_BASE <index>.btg`).

### Coordinate frames

* **Local frame**: `x = east` m, `y = north` m, `z = altitude` m (WGS84
  ellipsoid), anchored at the airfield datum. `+Z` is "up".
* BTG vertices are ECEF offsets from the tile centre; converted via
  `FlightGearCoordinateUtils` (`ecefToGeodetic` → `toLocal`, and back).

---

## 4. Problems encountered and fixes (chronological)

### 4.1 JCSG `StackOverflowError` (the big one)

**Spec said** use a JCSG 3D boolean `difference` on a closed terrain solid.

**Symptom**: generation aborted with `java.lang.StackOverflowError` inside
`eu.mihosoft.jcsg.Node.build` (recursive BSP construction).

**Diagnosis**: reproduced with a *synthetic wavy heightfield*. JCSG's
`Node.build` uses the first polygon as the split plane (no heuristic); on a
real (non-flat) heightfield it fragments pathologically. It overflowed with
**~98 triangles**, and even a **256 MB stack** still overflowed. Flat terrain
did *not* overflow (all coplanar → shallow tree), which is why a flat stress
test initially misled us.

**Fix**: abandon JCSG for the terrain cut. Because the cut is a **vertical
prism**, the hole is just the 2D difference of the terrain triangles against
the footprint — no 3D boolean needed. `BtgCsgConverter.clipTerrain` now does:

```java
Geometry outside = triangle.difference(cutPolygon);          // 2D
for (Polygon piece : GeomUtils.flatMapToPoly(outside))
    for (double[] t : triangulator.triangulateSimple(piece))
        result.add(new Triangle(..., interpolateAltitude(face, e, n), ...));
```

`interpolateAltitude` is barycentric over the source triangle, so the clipped
mesh **preserves the original terrain relief** (only the footprint is removed).

> **Deviation from the spec**: Step 4 no longer uses JCSG. The spec's goal
> (per-tile hole + flat plate + skirt) is unchanged; the mechanism is a robust
> 2D JTS clip. JCSG was unusable on heightfields.

### 4.2 Truncated `Objects/*.stg` → "Unknown STG token" (scenery failed to load)

**Symptom**: FlightGear logged
`error-btg-load::Unknown STG token:OBJECT_` and tiles failed to load. The
`Objects/<tile>.stg` files were exact multiples of 8192 bytes and ended
mid-token (e.g. `... 0 0\nOBJECT_`).

**Root cause**: `FlightGearBucketOutput` uses an 8 KB-buffered
`OutputStreamWriter`. The only flush point is
`FlightGearTranslatorProvider.close()` → `bucketOutputRegistry.closeAll()`,
called **last** in `AbstractTileDataConverter.complete()`. The heavy cut phase
runs inside an adapter's `complete()`; when it aborted, `provider.close()` was
never reached, leaving every STG writer unflushed.

**Fixes**:
* `AbstractTileDataConverter.complete()` now wraps each adapter in
  `catch (Throwable)` and calls `translatorProvider.close()` in a `finally`.
* `FlightGearAirfieldTranslationAdapter` catches `Throwable` around the
  download and each airfield cut.
* `FlightGearTranslatorProvider` registers a JVM shutdown hook that flushes the
  registry on abrupt termination.

After the fix: **0 truncated STGs** (1517/1517 non-empty, none a multiple of 8192).

### 4.3 Triangle-count explosion from the conforming-Delaunay triangulator

**Symptom**: after the 2D clip, each 3-vertex clipped piece produced ~30–55
triangles. `PolygonTriangulator` uses `ConformingDelaunayTriangulationBuilder`,
which inserts Steiner points; at metre scale it even splits a plain triangle
into 2 and a square frame into 56.

**Fix**: added `PolygonTriangulator.triangulateSimple()` — ear clipping with no
Steiner points (holes handled by bridging). Result on the same input:
338 region faces → **346 triangles** (was 10057).

### 4.4 T-junctions / "short circuits" at the cut seam

**Symptom**: `btgchk -c` reported extra open edges and
`find_fence: too many short circuits, giving up!` on ~20 tiles.

**Root cause**: the clipped terrain's hole boundary has extra vertices where
terrain triangle edges cross the cut boundary, while the skirt's outer rim only
had the cut polygon's vertices. They were geometrically coincident but did not
share vertices.

**Fix (stitching)**:
* `clipTerrain` returns the seam vertices (`ClipResult.crossings`).
* The writer densifies `cutPolygon` with those vertices before building the
  skirt.
* `rebuild(...)` assembles cut terrain + overlay in a **single shared,
  position-deduplicated vertex pool**, so the coincident seam vertices merge.

Result: tiles with a short circuit went **~20 → 2**; open edges roughly halved
(4318272: 561 → 292, original 272).

### 4.5 Guard rails added along the way

* `MAX_CUT_FACES = 60000` — skip the cut if a region is pathologically large.
* `REGION_MARGIN_M = 200` — localize the cut region.
* Degenerate-triangle skipping in both `BtgCsgConverter.rebuild` and the
  overlay assembly (reduced invalid triangles on 4318272 from 88 → 9).
* `catch (Throwable)` around the cut so a single bad airfield never aborts the
  run.

---

## 5. Testing

### 5.1 Unit — `BtgCsgConverterTest` (JUnit 4, generation module)

* `clipLeavesHole()` — synthetic wavy square heightfield; clips a footprint;
  asserts no surviving triangle centroid is inside the footprint, and that the
  rebuilt tile round-trips through `Btg.write`/`Btg.read` (vertex/face counts
  preserved).
* `convexHullBufferRingTriangulates()` — hull + buffer + ring triangulation.

Both pass.

### 5.2 Integration — `AirfieldBtgCutIntegrationTest` (console module)

Modeled on `TransportationIntegrationTest`; gated with `assumeTrue` for PBF,
TerraSync, and Python. Runs `App.main(...)` with
`generateAirfieldsBtgCut(true)`, then:

* asserts the target dir exists and `.btg.gz` files were produced;
* Java readability — `Btg.read` returns a `BtgTile` with `vertexCount > 0`,
  `faceCount > 0`;
* Python readability — a subprocess imports
  `example/Blender-Flightgear-BTG-Import-Export-main/src/fg_btg_btgio.py` and
  calls `parse_btg` on each produced BTG (gzip decompressed first), asserting
  exit 0 and non-empty `BTGData`.

### 5.3 How the scenery was regenerated (used repeatedly during debugging)

The console module has a shaded jar; a tiny launcher prepends the freshly
compiled generation classes and calls `App.main`:

```java
public class RunGen {
  public static void main(String[] a) throws Exception {
    FlightGearOptionsProvider.getOptions().setGenerateAirfieldsBtg(true);
    FlightGearOptionsProvider.getOptions().setGenerateAirfieldsBtgCut(true);
    FlightGearOptionsProvider.getOptions().setFlightGearSceneryPath("D:\\Games\\FlightGear 2024.1\\TerraSync");
    com.osm2xp.console.App.main(a);
  }
}
// java -cp "<genout>;<shaded-jar>" RunGen <pbf> -m FLIGHT_GEAR -c testdata -s testscenery_btgcut
```

Two full regenerations were done (pre-stitch ~3m25s, post-stitch ~4m31s).

---

## 6. `btgchk` results

TerraGear's `btgchk -c` (diagnostic only) was run over every generated tile and
compared against the stock TerraSync tile.

`D:\Games\FlightGear 2024.1\WestSiberia\Terrain\` (32 tiles, current build) —
**all exit 0, no crashes**:

| Tile | Invalid tri | Dup | Obsc | Short-circuit | Edges 1 / 2 |
|---|---|---|---|---|---|
| 4236218 | 2 | 1 | 0 | 0 | 54 / 7230 |
| 4236226 | 2 | 0 | 0 | 0 | 52 / 6436 |
| 4236369 | 16 | 0 | 31 | 0 | 255 / 25368 |
| 4301883 | 46 | 2 | 57 | 0 | 316 / 70816 |
| 4301891 | 53 | 939 | 168 | **1** | 1069 / 97012 |
| 4318162 | 12 | 7 | 19 | **1** | 776 / 32717 |
| 4318272 | 6 | 90 | 7 | 0 | 292 / 27319 |
| 4350819 | 3 | 0 | 0 | **1** | 758 / 34943 |
| 4383720 | 18 | 16 | 29 | **1** | 393 / 38643 |

Comparison (same tile, stock vs cut):

| Tile | | Invalid tri | Dup | Obsc | Edges 1 | Short-circuit |
|---|---|---|---|---|---|---|
| 4318272 | stock | ~12,000 | 101 | 8 | 272 | 0 |
| | cut | 6 | 90 | 7 | 292 | 0 |
| 4301891 | stock | ~53,600 | 940 | 176 | 623 | 0 |
| | cut | 53 | 939 | 168 | 1069 | 1 |

Takeaways:

* The cut tiles have **far fewer** invalid triangles than the stock terrain
  (which is itself full of them and loads fine).
* Duplicate/obscure counts are essentially inherited from the base terrain.
* A few large-footprint / multi-tile airfields still show elevated open edges
  and 1 "short circuit" warning; the seam is now the same order of magnitude as
  the natural terrain holes.

> `btgchk` itself crashed on some tiles during early runs (WER dumps in
> `%LOCALAPPDATA%\CrashDumps\btgchk.exe.*.dmp`); a sibling session patched
> `btgchk` and it now runs all tiles cleanly.

---

## 7. FlightGear crash investigation

After the STG truncation was fixed, FlightGear still crashed on the generated
`WestSiberia` scenery (~16 s in, during terrain material processing).

### 7.1 How the crash data was captured

* **WER local dumps** for `fgfs.exe` were configured
  (`HKLM\SOFTWARE\Microsoft\Windows\Windows Error Reporting\LocalDumps\fgfs.exe`,
  `DumpFolder = D:\Work\git\osm2xp\dumps`, `DumpType = 2`) — **but no dump
  appeared there**.
* The reason: FlightGear's built-in **Sentry/Crashpad** handler caught the
  fault first. The dump is under the user profile:

  ```
  C:\Users\root\AppData\Roaming\flightgear.org\sentry_db\reports\
      f1398250-3b22-4b32-faf9-0b02eef5defa.dmp   (2,020,128 bytes)
  ```

### 7.2 Minidump analysis

Parsed the minidump by hand (header → stream directory):

```
ExceptionCode  = 0xC0000005   (ACCESS_VIOLATION)
ExceptionAddress = 0x0000000000000000   (null pointer / null call)
faulting thread stack: deep recursion in fgfs.exe+0xC66E3A (~40+ repeats),
then fgfs.exe+0xC6B8F8 / +0xC6A705 / +0xC66DE3, unwinding through ntdll
```

* The fault is in **`fgfs.exe` (FlightGear itself)** — not the NVIDIA driver.
* The log's last activity was `SGMaterialLib` / `mat.cxx`
  `Checking materials for location (83.375,54.9375)` → the crash is in the
  **terrain/material** path.
* No PDB ships with the release, so `fgfs.exe+0xC66E3A` cannot be symbolized
  locally.
* Materials in a cut tile are all valid FG names: `Airport, DryCrop, Freeway,
  Grass, Grassland, MixedForest, Railroad, Road, Stream, Urban` plus
  `pa_taxiway, pa_tiedown, pc_rest`. So it is **not** an unknown material.

### 7.3 FlightGear logging flags (confirmed present in this build's `options.xml`)

| Flag | Effect |
|---|---|
| `--console` (`-c`) | show the Windows console |
| `--developer` | extra logging |
| `--log-level=bulk --log-class=all` | maximum verbosity |
| `--fpe` | abort on floating-point exceptions (pinpoints FP issues) |
| `--sentry` | FG crash/error reporting (sends to the FG devs) |

Working capture bat (note: **no duplicate `--log-class`, no `--fpe`**):

```bat
@echo off
cd /d "d:\Games\FlightGear 2024.1\bin"
fgfs ^
  --fg-scenery="../WestSiberia" ^
  --airport=XNNB ^
  --prop:/sim/presets/airport-id=XNNB ^
  --prop:/sim/startup/language=english ^
  --log-level=bulk ^
  --log-class=all ^
  --developer ^
  --console ^
  --log-dir="D:\Work\git\osm2xp"
```

Pitfalls found:

* `--fpe` makes FlightGear abort at ~0.16 s on benign FP exceptions during
  graphics/Qt init — it was the cause of three tiny 12 KB logs. Use it only for
  a targeted run.
* The duplicate `--log-class` is ignored with a warning; keep only
  `--log-class=all`.
* The `qt.qpa.window: SetProcessDpiAwarenessContext() failed: Access denied`
  message is a **benign** Qt warning (DPI awareness already set by the app
  manifest) and is unrelated to the crash.

### 7.4 Status of the crash

Still **open**. Evidence so far:

* It is a FlightGear-side null dereference with deep recursion in the
  terrain/material code.
* `btgchk` reports the tiles as structurally comparable to (mostly cleaner than)
  the stock terrain, so the mesh is not obviously malformed.
* It appeared only after the cut mode was introduced; earlier merge-on-top runs
  did not crash.

Recommended next steps:

1. A/B test: regenerate `WestSiberia` with the **cut disabled** (merge mode) and
   confirm whether the cut tile content is the trigger.
2. Try a different start airport on a cut tile, and/or disable the AI carrier
   (`--ai-traffic=no`) to rule out unrelated model loading.
3. Report the `.dmp` on the FlightGear tracker (gitlab.com/flightgear) — the
   only way to symbolize `fgfs.exe+0xC66E3A`, since no PDB is available locally.

---

## 8. Known limitations / open items

* **Deviation from spec**: Step 4 uses a JTS 2D clip, not JCSG (JCSG is unstable
  on heightfields). The prism is vertical so the result is equivalent.
* **Multi-tile / large-footprint airfields** can still leave a "short circuit"
  warning and elevated open edges in `btgchk` (5 of 32 tiles currently).
* **`generateAirfieldsBtgCut`** defaults to `false`; the shipped default config
  under `osm2xp_additions/flightgear/FlightGearOptions.xml` was left untouched
  (it predates even `generateAirfieldsBtg`).
* **FlightGear crash** (section 7) is unresolved and appears to be a FG-side
  issue triggered by the modified terrain.
* The terrain downloader downloads the **whole OSM bbox** per the spec; for very
  large PBFs this is many cells/tiles (present tiles are skipped).

---

## 9. Build / run notes

* Generation module: Java 11, Eclipse-plugin packaging (Tycho). A full
  `mvn` build needs the Tycho target platform.
* During development, compilation was verified with `javac` against
  `target/classes` + the module `lib/`/`staticlib/` jars + core libs
  (`-encoding ISO-8859-1` to tolerate a legacy-encoded source file).
* `btgchk` invocation:
  `btgchk -p <scenery-root> -i <tileIndex> -c`
  (the `-p` path is the scenery root that contains `Terrain/`).
* Generated tile layout: `<sceneryRoot>/Terrain/<band>/<cell>/<index>.btg.gz`
  and `<index>.stg` (`OBJECT_BASE <index>.btg`).

---

## 10. File/commit summary

New:
```
com.osm2xp.generation/.../airfield/btg/BtgCsgConverter.java
com.osm2xp.generation/.../airfield/btg/FlightGearAirportCutWriter.java
com.osm2xp.generation/.../flightgear/FlightGearTerrainDownloader.java
com.osm2xp.generation/src/test/java/.../btg/BtgCsgConverterTest.java
com.osm2xp.console/src/test/java/.../AirfieldBtgCutIntegrationTest.java
```

Changed (this feature):
```
com.osm2xp.generation/.../generation/options/FlightGearOptions.java
com.osm2xp.generation/.../translators/airfield/FlightGearAirfieldTranslationAdapter.java
com.osm2xp.generation/.../translators/airfield/btg/PolygonTriangulator.java
com.osm2xp.generation/.../converters/impl/AbstractTileDataConverter.java
com.osm2xp.generation/.../translators/FlightGearTranslatorProvider.java
testdata/flightgear/FlightGearOptions.xml
```

Pre-existing changes from other sessions (not this feature): `Btg.java`,
`FlightGearBuildingAnalyzer.java`, `FlightGearTranslatorImpl.java`,
`XPStringLightTranslator.java`, `PolygonUtil.java`, `testdata/.settings/com.osm2xp.prefs`.
