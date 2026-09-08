package com.osm2xp.translators.flightgear;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;

import math.geom2d.Point2D;

/**
 * FlightGear counterpart of the X-Plane {@code XPSpecObjectTranslator}. Handles "special"
 * objects (chimneys, cooling towers, silos, storage tanks, ...) by selecting a FlightGear
 * {@code .ac} model whose size is closest to the OSM polygon and writing an
 * {@code OBJECT_SHARED_AGL Models/...} line into the sub-bucket STG.
 * <p>
 * Size can mean different dimensions, most suitable for choosing an object of the given type -
 * e.g. height for a chimney, diameter for a cooling tower, silo or storage tank. Subclasses
 * override {@link #getObjectSize(OsmPolygon)} to provide the size of the selected OSM polygon and
 * {@link #getObjectFilePreffix()} to pick the model name prefix used to scan the bundled
 * {@code flightgear/specobjects} folder.
 * <p>
 * Models that are not bundled in {@code flightgear/specobjects} (e.g. the FlightGear shared silo
 * and storage tank models from {@code Models/Agriculture/} and {@code Models/Industrial/}) can be
 * hardcoded as {@link ObjectDef}s via {@link #buildObjectDefs(String...)}, which parses the model
 * diameter out of the file name, and selected by overriding {@link #getObjectDefs(OsmPolygon)}.
 *
 * @author osm2xp
 */
public abstract class FlightGearSpecObjectTranslator extends FlightGearObjectTranslator {

	private static final String AC_EXT = ".ac";



	/** A model candidate: its nominal size (e.g. diameter in metres) and full STG model path. */
	protected static class ObjectDef {
		public final int size;
		public final String path;

		public ObjectDef(String path, int size) {
			this.path = path;
			this.size = size;
		}
	}

	protected List<ObjectDef> objectDefs = new ArrayList<>();

	public FlightGearSpecObjectTranslator() {
		if (generationEnabled()) {
			File specObjectsFolder = PathsService.getPathsProvider().getFlightGearSpecObjectsFolder();
			if (specObjectsFolder.isDirectory()) {
				String preffix = getObjectFilePreffix();
				String[] objectFiles = specObjectsFolder.list((parent, name) -> name.toLowerCase().startsWith(preffix)
						&& name.toLowerCase().endsWith(AC_EXT));
				for (String currentFile : objectFiles) {
					int idx = currentFile.lastIndexOf('-');
					int idx2 = currentFile.lastIndexOf('.');
					if (idx > 0 && idx2 > idx) {
						int size = Integer.parseInt(currentFile.substring(idx + 1, idx2));
						objectDefs.add(new ObjectDef(FlightGearModelsProvider.SPEC_OBJECTS_MODEL_PREFIX + currentFile,
								size));
					}
				}
			}
		}
	}

	protected abstract boolean generationEnabled();

	/**
	 * Builds {@link ObjectDef}s from the given model paths, parsing the model diameter (the
	 * trailing {@code <number>m}/{@code <number>M} before {@code .ac}) out of each file name.
	 * Models without a parseable diameter are skipped.
	 */
	protected static List<ObjectDef> buildObjectDefs(String... modelPaths) {
		List<ObjectDef> result = new ArrayList<>();
		for (String path : modelPaths) {
			int size = parseDiameterFromName(path);
			if (size > 0) {
				result.add(new ObjectDef(path, size));
			}
		}
		return result;
	}

	/** Creates a single {@link ObjectDef} with an explicit size, for models lacking a diameter in their name. */
	protected static ObjectDef objectDef(String path, int size) {
		return new ObjectDef(path, size);
	}

	/**
	 * Extracts the diameter (in metres) encoded in a model file name (e.g.
	 * {@code Models/Agriculture/1silo_8m.ac} -> 8), or {@code -1} if it cannot be parsed.
	 */
	protected static int parseDiameterFromName(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
		if (name.endsWith(AC_EXT)) {
			name = name.substring(0, name.length() - AC_EXT.length());
		}
		int idx = name.lastIndexOf('m');
		while (idx >= 0) {
			int start = idx;
			while (start > 0 && Character.isDigit(name.charAt(start - 1))) {
				start--;
			}
			if (start < idx) {
				try {
					return Integer.parseInt(name.substring(start, idx));
				} catch (NumberFormatException e) {
					// keep scanning backwards
				}
			}
			idx = name.lastIndexOf('m', idx - 1);
		}
		return -1;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!(osmPolyline instanceof OsmPolygon)) { // We support only polygon-based objects for now
			return false;
		}
		OsmPolygon osmPolygon = (OsmPolygon) osmPolyline;
		if (canProcess(osmPolygon)) {
			int size = getObjectSize(osmPolygon);
			if (size < 0) {
				return false;
			}
			String modelFile = getSuitableModelFile(getObjectDefs(osmPolygon), size);
			if (modelFile == null) {
				return false;
			}
			Point2D center = osmPolygon.getCenter();
			writeObject(modelFile, center.y(), center.x(), 0);
			return true;
		}
		return false;
	}

	protected abstract boolean canProcess(OsmPolygon osmPolygon);

	/**
	 * Returns the model candidates to choose from for the given polygon. The default
	 * implementation returns the models scanned from the bundled {@code flightgear/specobjects}
	 * folder; subclasses selecting hardcoded models (e.g. silos and storage tanks) override this.
	 */
	protected List<ObjectDef> getObjectDefs(OsmPolygon osmPolygon) {
		return objectDefs;
	}

	private String getSuitableModelFile(List<ObjectDef> defs, int size) {
		int minDelta = Integer.MAX_VALUE;
		ObjectDef optimal = null;
		for (ObjectDef objectDef : defs) {
			int delta = Math.abs(objectDef.size - size);
			if (delta < minDelta) {
				minDelta = delta;
				optimal = objectDef;
			}
		}
		return optimal == null ? null : optimal.path;
	}

	/**
	 * Object size to choose object. Should return -1 if the size is too small to add an object.
	 *
	 * @param osmPolygon
	 * @return
	 */
	protected abstract int getObjectSize(OsmPolygon osmPolygon);

	protected abstract String getObjectFilePreffix();

	@Override
	public void translationComplete() {
		// Nothing to flush - STG lines are written eagerly
	}

	@Override
	public boolean isTerminating() {
		return false;
	}
}
