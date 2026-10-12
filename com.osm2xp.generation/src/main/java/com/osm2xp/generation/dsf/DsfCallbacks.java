package com.osm2xp.generation.dsf;

import java.util.List;

/**
 * Feeder callbacks used to exchange data between the DSF reader, writer and the
 * text parser/emitter.
 */
public interface DsfCallbacks {

	void acceptTerrainDef(String partialPath);

	void acceptObjectDef(String partialPath);

	void acceptPolygonDef(String partialPath);

	void acceptNetworkDef(String partialPath);

	void acceptRasterDef(String partialPath);

	void acceptProperty(String name, String value);

	void beginPatch(int terrainType, double nearLOD, double farLOD, int flags, int coordDepth);

	void beginPrimitive(int type);

	void addPatchVertex(double[] coords);

	void endPrimitive();

	void endPatch();

	void addObject(int objectType, double[] coords, int mode);

	void beginSegment(int networkType, int networkSubtype, double[] coords, boolean curved);

	void addSegmentShapePoint(double[] coords, boolean curved);

	void endSegment(double[] coords, boolean curved);

	void beginPolygon(int polygonType, int param, int coordDepth);

	void beginPolygonWinding();

	void addPolygonPoint(double[] coords);

	void endPolygonWinding();

	void endPolygon();

	void addRasterData(DsfRasterHeader header, byte[] data);

	void setFilter(int filterIndex);

	default void pointPoolInfo(int divisions, double hgtScale, double hgtOffset, List<String> info) {
	}
}
