package com.osm2xp.translators.airfield.btg;

import java.util.List;

import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;

/**
 * Cuts a single terrain tile against the cut footprint: the faces intersecting
 * the footprint are removed (the hole), the rest are kept untouched.
 */
public final class AirfieldTileCutter {

	/** Upper bound on removed faces per tile, to avoid pathological tiles. */
	public static final int MAX_CUT_FACES = 60000;

	/** One tile's cut: the terrain plus its untouched and removed faces. */
	public static final class TileCut {
		private final BtgTile terrain;
		private final List<BtgFace> keptFaces;
		private final List<BtgCsgConverter.TerrainFace> removedFaces;

		TileCut(BtgTile terrain, List<BtgFace> keptFaces, List<BtgCsgConverter.TerrainFace> removedFaces) {
			this.terrain = terrain;
			this.keptFaces = keptFaces;
			this.removedFaces = removedFaces;
		}

		public BtgTile terrain() {
			return terrain;
		}

		public List<BtgFace> keptFaces() {
			return keptFaces;
		}

		public List<BtgCsgConverter.TerrainFace> removedFaces() {
			return removedFaces;
		}
	}

	/** Cuts the tile, or returns {@code null} if it has no removable faces / is too big. */
	public TileCut cut(BtgCsgConverter converter, BtgTile terrain, Polygon cutPolygon) {
		try {
			BtgCsgConverter.CellCut cut = converter.cellCut(terrain, cutPolygon);
			if (cut.removedFaces.size() > MAX_CUT_FACES) {
				Osm2xpLogger.warning("Skipping terrain cut: " + cut.removedFaces.size() + " faces (limit "
						+ MAX_CUT_FACES + ")");
				return null;
			}
			return new TileCut(terrain, cut.keptFaces, cut.removedFaces);
		} catch (Throwable t) {
			Osm2xpLogger.error("Error partitioning terrain tile, leaving it untouched", t);
			return null;
		}
	}
}
