# apt.dat Feature Availability: X-Plane vs. FlightGear

This document breaks down the features defined in the X-Plane **apt.dat 1200** file format specification and assesses their availability in both **X-Plane 12** and **FlightGear**.

## Key Version Difference

- **X-Plane 12** uses the **1200** specification, which is the most current and feature-rich.
- **FlightGear** is built around much older specifications (effectively **version 1100** and earlier). It is not fully compatible with the 1200 format and its tools (e.g., `terragear`) will often fail or ignore new features.

---

## Feature Breakdown

### 1. Airport & Heliport Headers
*Row Codes: 1, 16, 17*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Land Airport Header** | ✅ Full Support | ✅ Supported | Core structure is shared. |
| **Seaplane Base Header** | ✅ Full Support | ✅ Supported | Core structure is shared. |
| **Heliport Header** | ✅ Full Support | ✅ Supported | Core structure is shared. |

---

### 2. Runways, Water Runways & Helipads
*Row Codes: 100, 101, 102*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Basic Runway Definition** | ✅ Full Support | ✅ Supported | Location, designator, width, basic surface, and markings. |
| **New Surface Types** (e.g., `20-23`, `27-30`, `35-38`) | ✅ Full Support | ❌ Unsupported | FlightGear uses older surface codes. Code `33`, for example, can cause parser errors. |
| **Runway Shoulder Definition** (e.g., `206`) | ✅ Full Support | ❌ Unsupported | Feature introduced in the 1200 spec. |
| **Expanded Marking/Lighting Options** | ✅ Full Support | ❌ Unsupported | New codes for markings and approach lighting. |
| **Water Runways** | ✅ Full Support | ✅ Supported | Basic functionality is present. |
| **Helipads** | ✅ Full Support | ✅ Supported | Basic functionality is present. |

---

### 3. Pavement (Taxiways & Ramps)
*Row Code: 110*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Polygon Pavement Definitions** | ✅ Full Support | ✅ Supported | Closed-loop definitions with nodes. |
| **Pavement Holes** | ✅ Full Support | ✅ Supported | Capability exists. |
| **New Surface Types** | ✅ Full Support | ❌ Unsupported | Same issue as with runways. |

---

### 4. Linear Features & Airport Boundaries
*Row Codes: 120, 130*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Line Definitions (Strings/Loops)** | ✅ Full Support | ✅ Supported | Basic node/line structure. |
| **Bezier Control Points** | ✅ Full Support | ✅ Supported | Supported in older specs. |
| **Line & Light Styles** | ✅ Full Support | ✅ Supported | The basic code list (e.g., `1-9`, `51-59`) is likely supported. |

---

### 5. Airport Furniture (Objects)
*Row Codes: 14, 15, 18, 19, 20, 21*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Viewpoint** (14) | ✅ Full Support | ✅ Supported | Core feature. |
| **Startup Location** (15) | ✅ Full Support (deprecated for 1300) | ✅ Supported | Core feature. |
| **Light Beacon** (18) | ✅ Full Support | ✅ Supported | Core feature. |
| **Windsock** (19) | ✅ Full Support | ✅ Supported | Core feature. |
| **Taxiway Sign** (20) | ✅ Full Support | ✅ Supported | Sign text formatting is shared. |
| **Lighting Objects (VASI/PAPI)** (21) | ✅ Full Support | ✅ Supported | Core feature. |

---

### 6. ATC Communications Frequencies
*Row Codes: 50-56 (Legacy), 1050-1056 (New)*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Legacy 25kHz Frequencies** (50-56) | ✅ Full Support | ✅ Supported | The older format is the one FlightGear understands. |
| **8.33kHz Frequencies** (1050-1056) | ✅ Full Support | ❌ Unsupported | Newer row codes introduced after the 1100 spec. |

---

