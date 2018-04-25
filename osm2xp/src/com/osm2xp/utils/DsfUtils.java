package com.osm2xp.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;

import org.eclipse.core.runtime.preferences.InstanceScope;

import math.geom2d.Box2D;
import math.geom2d.Point2D;

import com.osm2xp.constants.Osm2xpConstants;
import com.osm2xp.constants.Perspectives;
import com.osm2xp.constants.XplaneConstants;
import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.exceptions.Osm2xpTechnicalException;
import com.osm2xp.gui.Activator;
import com.osm2xp.model.facades.FacadeSetManager;
import com.osm2xp.model.xplane.XplaneDsfObject;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.impl.OsmWriterImpl;

/**
 * DsfUtils.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class DsfUtils {

	private static final String DEFAULT_FACADE_LOD = "LOD 0.000000 25000.000000";

	public static void xplaneDsfObjectToOsm(XplaneDsfObject dsfObject) {
		OsmWriterImpl writer = new OsmWriterImpl("c:\\debug\\"
				+ new GregorianCalendar().getTimeInMillis() + "\\");
		writer.init(GeomUtils.cleanCoordinatePoint(dsfObject.getOsmPolygon()
				.getPolygon().getFirstPoint()));
		int cpt = 1;
		for (Point2D point : dsfObject.getOsmPolygon().getPolygon()
				.getVertices()) {

			writer.write("<node id=\"" + cpt++ + "\" lat=\"" + point.y
					+ "\" lon=\"" + point.x + "\" version=\"1\" />\n");
		}

		writer.write("<way id=\"" + cpt++
				+ "\" visible=\"true\" version=\"2\" >\n");
		writer.write("<tag k=\"building\" v=\"yes\"/>\n");

		cpt = 1;
		for (Point2D point : dsfObject.getOsmPolygon().getPolygon()
				.getVertices()) {

			writer.write("<nd ref=\"" + cpt++ + "\"/>\n");
		}
		writer.write("</way>\n");

		// write origin
		Point2D origin = null;// GeomUtils.getRotationPoint(dsfObject.getOsmPolygon().getPolygon(),50,0);

		writer.write("<node id=\"" + cpt++ + "\" lat=\"" + origin.y
				+ "\" lon=\"" + origin.x + "\" version=\"1\" >\n");
		writer.write("<tag k=\"man_made\" v=\"water_tower\"/>\n");
		writer.write("</node>\n");

		writer.complete(null);
	}

	/**
	 * get the right path to the dsfTool executable
	 * 
	 * @return String the path to the dsfTool executable
	 */
	public static String getDsfToolPath() {
		String dsfTool;
		if (MiscUtils.isWindows()) {
			dsfTool = Osm2xpConstants.UTILS_PATH + File.separatorChar
					+ "DSFTool.exe";
		} else if (MiscUtils.isMac()) {
			dsfTool = Osm2xpConstants.UTILS_PATH + File.separatorChar
					+ "DSFToolMac";
		} else {
			dsfTool = Osm2xpConstants.UTILS_PATH + File.separatorChar
					+ "DSFToolLinux";
		}
		return dsfTool;
	}

	public static void applyFacadeLod(File to) throws FileNotFoundException,
			IOException {

		File[] filesList = to.listFiles();
		if (filesList != null) {
			for (int cpt = 0; cpt < filesList.length; cpt++) {
				if (filesList[cpt].getName().toLowerCase().contains(".fac")) {
					File facade = filesList[cpt];
					String line = null;
					String facadeContent = new String();
					RandomAccessFile reader = new RandomAccessFile(facade, "rw");
					while ((line = reader.readLine()) != null) {
						facadeContent += line + "\r\n";
					}
					facadeContent = facadeContent.replaceAll(
							DEFAULT_FACADE_LOD, "LOD 0.000000 "
									+ XplaneOptionsHelper.getOptions()
											.getFacadeLod() + ".000000");
					FileWriter writer = new FileWriter(facade.getAbsolutePath());
					writer.write(facadeContent);
					writer.close();

				}
			}
		}
	}

	/**
	 * @param to
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void removeConcreteRoofsAndWalls(File to)
			throws FileNotFoundException, IOException {

		File[] filesList = to.listFiles();
		if (filesList != null) {
			for (int cpt = 0; cpt < filesList.length; cpt++) {
				if (filesList[cpt].getName().toLowerCase().contains(".fac")) {
					File facade = filesList[cpt];
					String line = null;
					String facadeContent = new String();
					try (RandomAccessFile reader = new RandomAccessFile(facade, "rw")) {
						while ((line = reader.readLine()) != null) {
							facadeContent += line + "\r\n";
						}
						facadeContent = facadeContent.replaceAll(
								"HARD_ROOF concrete", "");
						facadeContent = facadeContent.replaceAll(
								"HARD_WALL concrete", "");
						try (FileWriter writer = new FileWriter(facade.getAbsolutePath())) {
							writer.write(facadeContent);
						}
					}

				}
			}
		}
	}

	/**
	 * return the dsf file for given coordinates and scene folder
	 * 
	 * @param sceneFolder
	 * @param coordinates
	 * @return File
	 */
	public static File computeXPlaneDsfFilePath(String sceneFolder,
			Point2D coordinates) {
		// compute folder and file name , following xplane specifications
		String[] folderAndFileNames = DsfUtils
				.getFolderAndFileNames(coordinates);
		String nomFichier = folderAndFileNames[1];
		String nomRepertoire = folderAndFileNames[0];
		String repertoireDsf = sceneFolder + File.separatorChar
				+ "Earth nav data" + File.separatorChar + nomRepertoire;
		String nomFichierDsf = repertoireDsf + File.separatorChar + nomFichier
				+ ".dsf.txt";
		File dsfFile = new File(nomFichierDsf);
		return dsfFile;
	}

	/**
	 * @param sceneFolder
	 * @param dsfObjectsProvider
	 * @throws Osm2xpBusinessException
	 */
	public static void writeLibraryFile(String sceneFolder,
			DsfObjectsProvider dsfObjectsProvider) {
		BufferedWriter outputlibrary = null;
		try {

			FileWriter writerLibrary = new FileWriter(sceneFolder
					+ File.separatorChar + "library.txt", false);
			outputlibrary = new BufferedWriter(writerLibrary);

			outputlibrary.write("I" + "\n");
			outputlibrary.write("800" + "\n");
			outputlibrary.write("LIBRARY" + "\n");
			for (String facade : dsfObjectsProvider.getFacadesList()) {
				outputlibrary.write("EXPORT \\lib\\osm2xp\\facades\\" + facade
						+ " .." + File.separator + "osm2xpFacades"
						+ File.separator
						+ InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).get(FacadeSetManager.FACADE_SETS_PROP,"")
						+ File.separator + facade + "\n");

			}
			outputlibrary.flush();
		} catch (IOException e) {
			throw new Osm2xpTechnicalException(e);
		}
	}

	/**
	 * Copy facades files from workspace to scene folder. Also apply lod and
	 * hard wall settings
	 * 
	 * @throws Osm2xpBusinessException
	 */
