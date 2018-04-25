package com.osm2xp.utils.helpers;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.eclipse.core.runtime.preferences.InstanceScope;

import math.geom2d.Point2D;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.osm2xp.constants.Osm2xpConstants;
import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.gui.Activator;
import com.osm2xp.model.facades.FacadeSetManager;
import com.osm2xp.model.stats.BuildingStat;
import com.osm2xp.model.stats.BuildingsStats;
import com.osm2xp.model.stats.ForestStat;
import com.osm2xp.model.stats.ForestsStats;
import com.osm2xp.model.stats.GenerationStats;
import com.osm2xp.model.stats.ObjectStat;
import com.osm2xp.model.stats.ObjectsStats;

/**
 * StatsHelper.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class StatsHelper {

	private static final String STATS_FOLDER = "stats";
	private static List<GenerationStats> statsList = new ArrayList<GenerationStats>();

	public static void RecapStats(String folderPath)
			throws Osm2xpBusinessException {
		if (!statsList.isEmpty()) {
			if (XplaneOptionsHelper.getOptions().isGenerateXmlStats()) {
				StatsHelper.saveRecapStats(folderPath);
			}
			if (XplaneOptionsHelper.getOptions().isGeneratePdfStats()) {
				StatsHelper.generatePdfRecapReport(folderPath);
			}
		}
	}

	private static GenerationStats getRecapStats() {
		GenerationStats result = new GenerationStats();
		result.setOsmFile(statsList.get(0).getOsmFile());
		result.setDate(statsList.get(0).getDate());
		result.setOsm2XpVersion(Osm2xpConstants.OSM2XP_VERSION);
		result.setBuildingsStats(new BuildingsStats());
		result.setForestsStats(new ForestsStats());
		result.setObjectsStats(new ObjectsStats());

		for (GenerationStats stats : statsList) {
			result.setBuildingsNumber(result.getBuildingsNumber()
					+ stats.getBuildingsNumber());
			result.setForestsNumber(result.getForestsNumber()
					+ stats.getForestsNumber());
			result.setObjectsNumber(result.getObjectsNumber()
					+ stats.getObjectsNumber());
			result.setStreetlightsNumber(result.getStreetlightsNumber()
					+ stats.getStreetlightsNumber());

			for (BuildingStat buildingStat : stats.getBuildingsStats()
					.getBuildingStat()) {
				boolean buildingStatsPresent = false;
				for (BuildingStat resultBuildingStat : result
						.getBuildingsStats().getBuildingStat()) {

					if (resultBuildingStat.getType().equalsIgnoreCase(
							buildingStat.getType())) {
						resultBuildingStat.setNumber(resultBuildingStat
								.getNumber() + buildingStat.getNumber());
						buildingStatsPresent = true;
					}
				}
				if (!buildingStatsPresent) {
					BuildingStat bStat = new BuildingStat();
					bStat.setType(buildingStat.getType());
					bStat.setNumber(buildingStat.getNumber());
					result.getBuildingsStats().getBuildingStat().add(bStat);
				}
			}

			for (ObjectStat objectStat : stats.getObjectsStats()
					.getObjectStat()) {
				boolean objectStatsPresent = false;
				for (ObjectStat resultObjectStat : result.getObjectsStats()
						.getObjectStat()) {

					if (resultObjectStat.getObjectPath().equalsIgnoreCase(
							objectStat.getObjectPath())) {
						resultObjectStat.setNumber(resultObjectStat.getNumber()
								+ objectStat.getNumber());
						objectStatsPresent = true;
					}

				}
				if (!objectStatsPresent) {
					ObjectStat bStat = new ObjectStat();
					bStat.setObjectPath(objectStat.getObjectPath());
					bStat.setNumber(objectStat.getNumber());
					result.getObjectsStats().getObjectStat().add(bStat);
				}
			}

			for (ForestStat forestStat : stats.getForestsStats()
					.getForestStat()) {
				boolean forestStatsPresent = false;
				for (ForestStat resultForestStat : result.getForestsStats()
						.getForestStat()) {

					if (resultForestStat.getForestPath().equalsIgnoreCase(
							forestStat.getForestPath())) {
						resultForestStat.setNumber(resultForestStat.getNumber()
								+ forestStat.getNumber());
						forestStatsPresent = true;
					}

				}
				if (!forestStatsPresent) {
					ForestStat bStat = new ForestStat();
					bStat.setForestPath(forestStat.getForestPath());
					bStat.setNumber(forestStat.getNumber());
					result.getForestsStats().getForestStat().add(bStat);
				}
			}

		}
		return result;
	}

	/**
	 * @param currentFile
	 * @param coordinates
	 * @return
	 */
	public static GenerationStats initStats(File currentFile,
			Point2D coordinates) {
		GenerationStats stats = new GenerationStats();
		stats.setForestsStats(new ForestsStats());
		stats.setBuildingsStats(new BuildingsStats());
		stats.setObjectsStats(new ObjectsStats());
		writeStatHeader(currentFile, coordinates, stats);
		return stats;
	}

	/**
	 * @param stats
	 * @return
	 */
	public static boolean isTileEmpty(GenerationStats stats) {
		return (stats.getBuildingsNumber() + stats.getForestsNumber()
				+ stats.getObjectsNumber() + stats.getStreetlightsNumber()) == 0;

	}

	/**
	 * @param currentFile
	 * @param coordinates
	 * @param stats
	 */
	private static void writeStatHeader(File currentFile, Point2D coordinates,
			GenerationStats stats) {
		stats.setOsm2XpVersion(Osm2xpConstants.OSM2XP_VERSION);
		DateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss");
		stats.setDate(dateFormat.format(new Date()));
		stats.setOsmFile(currentFile.getPath());
		if (coordinates != null) {
			stats.setLatitude((int) coordinates.y);
			stats.setLongitude((int) coordinates.x);
		}
	}

	/**
	 * @param generationStats
	 */
	private static void addBuilding(GenerationStats generationStats) {
		generationStats
				.setBuildingsNumber(generationStats.getBuildingsNumber() + 1);
	}

	/**
	 * @param generationStats
	 */
	public static void addStreetLight(GenerationStats generationStats) {
		generationStats.setStreetlightsNumber(generationStats
				.getStreetlightsNumber() + 1);
	}

	/**
	 * @param generationStats
	 */
	private static void addForest(GenerationStats generationStats) {
		generationStats
				.setForestsNumber(generationStats.getForestsNumber() + 1);
	}

	/**
	 * @param generationStats
	 */
	private static void addObject(GenerationStats generationStats) {
		generationStats
				.setObjectsNumber(generationStats.getObjectsNumber() + 1);
	}

	/**
	 * @param objectPath
	 * @param generationStats
	 * @return
	 */
	private static ObjectStat getObjectType(String objectPath,
			GenerationStats generationStats) {
		for (ObjectStat objectStat : generationStats.getObjectsStats()
				.getObjectStat()) {
			if (objectStat.getObjectPath().equals(objectPath))
				return objectStat;
		}
		return null;

	}

	/**
	 * @param buildingType
	 * @param generationStats
	 * @return
	 */
	private static BuildingStat getBuildingType(String buildingType,
			GenerationStats generationStats) {
		for (BuildingStat buildingStat : generationStats.getBuildingsStats()
				.getBuildingStat()) {
			if (buildingStat.getType().equals(buildingType))
				return buildingStat;
		}
		return null;

	}

	/**
	 * @param buildingType
	 * @param generationStats
	 */
	public static void addBuildingType(String buildingType,
			GenerationStats generationStats) {
		BuildingStat buildingStat = getBuildingType(buildingType,
				generationStats);
		if (buildingStat != null) {
			buildingStat.setNumber(buildingStat.getNumber() + 1);

		} else {
			BuildingStat newBuildingStat = new BuildingStat();
			newBuildingStat.setType(buildingType);
			newBuildingStat.setNumber(1);
			generationStats.getBuildingsStats().getBuildingStat()
					.add(newBuildingStat);
		}

		addBuilding(generationStats);
	}

	/**
	 * @param forestPath
	 * @param generationStats
	 * @return
	 */
	private static ForestStat getForestType(String forestPath,
			GenerationStats generationStats) {
		for (ForestStat forestStat : generationStats.getForestsStats()
				.getForestStat()) {
			if (forestStat.getForestPath().equals(forestPath))
				return forestStat;
		}
		return null;

	}

	/**
	 * @param ForestPath
	 * @param generationStats
	 */
	public static void addForestType(String ForestPath,
			GenerationStats generationStats) {
		ForestStat forestStat = getForestType(ForestPath, generationStats);
		if (forestStat != null) {
			forestStat.setNumber(forestStat.getNumber() + 1);
		} else {
			ForestStat newForestStat = new ForestStat();
			newForestStat.setForestPath(ForestPath);
			newForestStat.setNumber(1);
			generationStats.getForestsStats().getForestStat()
					.add(newForestStat);
		}

		addForest(generationStats);
	}

	/**
	 * @param objectPath
	 * @param generationStats
	 */
	public static void addObjectType(String objectPath,
			GenerationStats generationStats) {
		ObjectStat objectStat = getObjectType(objectPath, generationStats);

		if (objectStat != null) {
			objectStat.setNumber(objectStat.getNumber() + 1);

		} else {
			ObjectStat newObjectStat = new ObjectStat();
			newObjectStat.setObjectPath(objectPath);
			newObjectStat.setNumber(1);
			generationStats.getObjectsStats().getObjectStat()
					.add(newObjectStat);
		}
		addObject(generationStats);
	}

	/**
	 * @return
	 */
	public static List<GenerationStats> getStatsList() {
		return statsList;
	}

	/**
	 * @param file
	 * @param bean
	 * @throws Osm2xpBusinessException
	 */
	public static void saveStats(String folderPath, Point2D tile,
			GenerationStats stats) throws Osm2xpBusinessException {
		File file = new File(folderPath + File.separator + STATS_FOLDER
				+ File.separator + "stats_" + (int) tile.y + "-" + (int) tile.x
				+ ".xml");
		try {
			JAXBContext jc = JAXBContext.newInstance(GenerationStats.class
					.getPackage().getName());
			Marshaller marshaller = jc.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
					Boolean.TRUE);
			marshaller.marshal(stats, file);
		} catch (JAXBException e) {
			throw new Osm2xpBusinessException("Error saving stats to file "
					+ file.getPath());
		}
	}

	/**
	 * @param file
	 * @param bean
	 * @throws Osm2xpBusinessException
	 */
	public static void saveRecapStats(String folderPath)
			throws Osm2xpBusinessException {
		if (!statsList.isEmpty()) {
			File file = new File(folderPath + File.separator + STATS_FOLDER
					+ File.separator + "recapStats" + ".xml");
			GenerationStats stats = getRecapStats();
			try {
				JAXBContext jc = JAXBContext.newInstance(GenerationStats.class
						.getPackage().getName());
				Marshaller marshaller = jc.createMarshaller();
				marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
				marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
						Boolean.TRUE);
				marshaller.marshal(stats, file);
			} catch (JAXBException e) {
				throw new Osm2xpBusinessException("Error saving stats to file "
						+ file.getPath());
			}
		}
	}

	private static void addEmptyLine(Paragraph paragraph, int number) {
		for (int i = 0; i < number; i++) {
			paragraph.add(new Paragraph(" "));
		}
	}

	public static void generatePdfRecapReport(String folderPath)
			throws Osm2xpBusinessException {
		File file = new File(folderPath + File.separator + STATS_FOLDER
				+ File.separator + "recapStats.pdf");
		String title = "Osm2xp recap stats ";
		generatePdfReport(file, getRecapStats(), title);
	}

	public static void generatePdfReport(String folderPath,
			GenerationStats stats) throws Osm2xpBusinessException {
		File file = new File(folderPath + File.separator + STATS_FOLDER
				+ File.separator + "stats_" + stats.getLatitude() + "-"
				+ stats.getLongitude() + ".pdf");
		String title = "Osm2xp report for tile " + stats.getLatitude() + "/"
				+ stats.getLongitude();
		generatePdfReport(file, stats, title);

	}

	public static void generatePdfReport(File file, GenerationStats stats,
			String title) throws Osm2xpBusinessException {

		try {
			Document document = new Document();

			PdfWriter writer = PdfWriter.getInstance(document,
					new FileOutputStream(file.getPath()));
			document.open();

			document.addTitle(title);
			document.addSubject("Osm2xp report");
			document.addKeywords("osm2xp");
			document.addAuthor("osm2xp");
			document.addCreator("osm2xp");

			Paragraph preface = new Paragraph();

			addEmptyLine(preface, 1);

			preface.add(new Paragraph(title + " of file " + stats.getOsmFile(),
					new Font(Font.TIMES_ROMAN, 18, Font.BOLD)));
			addEmptyLine(preface, 1);
			SimpleDateFormat dateFormat = new SimpleDateFormat(
					"EEEE d MMMM yyyy HH:mm:ss ");
			preface.add(new Paragraph("Generated by osm2xp "
					+ Osm2xpConstants.OSM2XP_VERSION + " , "
					+ dateFormat.format(new Date()), new Font(Font.TIMES_ROMAN,
					12, Font.BOLD)));
			addEmptyLine(preface, 15);
			preface.add(new Paragraph("Settings: ", new Font(Font.TIMES_ROMAN,
					12, Font.UNDERLINE + Font.BOLD)));
			addEmptyLine(preface, 1);
			preface.add(new Paragraph("Output mode: "
					+ GuiOptionsHelper.getOptions().getOutputFormat(),
					new Font(Font.TIMES_ROMAN, 12, Font.NORMAL)));
			if (XplaneOptionsHelper.getOptions().isGenerateBuildings()) {
				preface.add(new Paragraph("Facade sets: "
						+ InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).get(FacadeSetManager.FACADE_SETS_PROP,""),
						new Font(Font.TIMES_ROMAN, 12, Font.NORMAL)));
				preface.add(new Paragraph("Facade L.O.D: "
						+ XplaneOptionsHelper.getOptions().getFacadeLod(),
						new Font(Font.TIMES_ROMAN, 12, Font.NORMAL)));
				preface.add(new Paragraph("Hard buildings: "
						+ XplaneOptionsHelper.getOptions().isHardBuildings(),
						new Font(Font.TIMES_ROMAN, 12, Font.NORMAL)));
				preface.add(new Paragraph("Package facades files: "
						+ XplaneOptionsHelper.getOptions().isPackageFacades(),
						new Font(Font.TIMES_ROMAN, 12, Font.NORMAL)));
				addEmptyLine(preface, 3);
			}
			preface.add(new Paragraph("Global stats: ", new Font(
					Font.TIMES_ROMAN, 12, Font.UNDERLINE + Font.BOLD)));
			addEmptyLine(preface, 1);
			if (XplaneOptionsHelper.getOptions().isGenerateBuildings()) {
				preface.add(new Paragraph("Number of generated buildings: "
						+ stats.getBuildingsNumber(), new Font(
						Font.TIMES_ROMAN, 12, Font.NORMAL)));
			}
			if (XplaneOptionsHelper.getOptions().isGenerateFor()) {
				preface.add(new Paragraph("Number of generated forests: "
						+ stats.getForestsNumber(), new Font(Font.TIMES_ROMAN,
						12, Font.NORMAL)));
			}
			if (XplaneOptionsHelper.getOptions().isGenerateObj()) {
				preface.add(new Paragraph("Number of generated objects: "
						+ stats.getObjectsNumber(), new Font(Font.TIMES_ROMAN,
						12, Font.NORMAL)));
			}
			if (XplaneOptionsHelper.getOptions().isGenerateStreetLights()) {
				preface.add(new Paragraph("Number of generated streetLights: "
						+ stats.getStreetlightsNumber(), new Font(
						Font.TIMES_ROMAN, 12, Font.NORMAL)));
			}
			document.add(preface);
			document.newPage();

			PdfContentByte pdfContentByte = writer.getDirectContent();
			PdfTemplate pdfTemplateChartHolder = pdfContentByte.createTemplate(
					500, 500);
			Graphics2D graphicsChart = pdfTemplateChartHolder.createGraphics(
					500, 500, new DefaultFontMapper());
			Rectangle2D chartRegion = new Rectangle2D.Double(0, 0, 500, 500);
			ChartsHelper.getRecapPieChart(stats).draw(graphicsChart,
					chartRegion);
			graphicsChart.dispose();
			Image chartImage;

			chartImage = Image.getInstance(pdfTemplateChartHolder);

			document.add(chartImage);

			// BUILDINGS CHART

			if (XplaneOptionsHelper.getOptions().isGenerateBuildings()
					&& stats.getBuildingsNumber() > 0) {
				document.newPage();
				pdfTemplateChartHolder = pdfContentByte
						.createTemplate(500, 500);
				graphicsChart = pdfTemplateChartHolder.createGraphics(500, 500,
						new DefaultFontMapper());
				chartRegion = new Rectangle2D.Double(0, 0, 500, 500);
				ChartsHelper.getBuildingsPieChart(stats).draw(graphicsChart,
						chartRegion);
				graphicsChart.dispose();
				chartImage = Image.getInstance(pdfTemplateChartHolder);
				document.add(chartImage);
			}

			// OBJECTS CHART
			if (XplaneOptionsHelper.getOptions().isGenerateObj()
					&& stats.getObjectsNumber() > 0) {
				document.newPage();
				pdfTemplateChartHolder = pdfContentByte
						.createTemplate(500, 500);
				graphicsChart = pdfTemplateChartHolder.createGraphics(500, 500,
						new DefaultFontMapper());
				chartRegion = new Rectangle2D.Double(0, 0, 500, 500);
				ChartsHelper.getObjectsPieChart(stats).draw(graphicsChart,
						chartRegion);
				graphicsChart.dispose();
				chartImage = Image.getInstance(pdfTemplateChartHolder);
				document.add(chartImage);
				document.newPage();
			}

			// FORESTS CHART
			if (XplaneOptionsHelper.getOptions().isGenerateFor()
					&& stats.getForestsNumber() > 0) {
				document.newPage();
				pdfTemplateChartHolder = pdfContentByte
						.createTemplate(500, 500);
				graphicsChart = pdfTemplateChartHolder.createGraphics(500, 500,
						new DefaultFontMapper());
				chartRegion = new Rectangle2D.Double(0, 0, 500, 500);
				ChartsHelper.getForestsPieChart(stats).draw(graphicsChart,
						chartRegion);
				graphicsChart.dispose();
				chartImage = Image.getInstance(pdfTemplateChartHolder);
				document.add(chartImage);
			}
			document.close();
		} catch (BadElementException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		} catch (DocumentException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		} catch (FileNotFoundException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		}

	}

}
