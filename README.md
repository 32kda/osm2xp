# osm2xp
This project is based on original OSM2XP by Benjamin Blanchet, code was originally taken from code.google.com/p/osm2xp
# Original osm2xp 
Supports generating *buildings* using facade set from OSM data loaded from pbf file, as well as generating
* Forest zones
* Some objects by rules 

I've needed custom generation for small terrain area around an airfield and decided to extend old OSM2XP app to support this.
Since initial task is generating X-Plane 10 scenery, most fixes I've done and all testing was done for X-Plane 10 scenery generation. I can't guarantee other modes will work correctly, but if you want to post some issues or offer some ideas/help for them - feel free to contact me.

## Already done

### Generation of
* Roads, with trying to select best matching road type, if lane count data is present in OSM
* Railways
* Power lines
* Barriers, two types are supported - fence and wall
* Water tanks, fuel tanks, gasometers - using special facade
* Chimneys - are generated by inserting special objects taken from opensceneryx and ruscenery packages, as well as created by myself. Object with height closest to necessary is selected, e.g. for chimney with height 80m object with height=75m will be chosen
* For buildings with type *"garage"* special facade is used, since using regular building facade for garage usually gives poor result

### As well as
* Improved facade set editor - added facade preview, ability to delete facade and specify facades for fence or wall 
* Generating smaller area, then 1 tile - just as much as OSM PBF file defines. OSM PBF file can be obtained e.g. using [bbbike.org](https://extract.bbbike.org/ "bbbike.org")
* Migration to Java 8, using some newer libraries and some UI fixes

## Installation
You need at least Java 8 JRE or JDK to be installed

Download program archive from [Google Drive](https://drive.google.com/open?id=1Xj2mk3_RjO-kRp4VapPQoWeEVRl1iRti "Google Drive") and unpack to any folder. It's better to use path without spaces. Launch program using osm2xp/osm2xp.exe executable.

For now program is available for Windows only.

## Quick Start Guide

0. Edit/ elaborate area of interest on OSM site - they have pretty good online editor, very convenient for editing lines and polygons
1. Download OSM data file of interesting area. At this moment (06.2018) [bbbike.org](https://extract.bbbike.org/ "bbbike.org") is a good choice to do this, follow instructions on their site
2. Launch OSM2XP, choose X-Plane 10 mode using toolbar "Modes" action. On "Scenery" tab in "Scene file" field choose your OSM PBF file using "Browse" button or just paste a path to it. Edit "Scene name" field to change resulting scenery folder name if you want 
3. If necessary, go to "Buildings" tab and in "Facade sets" section configure used facade sets by selecting either facade set folder or facade set descriptor file. One facade set is provided by default with OSM2XP as an example, but it was composed by me from Alex Krug's work, some other open stuff from the Internet and some my facades, and, of course it would not suit for any area on Earth). If you want to edit facade set and/or preview it's facades, use toolbar action Tools > Facade Set Editor  
4. If necessary, go to Advanced Options tab and under Generated Items section choose item kinds which will be generated
5. Click "Generate" on toolbar (hammer icon). Generation process will start
6. After generation finishes, you can go to folder, in which original PBF file was located. You would see generated scenery folder there

### Some notes on using generated scenery

Generated scenery should be copied into "Custom Scenery" folder of X-Plane installation. In *scenery_packs.ini* file it should be placed "above" all base meshes, but "below" all custom airport scenarios. Read more [here](https://www.x-plane.com/kb/changing-custom-scenery-load-order-in-x-plane-10/ "here")

Of course, generator can't extract and use more data, than OSM map contains, and e.g. choose color of a house   
Roads, railways, powerlines, fence are just a lines on a map, and generation can't guarantee that they wouldn't overlap, will contain gates where needed, etc.
After all - it's generation, please don't expect too much from it

## Nearest plans
* Generate bridges
* Pusblish App to site