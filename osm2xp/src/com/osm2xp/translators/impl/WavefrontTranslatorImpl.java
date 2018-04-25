package com.osm2xp.translators.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;
import org.osm2world.core.ConversionFacade;
import org.osm2world.core.target.Target;
import org.osm2world.core.target.obj.ObjTarget;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.options.ObjectFile;
import com.osm2xp.model.options.ObjectTagRule;
import com.osm2xp.model.options.ObjectsRulesList;
import com.osm2xp.model.osm.Node;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.osm.Tag;
import com.osm2xp.model.osm.Way;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.DsfUtils;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.WavefrontOptionsHelper;
import com.osm2xp.utils.helpers.XmlHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;

/**
 * Wavefront translator implementation. Uses Osm2World (http://osm2world.org/)
 * to generate 3D objects from filtered osm data.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class WavefrontTranslatorImpl implements ITranslator {
	/**
	 * google map logo, TODO really dirty to use a url, gotta change it.
	 */
	private static final String GOOGLEMAPS_LOGO = "http://maps.gstatic.com/intl/fr_ALL/mapfiles/vt/mapsgl_promo_v2.png";
	/**
	 * google map url.
	 */
	private static final String GOOGLEMAPS_URL = "http://maps.google.com/maps?q=";
	/**
	 * openStreetMap way detail url
	 */
	private static final String OPENSTREETMAP_WAY_URL = "http://www.openstreetmap.org/browse/way/";
	/**
	 * openStreetMap logo, TODO really dirty to use a url, gotta change it.
	 */
	private static final String OPENSTREETMAP_LOGO = "http://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/Openstreetmap_logo.svg/45px-Openstreetmap_logo.svg.png";
	/**
	 * generated file folder path.
	 */
	private String folderPath;
	/**
	 * current lat/long tile.
	 */
	private Point2D currentTile;
	/**
	 * objects tag rules.
	 */
	private List<ObjectTagRule> objectsTagRules = new ArrayList<ObjectTagRule>();
	/**
	 * objects map.
	 */
	private HashMap<Long, Point2D> objectsMap = new HashMap<Long, Point2D>();
	/**
	 * Single object export (one big 3D object for all osm filtered data)?
	 */
	private Boolean singleObjectExport;
	/**
	 * osm nodes list
	 */
	private List<Node> globalNodeList = new ArrayList<Node>();
	/**
	 * osm ways list
	 */
	private List<OsmPolygon> globalWayList = new ArrayList<OsmPolygon>();

	/**
	 * WaveFront translator constructor.
	 * 
	 * @param folderPath
	 *            folder path.
	 * @param currentTile
	 *            current lat/long tile.
	 * @param singleObjectExport
	 *            singleExport yes/no
	 */
	public WavefrontTranslatorImpl(String folderPath, Point2D currentTile,
			Boolean singleObjectExport) {
		this.folderPath = folderPath;
		this.singleObjectExport = singleObjectExport;
		this.currentTile = currentTile;
		new File(folderPath + File.separator + "objects" + File.separator)
				.mkdirs();

	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {

	}

	/**
	 * export a single object to wavefront file format
	 * 
	 * @param nodeList
	 * @param way
	 */
	private void exportPolygonToObject(final OsmPolygon osmPolygon) {
		if (osmPolygon.getNodes().size() > 4
				|| (OsmUtils.getHeightFromTags(osmPolygon.getTags()) != null && OsmUtils
						.getHeightFromTags(osmPolygon.getTags()) > 20)) {
			String filePath = null;
			try {
				filePath = OsmUtils.CreateTempFile(folderPath, osmPolygon);
			} catch (IOException e2) {

				e2.printStackTrace();
			}
			ConversionFacade cv = new ConversionFacade();
			ObjTarget target = null;
			List<Target<?>> targets = new ArrayList<Target<?>>();
			try {

				target = new ObjTarget(new PrintStream(new File(folderPath
						+ File.separator + "objects" + File.separator
						+ osmPolygon.getId() + ".obj")), (new PrintStream(
						new File(folderPath + File.separator + "objects"
								+ File.separator + osmPolygon.getId()
								+ ".obj.mtl"))));
				new File(folderPath + File.separator + "objects files"
						+ File.separator + osmPolygon.getId() + ".obj.mtl")
						.deleteOnExit();
			} catch (FileNotFoundException e1) {
			}
			targets.add(target);
			try {
				cv.createRepresentations(new File(filePath), null, null,
						targets);
				ObjectTagRule objectTagRule = new ObjectTagRule(new Tag("id",
						String.valueOf(osmPolygon.getId())),
						new ArrayList<ObjectFile>() {
							{
								add(new ObjectFile("objects/"
										+ osmPolygon.getId() + ".obj"));

							}
						}, 0, true);

				objectsTagRules.add(objectTagRule);
				objectsMap
						.put(osmPolygon.getId(), GeomUtils
								.getPolygonCenter(GeomUtils
										.getPolygonFromOsmNodes(osmPolygon
												.getNodes())));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {
		if (OsmUtils.isBuilding(osmPolygon.getTags())) {

			if (osmPolygon.getNodes().size() > WavefrontOptionsHelper
					.getOptions().getWaveFrontExportPointsFilter()
					|| (OsmUtils.getHeightFromTags(osmPolygon.getTags()) != null && OsmUtils
							.getHeightFromTags(osmPolygon.getTags()) > WavefrontOptionsHelper
							.getOptions().getWaveFrontExportHeightFilter())) {
				globalWayList.add(osmPolygon);
				globalNodeList.addAll(osmPolygon.getNodes());

				if (!singleObjectExport) {
					exportPolygonToObject(osmPolygon);
				}

			}
		}

	}

	@Override
	public void processRelation(Relation relation)
			throws Osm2xpBusinessException {
	}

	@Override
	public void complete() {
		if (singleObjectExport) {
			if (!globalWayList.isEmpty()) {
				String fileName = null;
				try {
					fileName = OsmUtils.CreateTempFile(folderPath,
							globalWayList, currentTile.y + "_" + currentTile.x);
				} catch (IOException e) {
					e.printStackTrace();
				}
				File osmFile = new File(fileName);
				exportOsmFileToObject(osmFile);
				osmFile.deleteOnExit();
			}
		} else {
			try {
				XmlHelper.saveToXml(new ObjectsRulesList(objectsTagRules),
						new File(folderPath + File.separator
								+ "objectsRules.xml"));
			} catch (Osm2xpBusinessException e1) {
				Osm2xpLogger.error("Error writing object rules file.", e1);
			}
			try {
				writeRecapHtmlFile();
			} catch (IOException e) {
				Osm2xpLogger.error("Error writing html recap file", e);
			}
		}
		if (WavefrontOptionsHelper.getOptions()
				.isGenerateWaveFrontDsfPlaceholder()) {
			try {
				if (!singleObjectExport) {
					writeDsfPlaceHolder();
				} else {
					writeDsfPlaceHolderForSingleLargeObject();
				}
			} catch (Osm2xpBusinessException e) {
				Osm2xpLogger.error("Error writing dsf placeholder", e);
			}
		}
	}

	/**
	 * create a dsf to place objects file.
	 * 
	 * @throws Osm2xpBusinessException
	 */
	private void writeDsfPlaceHolder() throws Osm2xpBusinessException {
		String[] folderAndFileNames = DsfUtils
				.getFolderAndFileNames(currentTile);
		String fileName = folderAndFileNames[1];
		String folderName = folderAndFileNames[0];

		File dsfTextFile = new File(folderPath + File.separatorChar
				+ "Earth nav data" + File.separatorChar + folderName
				+ File.separatorChar + fileName + ".dsf.txt");
		dsfTextFile.deleteOnExit();
		List<String> objectsList = new ArrayList<String>();
		for (OsmPolygon way : globalWayList) {
			objectsList.add("objects/" + way.getId() + ".obj");
		}
		DsfObjectsProvider dsfObjectsProvider = new DsfObjectsProvider();
		dsfObjectsProvider.setObjectsList(objectsList);
		String dsfHeaderText = DsfUtils.getDsfHeader(currentTile,
				dsfObjectsProvider);
		FilesUtils.writeTextToFile(dsfTextFile, dsfHeaderText, false);
		for (OsmPolygon way : globalWayList) {

			LinearRing2D polygon = GeomUtils.getPolygonFromOsmNodes(way
					.getNodes());
			Point2D center = GeomUtils.getPolygonCenter(polygon);
			StringBuffer sb = new StringBuffer();
			sb.append("OBJECT "
					+ objectsList.indexOf("/objects files/" + way.getId()
							+ ".obj") + " " + center.y + " " + center.x + " "
					+ 0);
			sb.append(System.getProperty("line.separator"));
			FilesUtils.writeTextToFile(dsfTextFile, sb.toString(), true);

		}
		DsfUtils.textToDsf(dsfTextFile, new File(folderPath
				+ File.separatorChar + "Earth nav data" + File.separatorChar
				+ folderName + File.separatorChar + fileName + ".dsf"));
	}

	/**
	 * create a dsf to place objects file.
	 * 
	 * @throws Osm2xpBusinessException
	 */
	private void writeDsfPlaceHolderForSingleLargeObject()
			throws Osm2xpBusinessException {
		String[] folderAndFileNames = DsfUtils
				.getFolderAndFileNames(currentTile);
		String fileName = folderAndFileNames[1];
		String folderName = folderAndFileNames[0];

		File dsfTextFile = new File(folderPath + File.separatorChar
				+ "Earth nav data" + File.separatorChar + folderName
				+ File.separatorChar + fileName + ".dsf.txt");
		dsfTextFile.deleteOnExit();
		List<String> objectsList = new ArrayList<String>();

		objectsList.add("objects/" + currentTile.y + "_" + currentTile.x
				+ ".obj");
		DsfObjectsProvider dsfObjectsProvider = new DsfObjectsProvider();
		dsfObjectsProvider.setObjectsList(objectsList);
		String dsfHeaderText = DsfUtils.getDsfHeader(currentTile,
				dsfObjectsProvider);
		FilesUtils.writeTextToFile(dsfTextFile, dsfHeaderText, false);
		List<Point2D> areaNodes = new ArrayList<Point2D>();
		// compute the center of the new large object
		for (OsmPolygon way : globalWayList) {

			areaNodes.add(GeomUtils.getNodesCenter(way.getNodes()));

		}
		Point2D center = GeomUtils
				.getPolygonCenter(new LinearRing2D(areaNodes));
		StringBuffer sb = new StringBuffer();
		sb.append("OBJECT 0" + " " + center.y + " " + center.x + " " + 0);
		sb.append(System.getProperty("line.separator"));
		FilesUtils.writeTextToFile(dsfTextFile, sb.toString(), true);

		DsfUtils.textToDsf(dsfTextFile, new File(folderPath
				+ File.separatorChar + "Earth nav data" + File.separatorChar
				+ folderName + File.separatorChar + fileName + ".dsf"));
	}

	/**
	 * export an osm file to wavefront object
	 * 
	 * @param osmFile
	 */
	private void exportOsmFileToObject(File osmFile) {

		ConversionFacade cv = new ConversionFacade();
		ObjTarget target = null;
		List<Target<?>> targets = new ArrayList<Target<?>>();
		try {

			target = new ObjTarget(new PrintStream(new File(folderPath
					+ File.separator + "objects" + File.separator
					+ currentTile.y + "_" + currentTile.x + ".obj")),
					(new PrintStream(new File(folderPath + File.separator
							+ "objects" + File.separator + currentTile.y + "_"
							+ currentTile.x + ".obj.mtl"))));
			new File(folderPath + File.separator + "objects files"
					+ File.separator + currentTile.y + "_" + currentTile.x
					+ ".obj.mtl").deleteOnExit();
		} catch (FileNotFoundException e1) {
		}
		targets.add(target);
		try {
			cv.createRepresentations(osmFile, null, null, targets);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void init() {
		Osm2xpLogger.info("Starting wavefront (.obj) generation of tile "
				+ (int) currentTile.y + "/" + (int) currentTile.x
				+ " - wavefront export uses osm2world http://osm2world.org/");
	}

	/**
	 * write a html report of generated objects
	 * 
	 * @throws IOException
	 */
	private void writeRecapHtmlFile() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<head>");
		sb.append("<style type=\"text/css\">");
		sb.append("table {");
		sb.append(" border-collapse:collapse;");
		sb.append("width:90%;");
		sb.append("}");
		sb.append("th, td {");
		sb.append(" border:1px solid black;");
		sb.append(" width:20%;");
		sb.append(" }");
		sb.append("td {");
		sb.append(" text-align:center;");
		sb.append(" }");
		sb.append("caption {");
		sb.append(" font-weight:bold");
		sb.append(" }");
		sb.append("</style>");
		sb.append("</head>");
		sb.append("<body>");
		sb.append("<table border=\"1\">");
		sb.append("<CAPTION>Osm2xp obj export report </CAPTION>");
		sb.append("<TR>");
		sb.append("<TH> object file </TH>");
		sb.append("<TH> latitude </TH>");
		sb.append("<TH> longitude</TH>");
		sb.append("<TH> OpenStreetMap link </TH>");
		sb.append("<TH> GoogleMap link </TH>");
		sb.append(" </TR>");
		for (Entry<Long, Point2D> entry : objectsMap.entrySet()) {
			sb.append("<tr>");
			sb.append("<td>");
			sb.append(String.valueOf(entry.getKey()) + ".obj");
			sb.append("</td>");
			sb.append("<td>");
			sb.append(String.valueOf(entry.getValue().y));
			sb.append("</td>");
			sb.append("<td>");
			sb.append(String.valueOf(entry.getValue().x));
			sb.append("</td>");
			sb.append("<td>");
			sb.append("<a href=" + OPENSTREETMAP_WAY_URL + entry.getKey()
					+ " TARGET=_BLANK><img src=\"" + OPENSTREETMAP_LOGO
					+ "\"/></a>");
			sb.append("</td>");

			sb.append("<td>");
			sb.append("<a href=" + GOOGLEMAPS_URL + entry.getValue().y + ","
					+ entry.getValue().x + "&num=1&t=h&vpsrc=0&z=20"
					+ "\" TARGET=_BLANK><img src=" + GOOGLEMAPS_LOGO + "></a>");
			sb.append("</td>");

			sb.append("</tr>");
		}
		sb.append("</table>");
		sb.append("</body>");
		sb.append("</html>");

		FilesUtils.writeTextToFile(new File(folderPath + File.separator
				+ "report.html"), sb.toString(), false);

	}

	@Override
	public Boolean mustStoreNode(Node node) {
		Boolean result = true;
		if (!GuiOptionsHelper.getOptions().isSinglePass()) {
			result = GeomUtils.compareCoordinates(currentTile, node);
		}
		return result;
	}

	@Override
	public Boolean mustStoreWay(Way way) {
		return null;
	}

	@Override
	public void processBoundingBox(HeaderBBox bbox) {
		// Do nothing
	}
}
