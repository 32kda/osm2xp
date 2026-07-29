# OSM2XP Code Analysis & Improvement Plan

## Project Overview

**OSM2XP** generates X-Plane flight simulator scenery from OpenStreetMap data. Java 8 / Eclipse RCP application (~40K LOC, 431 source files) with Maven/Tycho build, GUI and console modes.

## Key Architecture

```
OSM File (PBF/OSM/Shapefile)
  → Parser (SaxParserImpl / PbfParserImpl)
    → DataSink (H2DB / MapDB)
      → Translator (XPlaneTranslatorImpl + sub-translators)
        → Writers (DSF, apt.dat, .obj, .fac, .for, .pol files)
```

## Files Analyzed

- `XPlaneTranslatorImpl.java` — 670 lines, 26 methods, "God class"
- `OsmPolyline.java` / `OsmPolygon.java` — OSM data model
- `OsmUtils.java` — Static utility methods
- `XPPathTranslator.java` / `XPRailTranslator.java` / `XPPowerlineTranslator.java` / `XPRoadTranslator.java` — Path translation hierarchy
- `XPBarrierTranslator.java` — Barrier handler
- `TranslatorBuilder.java` — Static factory with global mutable registry
- `BuildingType.java` — Building classification enum
- `OsmPolylineFactory.java` — Test-data-friendly factory
- `OsmConstants.java` — Tag name constants
- `ConsoleAppIntegrationTest.java` — The only real test

---

## Issues Found

### 🔴 Critical

#### 1. Cyrillic Bug — `XPlaneTranslatorImpl.java:325`
```java
|| OsmUtils.isValueInTags("сommercial", polygon.getTags())
//                          ^ Cyrillic 'с' (U+0441) instead of Latin 'c' (U+0063)
```
**Impact:** The `commercial` value check never matches real OSM data. Buildings with `building=commercial` that should be classified as `BuildingType.INDUSTRIAL` fall through to the default `RESIDENTIAL`.

#### 2. Tag Lookup is O(n) per call — `OsmPolyline.java:73-76`
```java
public String getTagValue(String tagKey) {
    Optional<Tag> first = tags.stream()
        .filter(tag -> tagKey.equals(tag.getKey())).findFirst();
    return first.isPresent() ? first.get().getValue() : null;
}
```
Called 5-15× per building, each streaming all tags. Performance degrades linearly with tag count.

#### 3. Redundant `getBuildingType()` Calls
Called from `tryGetHeightByType()` (line 260) AND `computeFacadeIndex()` (line 355) on the same polygon. No caching between calls.

#### 4. `printStackTrace()` Scattered
Several places use `e.printStackTrace()` instead of `Osm2xpLogger.log()`.

### 🟡 Significant

#### 5. Static Global State (SOLID Violation)
- `GlobalOptionsProvider` — 28+ files, 80+ static call sites
- `XPlaneOptionsProvider` — 19+ files, 60+ static call sites
- `StatsProvider` — Global mutable map
- `PathsService` — Mutable at class-load time

All prevent unit testing without PowerMock.

#### 6. XPlaneTranslatorImpl — God Class (SRP Violation)
670 lines, 26 methods. Handles:
- Building type classification
- Height computation
- Facade index computation
- DSF writing
- Node processing
- Polyline preprocessing
- Forest handling
- Area queries
- Statistics tracking

#### 7. DRY Violation — Path Translators
`XPRailTranslator` (41 lines) and `XPPowerlineTranslator` (46 lines) are ~80% identical:
- Same constructor pattern `super(writer, outputFormat, idProvider)`
- Same `handlePoly()` guard + tag check + `addSegmentsFrom()` + return
- Same 3 override methods structure (`getPathType`, `getBridgeRampLength`, `getId`)

Only tag name (`railway` vs `power`) and option method differ.

#### 8. Dead Code
- Commented-out FSX, FlightGear, FlyLegacy factories in `TranslatorBuilder`
- Commented-out code blocks throughout `XPlaneTranslatorImpl`
- Empty `test/` module with stub `SampleClass`
- `osm2xp.helpers/` module with only stub test

### 🟢 Quality

#### 9. Test Coverage ~0%
Only 1 real integration test (`ConsoleAppIntegrationTest`). No unit tests.

#### 10. CI Doesn't Run Tests
`.travis.yml` runs `mvn clean compile install`. JaCoCo bound to `verify` phase, never executed.

#### 11. No Dependency Injection
All dependencies resolved statically or created with `new` in constructors.

#### 12. Hardcoded Magic Numbers
`MIN_BARRIER_PERIMETER = 200.0`, `maxRadius = 2000`, `1.1` coefficient, `levelHeight = 3`.

---

## Improvement Plan

### Phase 1: High-Impact, Low-Risk

| # | Task | Files | Effort |
|---|------|-------|--------|
| 1.1 | Fix Cyrillic bug | `XPlaneTranslatorImpl.java:325` | 5 min |
| 1.2 | Add O(1) tag Map cache | `OsmPolyline.java` | 2 hr |
| 1.3 | Remove dead code | Multiple files | 1 hr |
| 1.4 | `printStackTrace()` → Logger | Multiple files | 1 hr |

### Phase 2: Structural Refactoring (SOLID/DRY)

| # | Task | Files | Effort |
|---|------|-------|--------|
| 2.1 | Extract `BuildingClassifier` | New class + `XPlaneTranslatorImpl` | 4 hr |
| 2.2 | Strategy pattern for path translators | `XPPathTranslator` + subclasses | 3 hr |
| 2.3 | DI for options | `XPlaneTranslatorImpl` + callers | 8 hr |

### Phase 3: Testing

| # | Task | Effort |
|---|------|--------|
| 3.1 | Add Mockito + JUnit deps | 30 min |
| 3.2 | Create test data builders & in-memory writer | 2 hr |
| 3.3 | Unit tests for `BuildingClassifier` | 3 hr |

---

## Performance Notes

- All tag matching uses **exact string comparison** (`.equals()`). No regex in matching path.
- `String.contains()` used in `OsmUtils.isStringInTags()` for key prefix matching (e.g., `"height"` in `"building:height"`).
- **No regex rule engine** is used or recommended — keeping exact/precise matching for performance.
- The single biggest performance win is **O(1) tag lookup cache** (Phase 1.2).
- Second biggest: **getBuildingType() result cache** to avoid redundant classification.