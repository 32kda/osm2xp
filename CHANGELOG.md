# Changelog
All notable changes to OSM 2 XP project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [4.6.2]

### Fixed
- Ability to generate a single tile only
- Airfields were generated even when Airfields generation was switched off
- Light strings now have slightly cut ends to avoid lammposts on roads near the road crossings
- Default road type for wide roads changed to 14 to avoid trees nearthe road
- Building type computation improved - fixed a typo for `residential` buildings and areas, other small fixes

## [4.6.1]

### Fixed
- Small fix for Lights UI and defaults

## [4.6.0]

### Added
- Street lights generation for some roads using Object Strings feature

### Fixed
- Serious brekdown which led to the multipolygon malfunction
- UI fixes

## [4.5.0]

### Added
- Ability to specify an object height for the object auto-selection in format like 10x12h6
- Better console logging, like total generation time
- Analysis of extra area tags, like `place`, `boundary` and `admin_level`

### Fixed
- Problem with the meridian 180

### Changed
- Generated airfields placed into the separate folder `scenery_name_airfields`

## [4.4.0]

### Added
- Roads, Railways and power lines type constants are now configurable from UI and xml settings. Changed default highway road types to ones having light posts

### Changed
- UI fix - items generation is switched on and off on corresponding tabs instead of "Advanced" tab

### Fixed
- Existing folder deletion is now done in the background, not freezing the UI
- Console mode is working again
- OSM mode is working partially - bounding boxes and relations aren't supported yet

### Removed
- FlightGear, FSX and Fly! modes are removed from the UI, since they are broken currently and wouldn't be revived in nearest future

## [4.3.0]

### Added
- Automatic exlusion management option - e.g. underlying scenery facades will be excluded if user choosed to generate facades

### Changed
- When generating multiple airfields - their folders named `osm2xp_[icao code]` will be placed in same folder with generated scenery and input osm/pbf file

## [4.2.1]

### Changed
- Almost all X-Plane exclusions ON by default
- Built-in forest files: density slightly increased

### Fixed
- Invalid tag numeric values parsing, which e.g. led '17;1' become '171' resulting in a 'skyscraper' in scenery

## [4.2.0]

### Added
- Landuse areas analysis and landuse rules for facades, objects and other assets
- X-Plane library generation feature
- Local geoindex for nearest geoname obtaining (downloaded separately)
- Configure rendering level for objects and facades from the UI
- New facades in the default facadeset

### Fixed
- Multipolygon handling was invalid for PBF files - inner ways was determined incorrectly
- Small UI fixes

### Changed
- Discontinued street lights support, since it's poor relative to mods like Enhanced Street Lights

## [4.1.0]

### Added
- Selecting 3D object by type and size, ~50 houses models added to xplane/objects/house folder for this feature
- 3D object rules supporting models choosing by height
- Facades from World2Xplane library
- 3D objects from open libraries and rules for them added for GSM, radio towers and masts

### Fixed
- Choosing 3D object by id now will ignore all other conditions if id matches
- Calculating object direction
- Removing duplicate runway segments and other small aierfield gen issues

### Changed
- Using Eclipse 2018-12 instead of Oxygen as TP. This should avoid Java 9 - related issues

## [4.0.0]

### Added
- Console version - osm2xpc, lighter than UI one
- Draped polygon support, by default - creating pavements for parkings
- Database mode using MapDB - should allow slower generation, but for areas much larger, than before
- /xplane/resources folder - all files/folders from it will be copied into generated scenario root folder

### Fixed
- Barrier generation - only types 'fence' and 'wall' will be treated as walls now
- Airfield generation - uniting orphan airfields
- Airfield generation - removing duplicate runways (e.g. when polygnal and centerline runways are specified)
- Airfield generation - runways winding direction problems

### Changed
- Folder structure changed, e.g. all X-Plane stuff is now in 'osm2xp/xplane'
- Build mavenized, tuned Travis for it on Github
- LOD support fixed to treat given LOD value as maximal instead of strict

## [3.5.1]

### Added
- Open scenery file action for toolbar
- Action to generate only airfieeds not geneating anything else 

### Fixed
- osm file support
- getting exclusion zone coordinates from input file (was broken from 3.4.0)
- rules for facades (height property not yet supported)

### Changed
- PDF reports are deprecated and no longer supported

## [3.5.0]

### Added
- Airfield generation. Program is able to generate apt.dat files from OSM, containing runways, helipads, apron and taxiways
- Restet perspective action

## [3.4.0]

### Fixed
- [Critical] Different kinds of Out Of Memory issues, like "GC Overhead exceeds limit". Storage for Nodes and Ways was fully rewritten to ake much less RAM, for now generation of 500MB file needs ~4GB RAM, in previous implementation even 8 GB wasn't enough
- [Critical] Much more failproof in case of "broken" polygons having self-intersections or partial node information. Added more logics to fix them, if it fails - generation just continues, without failing whole generation process   

## [3.3.0]

### Changed
- Polygon/tile clipping logics. Now pbf file is parsed only once and then special translator from OSM 2 XP handles polygon, if it (or it'part)
belongs to current tile, clips to match current tile it and fixes geometry problems
- Polygon cutting logics - polygons having holes are cutted to have no holes for building polys and at most 254 holes for forest polys 

## [3.2.0]

### Added

- Generation of cooling towers by selecting most suitable model (by diameter) from predefined model set
- Ability to configure custom facades, e.g. for tanks/gasometers and garages

### Fixed
- Small fixes for multipoly handling

### Changed 
- Extended facade set editor


## [3.1.0]

### Added

- Basic multipolygon support (works for forest, but not for buildings yet - buildings still don't have "holes")

### Fixed
- Polygon simplification logics - it still gave bad results in some cases before 
- Some UI issues

### Changed 
- Removed some old and unused libraries from build

## [3.0.1]

### Added

- Ability to generate bridges for roads and railways

### Fixed

- A critical issue with wrong decimal format - this led to using "," instead of "." as decimal sign on some systems and generating invalid scenario in such case  

## [3.0.0]

### Added

- Generate roads with guessing lane count from tags
- Generate railways 
- Generate power lines
- Generate chimneys using object selection by chimney height
- Generate tanks/gasometers using special facade
- Generate barriers, two types supported - fence and walls
- Facade editor - added facade preview, ability to specify fence/wall facade and some small features
- Default facade set is shipped with program archive
- Generate debug images - 2048*2048 png file can be created for each tile with generated buildings, roads etc. on it with scale 1px=1m
- Ability to generate exclusion zone based on actual OSM file coverage, do not exlude whole tile 
- Ability to show console view, since it can be useful for diagnosing map generation problems and bugs 

### Changed
- Use _building:levels_ tag for getting building height when there's no _height_ tag specified
- Do not generate buildings for polygons with most of _man___made_ constants specified - such objects usually aren't a regular buildings
- Use special facade for building with type _garage_ - regular facades for garages give poor results

### Fixed

- Incorrect distance computation when simplifying polygons - if "simplify shapes" was chosen, many buldings became octagons and looked incorrect.
- Coordinates confusion - y is now latitude, x - longtitude, like it's on usual map  


## [2.0.0]

Original version by Benjamin Blanchet with ability to generate buildings and forest zones by OSM data
 