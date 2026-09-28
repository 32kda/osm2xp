package com.osm2xp.translators.flightgear.spatial;

import java.util.List;

import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.spatial.TileSpatialIndex;
import com.osm2xp.spatial.TileSpatialIndexFactory;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;
import com.osm2xp.translators.flightgear.FlightGearForestMaterials;

/**
 * Adapts a FlightGear {@link BtgTile} (+ its {@link FlightGearBucket}, which
 * provides the local anchor) to the generic {@link TileSpatialIndexFactory}.
 * <p>
 * This is the only place that knows about BTG/ECEF/FlightGear land-class
 * materials; the resulting {@link TileSpatialIndex} is format-agnostic.
 *
 * @author osm2xp
 */
public final class FlightGearTileSpatialIndexFactory {

	private FlightGearTileSpatialIndexFactory() {
	}

	public static TileSpatialIndex build(BtgTile tile, FlightGearBucket bucket) {
		return build(tile, bucket, TileSpatialIndex.DEFAULT_GRID);
	}

	/** Builds a spatial index from a BTG tile, anchored at the bucket centre. */
	public static TileSpatialIndex build(BtgTile tile, FlightGearBucket bucket, int grid) {
		int vertexCount = tile.getVertexCount();
		double anchorLon = bucket.getCenterLon();
		double anchorLat = bucket.getCenterLat();
		BtgVector3 center = tile.getCenter();
		double cx = center.getX();
		double cy = center.getY();
		double cz = center.getZ();
		float[] vertices = tile.getVertices();

		double[] east = new double[vertexCount];
		double[] north = new double[vertexCount];
		double[] height = new double[vertexCount];
		for (int i = 0; i < vertexCount; i++) {
			double[] geodetic = FlightGearCoordinateUtils.ecefToGeodetic(cx + vertices[3 * i],
					cy + vertices[3 * i + 1], cz + vertices[3 * i + 2]);
			double[] local = FlightGearCoordinateUtils.toLocal(geodetic[0], geodetic[1], anchorLon, anchorLat);
			east[i] = local[0];
			north[i] = local[1];
			height[i] = geodetic[2];
		}

		List<BtgFace> faces = tile.getFaces();
		int faceCount = faces.size();
		int[] triA = new int[faceCount];
		int[] triB = new int[faceCount];
		int[] triC = new int[faceCount];
		byte[] flags = new byte[faceCount];
		for (int f = 0; f < faceCount; f++) {
			BtgFace face = faces.get(f);
			triA[f] = face.getA();
			triB[f] = face.getB();
			triC[f] = face.getC();
			flags[f] = FlightGearForestMaterials.isForestMaterial(face.getMaterial())
					? FlightGearForestMaterials.FLAG_FOREST
					: 0;
		}

		return TileSpatialIndexFactory.build(east, north, height, triA, triB, triC, flags, grid);
	}
}
