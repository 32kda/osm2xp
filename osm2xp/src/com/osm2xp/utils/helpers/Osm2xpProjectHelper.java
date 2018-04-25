package com.osm2xp.utils.helpers;

import java.io.File;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import math.geom2d.Point2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.project.Coordinates;
import com.osm2xp.model.project.CoordinatesList;
import com.osm2xp.model.project.Osm2XpProject;

/**
 * Osm2xpProjectHelper.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class Osm2xpProjectHelper {

	private static Osm2XpProject osm2XpProject;
	private static File projectFile;

	/**
	 * @param filePath
	 * @throws Osm2xpBusinessException
	 */
	public static void loadProject(String filePath)
			throws Osm2xpBusinessException {
		Osm2XpProject result = new Osm2XpProject();
		projectFile = new File(filePath);
		try {
			JAXBContext jc = JAXBContext.newInstance(Osm2XpProject.class
					.getPackage().getName());
			Unmarshaller u = jc.createUnmarshaller();
			result = (Osm2XpProject) u.unmarshal(projectFile);
		} catch (JAXBException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		}
		osm2XpProject = result;
	}

	/**
	 * @param file
	 * @param osm2XpProject
	 * @throws Osm2xpBusinessException
	 */
	public static void saveProject() throws Osm2xpBusinessException {
		try {
			JAXBContext jc = JAXBContext.newInstance(Osm2XpProject.class
					.getPackage().getName());
			Marshaller marshaller = jc.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
					Boolean.TRUE);
			marshaller.marshal(osm2XpProject, projectFile);
		} catch (JAXBException e) {
			throw new Osm2xpBusinessException("Error saving project to file "
					+ projectFile.getPath());
		}
	}

	/**
	 * @param tilesList
	 * @throws Osm2xpBusinessException
	 */
	public static void initProject(List<Point2D> tilesList, String folderPath,
			String osmFilePath) throws Osm2xpBusinessException {
		osm2XpProject = new Osm2XpProject();
		osm2XpProject.setCoordinatesList(new CoordinatesList());
		for (Point2D coords : tilesList) {
			Coordinates coordinates = new Coordinates();
			coordinates.setLatitude((int) coords.y);
			coordinates.setLongitude((int) coords.x);
			osm2XpProject.getCoordinatesList().getCoordinates()
					.add(coordinates);
		}
		new File(folderPath).mkdirs();
		projectFile = new File(folderPath + File.separator + "osm2xp.project");
		osm2XpProject.setFile(osmFilePath);
		saveProject();
	}

	/**
	 * @param coordinates
	 * @throws Osm2xpBusinessException
	 */
	public static void removeTile(Point2D coordinates)
			throws Osm2xpBusinessException {
		if (osm2XpProject != null) {
			for (Coordinates coords : osm2XpProject.getCoordinatesList()
					.getCoordinates()) {
				Point2D point2d = new Point2D(coords.getLongitude(),
						coords.getLatitude());
				if ((point2d.x == coordinates.x)
						&& (point2d.y == coordinates.y)) {
					osm2XpProject.getCoordinatesList().getCoordinates()
							.remove(coords);
					if (osm2XpProject.getCoordinatesList().getCoordinates()
							.isEmpty()) {
						projectFile.delete();
					} else {
						saveProject();
					}

					break;
				}
			}
		}

	}

	public static Osm2XpProject getOsm2XpProject() {
		return osm2XpProject;
	}

	public static File getProjectFile() {
		return projectFile;
	}

}
