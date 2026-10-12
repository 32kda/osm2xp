package com.osm2xp.generation.xplane.resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;

import math.geom2d.Point2D;

/**
 * DsfUtils.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class DsfUtils {

	private static final String DEFAULT_FACADE_LOD = "LOD 0.000000 25000.000000";

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
	 * Copy facades files from workspace to scene folder. Also apply lod and
	 * hard wall settings
	 * 
	 * @throws Osm2xpBusinessException
	 */
//	public static void copyFacadeSet(String sceneFolder) {
//		if (XPlaneOptionsProvider.getOptions().isPackageFacades()
//				&& !new File(sceneFolder + File.separatorChar + "facades")
//						.exists()) {
//			File from = new File(Osm2xpConstants.FACADES_SETS_PATH
//					+ File.separatorChar
//					+ XPlaneOptionsProvider.getOptions().getFacadeSet());
//			File to = new File(sceneFolder + File.separatorChar + "facades");
//
//			try {
//				FilesUtils.copyDirectory(from, to);
//				applyFacadeLod(to);
//				if (!XPlaneOptionsProvider.getOptions().isHardBuildings()) {
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
	 * return the correct folder and file name for the given lat/long
	 * coordinates
	 * 
	 * @param coordinates
	 * @return String array, 0 = folder name, 1 = file name
	 */
	public static String[] getFolderAndFileNames(Point2D coordinates) {
		int latitude = (int) coordinates.y();
		int longitude = (int) coordinates.x();

		Double dossierFirst = Double.valueOf(latitude);
		Double dossierEnd = Double.valueOf(longitude);
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
	

}