//	public static void copyFacadeSet(String sceneFolder) {
//		if (XplaneOptionsHelper.getOptions().isPackageFacades()
//				&& !new File(sceneFolder + File.separatorChar + "facades")
//						.exists()) {
//			File from = new File(Osm2xpConstants.FACADES_SETS_PATH
//					+ File.separatorChar
//					+ XplaneOptionsHelper.getOptions().getFacadeSet());
//			File to = new File(sceneFolder + File.separatorChar + "facades");
//
//			try {
//				FilesUtils.copyDirectory(from, to);
//				applyFacadeLod(to);
//				if (!XplaneOptionsHelper.getOptions().isHardBuildings()) {
//					DsfUtils.removeConcreteRoofsAndWalls(to);
//				}
//			} catch (FileNotFoundException e) {
//				throw new Osm2xpTechnicalException(e);
//			} catch (IOException e) {
//				throw new Osm2xpTechnicalException(e);
//			}
//
//		}
//	}

	/**
	 * Compile a dsf file from a text file
	 * 
	 * @param textFile
	 * @param dsfFile
	 */
	public static void textToDsf(File textFile, File dsfFile) {
		try {
			Runtime runtime = Runtime.getRuntime();
			runtime.exec(new String[] { getDsfToolPath(), "--text2dsf",
					textFile.getPath(), dsfFile.getPath() });
		} catch (IOException e) {
			Osm2xpLogger.error("Error on .dsf conversion.", e);
		}
	}

	/**
	 * return the correct folder and file name for the given lat/long
	 * coordinates
	 * 
	 * @param coordinates
	 * @return String array, 0 = folder name, 1 = file name
	 */
	public static String[] getFolderAndFileNames(Point2D coordinates) {
		int latitude = (int) coordinates.y;
		int longitude = (int) coordinates.x;

		Double dossierFirst = new Double(latitude);
		Double dossierEnd = new Double(longitude);
		while (dossierFirst % 10 != 0) {
			dossierFirst--;
		}
		while (dossierEnd % 10 != 0) {
			dossierEnd--;
		}

		DecimalFormat dfLat = (DecimalFormat) DecimalFormat.getNumberInstance();
		dfLat.applyPattern("00");
		DecimalFormat dfLong = (DecimalFormat) DecimalFormat
				.getNumberInstance();
		dfLong.applyPattern("000");
		StringBuffer fichier = new StringBuffer();
		StringBuffer dossier = new StringBuffer();
		if (latitude >= 0) {
			fichier.append("+");
			dossier.append("+");
		}
		fichier.append(dfLat.format(latitude));
		dossier.append(dfLat.format(dossierFirst));
		if (longitude >= 0) {
			fichier.append("+");
			dossier.append("+");
		}
		fichier.append(dfLong.format(longitude));
		dossier.append(dfLong.format(dossierEnd));
		String nomFichier = fichier.toString();
		String nomRepertoire = dossier.toString();
		String[] result = new String[] { nomRepertoire, nomFichier };
		return result;
	}

	/**
	 * @param tileCoordinate
	 * @return
	 */
	public static String getDsfExclusions(String tileCoordinate) {
		StringBuilder sb = new StringBuilder();
		// Exclusions
		if (XplaneOptionsHelper.getOptions().isExcludeObj()) {
			// smart OBJ exclusion
			if (XplaneOptionsHelper.getOptions().isSmartExclusions()) {
				sb.append(XplaneConstants.EXCLUSION_PLACEHOLDER + "\n");
			} else {
				sb.append("PROPERTY sim/exclude_obj " + tileCoordinate);
			}
		}
		if (XplaneOptionsHelper.getOptions().isExcludeFac()) {
			sb.append("PROPERTY sim/exclude_fac " + tileCoordinate);
		}
		if (XplaneOptionsHelper.getOptions().isExcludeFor()) {
			sb.append("PROPERTY sim/exclude_for " + tileCoordinate);
		}
		if (XplaneOptionsHelper.getOptions().isExcludeNet()) {
			sb.append("PROPERTY sim/exclude_net " + tileCoordinate);
		}
		if (XplaneOptionsHelper.getOptions().isExcludeLin()) {
			sb.append("PROPERTY sim/exclude_lin " + tileCoordinate);
		}
		if (XplaneOptionsHelper.getOptions().isExcludeStr()) {
			sb.append("PROPERTY sim/exclude_str " + tileCoordinate);
		}

		if (GuiOptionsHelper.getOptions().getOutputFormat()
				.equals(Perspectives.PERSPECTIVE_XPLANE10)) {

			if (XplaneOptionsHelper.getOptions().isExcludePol()) {
				sb.append("PROPERTY sim/exclude_pol " + tileCoordinate);
			}

			if (XplaneOptionsHelper.getOptions().isExcludeBch()) {
				sb.append("PROPERTY sim/exclude_bch " + tileCoordinate);
			}
		}

		return sb.toString();
	}

	/**
	 * return the string representation of a dsf header
	 * 
	 * @param coordinates
	 * @param singlesFacadesList
	 * @param facadesList
	 * @param forestsList
	 * @param objectsList
	 * @return String the dsf header
	 */
	public static String getDsfHeader(Point2D coordinates,
			DsfObjectsProvider dsfObjectsProvider) {

		StringBuilder sb = new StringBuilder();
		int latitude = (int) coordinates.y;
		int longitude = (int) coordinates.x;
		Box2D exclusionBox = dsfObjectsProvider.getExclusionBox();
		String exclusionCoordinate = exclusionBox != null ? formatDsfCoord(exclusionBox.getMinX()) + "/" + formatDsfCoord(exclusionBox.getMinY()) + "/" + 
				formatDsfCoord(exclusionBox.getMaxX()) + "/" + formatDsfCoord(exclusionBox.getMaxY()) + "\n"				
				: longitude + ".000000/" + latitude + ".000000/"
				+ (longitude + 1) + ".000000/" + (latitude + 1) + ".000000\n";

		sb.append("I\n");
		sb.append("800\n");
		sb.append("DSF2TEXT\n\n");
		sb.append("PROPERTY sim/planet earth\n");
		sb.append("PROPERTY sim/overlay 1\n");
//		if (XplaneOptionsHelper.getOptions().isGenerateStreetLights()) {
//			// we set the require index so only streetlights objects will
//			// disappear when using the objects number slider in xplane
//			int requireIndex = dsfObjectsProvider.getStreetLightObjectsList()
//					.size();
//			sb.append("PROPERTY sim/require_object 1/" + requireIndex + "\n");
//		} else {
			sb.append("PROPERTY sim/require_object 1/0\n");
		//}
		sb.append("PROPERTY sim/require_facade 6/0\n");
		sb.append("PROPERTY sim/creation_agent Osm2Xp "
				+ Osm2xpConstants.OSM2XP_VERSION + " by Benjamin Blanchet \n");
		// Exclusions
		sb.append(getDsfExclusions(exclusionCoordinate));
		sb.append("PROPERTY sim/west " + longitude + "\n");
		sb.append("PROPERTY sim/east " + (longitude + 1) + "\n");
		sb.append("PROPERTY sim/north " + (latitude + 1) + "\n");
		sb.append("PROPERTY sim/south " + latitude + "\n\n");

		// forests files
		if (dsfObjectsProvider.getForestsList() != null) {
			for (String forest : dsfObjectsProvider.getForestsList()) {
				sb.append("POLYGON_DEF " + forest + "\n");
			}
		}

		
		// facades rules
		if (dsfObjectsProvider.getSinglesFacadesList() != null) {
			for (String facadeTagRule : dsfObjectsProvider
					.getSinglesFacadesList()) {
				sb.append("POLYGON_DEF " + facadeTagRule + "\n");
			}
		}


		// facades files
		if (dsfObjectsProvider.getFacadesList() != null) {
			for (String facade : dsfObjectsProvider.getFacadesList()) {
				String facadeDeclaration = null;
				if (!XplaneOptionsHelper.getOptions().isPackageFacades()) {

					facadeDeclaration = "POLYGON_DEF /lib/osm2xp/facades/"
							+ facade + "\n";
				} else {
					facadeDeclaration = "POLYGON_DEF facades/" + facade + "\n";
				}
				sb.append(facadeDeclaration);
			}
			sb.append("\n");
		}

		if (dsfObjectsProvider.getObjectsList() != null) {
			for (String objectPath : dsfObjectsProvider.getObjectsList()) {
				sb.append("OBJECT_DEF " + objectPath + "\n");
			}
		}
		
		sb.append("NETWORK_DEF lib/g10/roads.net\n");

		return sb.toString();

	}

	/**
	 * Formats lat/long to string with 9 chars after "."
	 * @param coord coord value to format
	 * @return formatted string
	 */
	public static String formatDsfCoord(double coord) {
		return String.format("%.9f", coord);
	}

}