### 7. Advanced Features (Introduced in 1200)
*Row Codes: 1000, 1001, 1002, 1003, 1004, 1100, 1101, 1110, 1200, 1300, 1301, 1302, 1400, 1401, 1402, 1500, 1501*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Airport Traffic Flows** (1000-1004, 1100, 1101, 1110) | ✅ Full Support | ❌ Unsupported | These are highly complex, rule-based ATC features exclusive to X-Plane. |
| **Taxi Route Networks** (1200-1206) | ✅ Full Support | ❌ Unsupported | Graph-based routing system for X-Plane's ATC. |
| **Ramp Start Metadata** (1300, 1301) | ✅ Full Support | ❌ Unsupported | New structure for gate assignments and metadata. |
| **Airport Metadata** (1302) | ✅ Full Support | ❌ Unsupported | Structured key-value pairs for ICAO/IATA codes, city, etc. |
| **Ground Truck Routes & Parking** (1400, 1401) | ✅ Full Support | ❌ Unsupported | New feature for animated ground vehicles. |
| **Custom Service Trucks** (1402) | ✅ Full Support | ❌ Unsupported | Overrides for default ground vehicle objects. |
| **Active Jetways** (1500) | ✅ Full Support | ❌ Unsupported | Animated jetways with positioning data. |
| **Custom Jetway Objects** (1501) | ✅ Full Support | ❌ Unsupported | Overrides for default jetway models. |

---

### 8. Comments & File Structure
*Row Code: 99*

| Feature | X-Plane 1200 | FlightGear (≤ 1100) | Notes |
| :--- | :--- | :--- | :--- |
| **Comments (`#`)** | ✅ Full Support | ✅ Supported | Standard practice. |
| **File Termination (`99`)** | ✅ Full Support | ✅ Supported | Required. |

---

## Summary of LLM-Processable Features

For generating `apt.dat` data using an LLM (like DeepSeek), the following features are most suitable as they are textual or rule-based:

- **Airport Metadata** (`1302`): City, country, IATA, ICAO, FAA codes.
- **Communication Frequencies** (`50-56`, `1050-1056`): ATIS, Tower, Ground, etc.
- **Traffic Flow Rules** (`1000-1101`): Wind, ceiling, time, and runway use rules.
- **Descriptive Names** (All `Text string` fields): Sign text, viewpoint names, startup names.
- **Taxiway Sign Text** (`20`): Correctly formatted strings using `{@L}`, `{@R}`, etc.

**GIS/Spatial Data is Not Suitable for LLMs**: Features requiring precise coordinates (lat/lon), complex polygons, or network topology (pavements, boundaries, taxi networks) cannot be reliably generated or validated by an LLM and require dedicated GIS tools or WED.

---

## File Layout in FlightGear

FlightGear does not read a single `apt.dat` per scenery package; it scans `<scenery_root>/NavData/apt/` **recursively** for `*.dat` / `*.dat.gz` files (`NavDataCache.cxx`). Per-airport files therefore work out of the box:

```
<scenery_root>/NavData/apt/<ICAO>/apt.dat
```

The sub-folder name is purely cosmetic — the airport identifier is read from the `1 <id> …` header row of each file, and the **first** definition of an identifier wins. Custom scenery roots are searched before TerraSync and `$FG_ROOT/Scenery`.

OSM2XP's **FlightGear** output mode writes exactly this layout: one `apt.dat` per airport under `NavData/apt/<ICAO>/` (real ICAO tag, or synthetic `xxNN`), using the compatible row subset described above. The **X-Plane** mode keeps the classic X-Plane layout (`Earth nav data/apt.dat`, or separate `osm2xp_<id>/` folders for multi-airport scenarios).

The scenery layer is written straight into FlightGear sub-bucket folders: `Objects/<band>/<bucket>/<index>.stg` plus a `BuildingList_<index>.txt.gz` next to it, so no manual bucket placement is needed (see the [installation manual](../docs/flightgear-scenery-installation.md)).