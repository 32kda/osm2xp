package com.osm2xp.generation.dsf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects DSF callbacks into a comparable model for tests.
 */
final class DsfModel implements DsfCallbacks {

	static final class Obj {
		int type;
		int mode;
		double[] coords;
	}

	static final class Poly {
		int type;
		int param;
		int depth;
		final List<List<double[]>> windings = new ArrayList<>();

		int pointCount() {
			int count = 0;
			for (List<double[]> winding : windings) {
				count += winding.size();
			}
			return count;
		}
	}

	static final class Seg {
		int type;
		int subtype;
		boolean curved;
		final List<double[]> points = new ArrayList<>();
	}

	final List<String> properties = new ArrayList<>();
	final List<String> terrainDefs = new ArrayList<>();
	final List<String> objectDefs = new ArrayList<>();
	final List<String> polygonDefs = new ArrayList<>();
	final List<String> networkDefs = new ArrayList<>();
	final List<Obj> objects = new ArrayList<>();
	final List<Poly> polygons = new ArrayList<>();
	final List<Seg> segments = new ArrayList<>();

	private Seg currentSegment;
	private Poly currentPolygon;

	static DsfModel fromText(Path path) {
		DsfModel model = new DsfModel();
		DsfTextParser.parse(path, model);
		return model;
	}

	static DsfModel fromBinary(byte[] dsf) {
		DsfModel model = new DsfModel();
		new DsfReader(dsf).read(model);
		return model;
	}

	@Override
	public void acceptProperty(String name, String value) {
		properties.add(name + "=" + value);
	}

	@Override
	public void acceptTerrainDef(String partialPath) {
		terrainDefs.add(partialPath);
	}

	@Override
	public void acceptObjectDef(String partialPath) {
		objectDefs.add(partialPath);
	}

	@Override
	public void acceptPolygonDef(String partialPath) {
		polygonDefs.add(partialPath);
	}

	@Override
	public void acceptNetworkDef(String partialPath) {
		networkDefs.add(partialPath);
	}

	@Override
	public void acceptRasterDef(String partialPath) {
	}

	@Override
	public void addObject(int objectType, double[] coords, int mode) {
		Obj obj = new Obj();
		obj.type = objectType;
		obj.mode = mode;
		obj.coords = coords.clone();
		objects.add(obj);
	}

	@Override
	public void beginSegment(int networkType, int networkSubtype, double[] coords, boolean curved) {
		currentSegment = new Seg();
		currentSegment.type = networkType;
		currentSegment.subtype = networkSubtype;
		currentSegment.curved = curved;
		currentSegment.points.add(coords.clone());
	}

	@Override
	public void addSegmentShapePoint(double[] coords, boolean curved) {
		currentSegment.points.add(coords.clone());
	}

	@Override
	public void endSegment(double[] coords, boolean curved) {
		currentSegment.points.add(coords.clone());
		segments.add(currentSegment);
		currentSegment = null;
	}

	@Override
	public void beginPolygon(int polygonType, int param, int coordDepth) {
		currentPolygon = new Poly();
		currentPolygon.type = polygonType;
		currentPolygon.param = param;
		currentPolygon.depth = coordDepth;
	}

	@Override
	public void beginPolygonWinding() {
		currentPolygon.windings.add(new ArrayList<double[]>());
	}

	@Override
	public void addPolygonPoint(double[] coords) {
		currentPolygon.windings.get(currentPolygon.windings.size() - 1).add(coords.clone());
	}

	@Override
	public void endPolygonWinding() {
	}

	@Override
	public void endPolygon() {
		polygons.add(currentPolygon);
		currentPolygon = null;
	}

	@Override
	public void beginPatch(int terrainType, double nearLOD, double farLOD, int flags, int coordDepth) {
	}

	@Override
	public void beginPrimitive(int type) {
	}

	@Override
	public void addPatchVertex(double[] coords) {
	}

	@Override
	public void endPrimitive() {
	}

	@Override
	public void endPatch() {
	}

	@Override
	public void addRasterData(DsfRasterHeader header, byte[] data) {
	}

	@Override
	public void setFilter(int filterIndex) {
	}
}
