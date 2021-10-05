package com.osm2xp.utils.osm;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.onpositive.classification.core.util.TagUtil;
import com.osm2xp.core.constants.CoreConstants;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.rules.ForestTagRule;
import com.osm2xp.generation.options.rules.TagsRule;
import com.osm2xp.generation.options.rules.XplaneObjectTagRule;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.utils.MiscUtils;

/**
 * OsmUtils.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class OsmUtils {

	/**
	 * @param value
	 * @param tags
	 * @return
	 */
	public static boolean isStringInTags(String value, List<Tag> tags) {
		for (Tag tag : tags) {
			if (tag.getKey().toLowerCase().contains(value)
					|| tag.getValue().toLowerCase().contains(value)) {
				return true;

			}
		}
		return false;
	}

	/**
	 * extract roof color information from a list of tags
	 * 
	 * @param tagsList
	 * @return
	 */
	public static Color getRoofColorFromTags(List<Tag> tagsList) {
		Color result = null;
		for (Tag tag : tagsList) {
			if (tag.getKey().contains("roof:color")) {
				try {
					result = Color.decode(tag.getValue());
				} catch (Exception e) {
					// Osm2xpLogger
					// .warning("Failed to extract color information for value "
					// + tag.getValue());
				}
			}
		}
		return result;
	}

	/**
	 * return the first tag of first list that is also in list2
	 * 
	 * @param tagsList1
	 * @param tagsList2
	 * @return
	 */
	public static Tag getMatchingTag(List<Tag> tagsList1, List<Tag> tagsList2) {

		for (Tag tag : tagsList1) {
			if (isTagInTagsList(tag.getKey(), tag.getValue(), tagsList2)) {
				return tag;
			}
		}
		return null;

	}

	/**
	 * return the first tag of first list that is also in list2
	 * 
	 * @param tagsList1
	 * @param tagsList2
	 * @return
	 */
	public static List<Tag> getMatchingTags(List<Tag> tagsList,
			OsmPolyline osmPolygon) {

		List<Tag> result = new ArrayList<Tag>();
		for (Tag userTag : tagsList) {
			for (Tag polygonTag : osmPolygon.getTags()) {
				// first check if the current tag is looking for a osm polygon
				// id
				// check
				if (userTag.getKey().equalsIgnoreCase("id")
						&& userTag.getValue().equalsIgnoreCase(
								String.valueOf(osmPolygon.getId()))) {
					result.add(polygonTag);
				}
				// if tags are matching
				if ((userTag.getKey().equalsIgnoreCase(polygonTag.getKey()) && userTag
						.getValue().equalsIgnoreCase(polygonTag.getValue()))
						|| (userTag.getKey().equalsIgnoreCase("*") && userTag
								.getValue().equalsIgnoreCase(
										polygonTag.getValue()))
						|| (userTag.getKey().equalsIgnoreCase(
								polygonTag.getKey()) && userTag.getValue()
								.equalsIgnoreCase("*"))

				) {
					// if tag is building="yes"
					if (polygonTag.getKey().equalsIgnoreCase("building")
							&& polygonTag.getValue().equalsIgnoreCase("yes")) {
						// only add the tag if there isn't a "wall=no" tag
						if (isBuildingWithWalls(osmPolygon.getTags())) {
							result.add(polygonTag);
						}
					} else
					// if isn't a building=yes tag, add this tag whatever
					{
						result.add(polygonTag);
					}
				}
			}
		}
		if (result.isEmpty()) {
			result = null;
		}
		return result;

	}

	/**
	 * return the first tag of first list that is also in list2
	 * 
	 * @param tagsList1
	 * @param tagsList2
	 * @return
	 */
	public static List<TagsRule> getMatchingRules(
			List<? extends TagsRule> tagsRulesList, OsmPolyline osmPolygon) {

		List<TagsRule> result = new ArrayList<TagsRule>();
		for (TagsRule tagsRules : tagsRulesList) {
			for (Tag polygonTag : osmPolygon.getTags()) {
				// first check if the current tag is looking for a osm polygon
				// id
				// check
				if (tagsRules.getTag().getKey().equalsIgnoreCase("id")
						&& tagsRules
								.getTag()
								.getValue()
								.equalsIgnoreCase(
										String.valueOf(osmPolygon.getId()))) {
					result.add(tagsRules);
					break;
				}
				// if tags are matching
				if (tagsRules.getTag().getKey()
						.equalsIgnoreCase(polygonTag.getKey())
						&& tagsRules.getTag().getValue()
								.equalsIgnoreCase(polygonTag.getValue())) {
					// if tag is building="yes"
					if (tagsRules.getTag().getKey()
							.equalsIgnoreCase("building")
							&& tagsRules.getTag().getValue()
									.equalsIgnoreCase("yes")) {
						// only add the tag if there isn't a "wall=no" tag
						if (isBuildingWithWalls(osmPolygon.getTags())) {
							result.add(tagsRules);
						}
					} else
					// if isn't a building=yes tag, add this tag whatever
					{
						result.add(tagsRules);
					}
				}
			}
		}
		if (result.isEmpty()) {
			result = null;
		}
		return result;

	}

	private static boolean isBuildingWithWalls(List<Tag> tagsList) {
		Boolean result = true;
		for (Tag tag : tagsList) {
			if (tag.getKey().equalsIgnoreCase("wall")
					&& tag.getValue().equalsIgnoreCase("no")) {
				result = false;
				break;
			}
		}
		return result;
	}

	public static boolean isTagInTagsList(String key, String value,
			List<Tag> tags) {
		for (Tag tag : tags) {
			if ((tag.getKey().equalsIgnoreCase(key) && tag.getValue()
					.equalsIgnoreCase(value))
					|| (value.equalsIgnoreCase("*") && tag.getValue()
							.equalsIgnoreCase(value))
					|| (tag.getKey().equalsIgnoreCase(key) && value
							.equalsIgnoreCase("*"))) {
				return true;

			}
		}
		return false;
	}

	public static boolean isExcluded(List<Tag> tags, Long id) {
		for (Tag tag : tags) {
			for (Tag userTag : XPlaneOptionsProvider.getOptions()
					.getBuildingsExclusions().getExclusions()) {
				if ((userTag.getKey().equalsIgnoreCase("id") && userTag
						.getValue().equalsIgnoreCase(String.valueOf(id)))
						|| (OsmUtils.compareTags(userTag, tag))) {
					return true;
				}
			}
		}
		return false;

	}

	public static boolean isValueInTags(String value, List<Tag> tags) {
		for (Tag tag : tags) {
			if (value.equalsIgnoreCase(tag.getValue())) {
				return true;
			}
		}
		return false;
	}
	
	public static String getTagValue(String tagKey, List<Tag> tags) {
		Optional<Tag> first = tags.stream().filter(tag -> tagKey.equals(tag.getKey())).findFirst();
		return first.isPresent() ? first.get().getValue() : null;
	}
	
	public static boolean isKeyInTags(String key, List<Tag> tags) {
		for (Tag tag : tags) {
			if (key.equalsIgnoreCase(tag.getKey())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isObject(List<Tag> tags) {
		for (Tag tag : tags) {
			for (XplaneObjectTagRule objectTagRule : XPlaneOptionsProvider
					.getOptions().getObjectsRules().getRules()) {
				if (compareTags(objectTagRule.getTag(), tag)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isForest(List<Tag> tags) {
		for (Tag tag : tags) {
			for (ForestTagRule forestTagsRule : XPlaneOptionsProvider
					.getOptions().getForestsRules().getRules()) {
				if (compareTags(forestTagsRule.getTag(), tag)) {
					return true;
				}

			}
		}
		return false;
	}

	public static boolean isOsmForest(List<Tag> tags) {
		Boolean result = false;
		for (Tag tag : tags) {
			if (tag.getKey().toLowerCase().contains("forest")
					|| tag.getValue().toLowerCase().contains("forest")
					|| tag.getKey().toLowerCase().contains("wood")
					|| tag.getValue().toLowerCase().contains("wood"))
				result = true;
			break;

		}

		return result;
	}

	/**
	 * Remove common tags such as source, name, etc...
	 * 
	 * @param tags
	 *            source tags
	 * @return List of tags
	 */
	public static List<Tag> removeCommonTags(List<Tag> tags) {
		List<Tag> result = new ArrayList<Tag>();
		for (Tag tag : tags) {
			if (!tag.getKey().toLowerCase().contains("name")
					&& !tag.getKey().toLowerCase().contains("source")
					&& !tag.getKey().toLowerCase().contains("note")
					&& !tag.getKey().toLowerCase().contains("url")
					&& !tag.getKey().toLowerCase().contains("ref")
					&& !tag.getKey().toLowerCase().contains("created")) {
				result.add(tag);
			}
		}
		return result;

	}

	public static boolean compareTags(Tag userTag, Tag tag) {
		return userTag.getKey().equalsIgnoreCase(tag.getKey())
				&& userTag.getValue().equalsIgnoreCase(tag.getValue());
	}
	
	public static boolean isRailway(List<Tag> tags) {
		return OsmUtils.isStringInTags("railway", tags);
	}
	
	public static boolean isRoad(List<Tag> tags) {
		return isStringInTags("highway", tags);
	}
	
	public static boolean isPowerline(List<Tag> tags) {
		return isTagInTagsList("power","line", tags);
	}
	
	public static boolean isFence(List<Tag> tags) {
		return isStringInTags("barrier", tags);
	}
	
	public static boolean isManMade(List<Tag> tags) {
		return isKeyInTags("man_made", tags);
	}
	
	public static boolean isAeroway(List<Tag> tags) {
		return isKeyInTags("aeroway", tags);
	}

	public static boolean isBuilding(List<Tag> tags) {
		String part = TagUtil.getValue("building:part",tags);
		return isStringInTags("building", tags) && 
				!isTagInTagsList("wall","no", tags) && 
				("no".equalsIgnoreCase(part) || part == null);
	}

	public static String getNormalizedTagText(Tag tag) {
		if (!tag.getKey().toLowerCase().contains("source")
				&& !tag.getKey().toLowerCase().contains("name")
				&& !tag.getKey().toLowerCase().contains("addr")
				&& !tag.getKey().toLowerCase().contains("description")
				&& !tag.getKey().toLowerCase().contains("fixme")
				&& !tag.getKey().toLowerCase().contains("todo")) {
			String value = tag.getValue().replace("&", "&amp;");
			value = value.replace("\"", "&quot;");
			value = value.replace("\'", "&apos;");
			value = value.replace("<", "&gt;");
			value = value.replace(">", "&apos;");
			String temp = Normalizer.normalize(value, Normalizer.Form.NFD);
			Pattern pattern = Pattern
					.compile("\\p{InCombiningDiacriticalMarks}+");
			value = pattern.matcher(temp).replaceAll("");
			tag.setValue(Normalizer.normalize(tag.getValue(),
					Normalizer.Form.NFKD));
			return "<tag k=\"" + tag.getKey() + "\" v=\"" + value + "\"/>\n";
		}
		return null;

	}

	/**
	 * get height information from osm tags.
	 * 
	 * @param tags
	 * @return the height in meter.
	 */
	public static int getHeightFromTags(List<Tag> tags) {
		for (Tag tag : tags) {
			if (!tag.getKey().toLowerCase().contains("max")
					&& !tag.getKey().toLowerCase().contains("min")) {
				if (tag.getKey().toLowerCase().contains("height")
						&& tag.getValue().length() < 11) {
					Double height = MiscUtils.extractNumber(tag.getValue());
					if (height != null && height < 800 && height > 2) {
						return (int) Math.round(height);
					}
				}
				if ("building:levels".equalsIgnoreCase(tag.getKey())) {
					try {
						int levels = Integer.parseInt(tag.getValue().trim());
						return (int) Math.round(levels * GlobalOptionsProvider.getOptions().getLevelHeight());
					} catch (NumberFormatException e) {
						//Best effort
					}
				}
				if (tag.getKey().toLowerCase().contains("level")
						&& !tag.getValue().contains("-1")
						&& tag.getValue().length() < 5) {
					Double levels = MiscUtils.extractNumber(tag.getValue());
					if (levels != null) {
						int height =  (int) Math.round(levels * GlobalOptionsProvider.getOptions().getLevelHeight());
						if (height < 800 && height > 2) {
							return height;
						}
					}
				}
			}
		}
		return 0;
	}
	
	public static String getReadableType(List<Tag> tags) {
		if (isBuilding(tags)) {
			return "building";
		}
		if (isManMade(tags)) {
			return "man_made";
		}
		if (isForest(tags) || isOsmForest(tags)) {
			return "forest";
		}
		if (isFence(tags)) {
			return "fence";
		}
		if (isObject(tags)) {
			return "object";
		}
		if (isPowerline(tags)) {
			return "powerline";
		}
		if (isRailway(tags)) {
			return "railway";
		}
		if (isRoad(tags)) {
			return "road";
		}
		if (isAeroway(tags)) {
			return "aeroway";
		}
		return "unknown";
	}

	public static String CreateTempFile(String folderPath,
			List<OsmPolygon> wayList, String fileName) throws IOException {

		String filePath = folderPath + File.separator + fileName + ".osm";

		try (BufferedWriter output = new BufferedWriter(new FileWriter(filePath, false))){
			output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			output.write("<osm version=\"0.6\" generator=\"osm2xp "
					+ CoreConstants.OSM2XP_VERSION + "\">\n");
	
			// write all nodes
			for (OsmPolyline osmPolygon : wayList) {
				for (Node node : osmPolygon.getNodes()) {
					output.write("<node id=\"" + node.getId() + "\" lat=\""
							+ node.getLat() + "\" lon=\"" + node.getLon()
							+ "\" version=\"1\" />\n");
				}
	
			}
	
			for (OsmPolyline osmPolygon : wayList) {
				output.write("<way id=\"" + osmPolygon.getId()
						+ "\" visible=\"true\" version=\"2\" >\n");
				for (Node node : osmPolygon.getNodes()) {
					output.write("<nd ref=\"" + node.getId() + "\"/>\n");
				}
				for (Tag tag : osmPolygon.getTags()) {
					String normalizedTag = getNormalizedTagText(tag);
					if (normalizedTag != null) {
						output.write(normalizedTag);
					}
	
				}
				output.write("</way>\n");
			}
			output.write("</osm>");
			output.flush();
		} catch (IOException e) {
			Osm2xpLogger.log(e);
		}
		new File(filePath).deleteOnExit();
		return filePath;
	}

	public static String CreateTempFile(String folderPath, OsmPolyline osmPolygon)
			throws IOException {

		String filePath = folderPath + File.separator + osmPolygon.getId()
				+ ".osm";
		try (BufferedWriter output = new BufferedWriter(new FileWriter(filePath, false))){
			output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			output.write("<osm version=\"0.6\" generator=\"osm2xp "
					+ CoreConstants.OSM2XP_VERSION + "\">\n");
	
			for (Node node : osmPolygon.getNodes()) {
				output.write("<node id=\"" + node.getId() + "\" lat=\""
						+ node.getLat() + "\" lon=\"" + node.getLon()
						+ "\" version=\"1\" />\n");
			}
	
			output.write("<way id=\"" + osmPolygon.getId()
					+ "\" visible=\"true\" version=\"2\" >\n");
			for (Node nd : osmPolygon.getNodes()) {
				output.write("<nd ref=\"" + nd.getId() + "\"/>\n");
			}
			for (Tag tag : osmPolygon.getTags()) {
				String normalizedTag = getNormalizedTagText(tag);
				if (normalizedTag != null) {
					output.write(normalizedTag);
				}
	
			}
			output.write("</way>\n");
	
			output.write("</osm>");
			output.flush();
		} catch (IOException e) {
			Osm2xpLogger.log(e);
		}
		new File(filePath).deleteOnExit();
		return filePath;
	}
	
	public static boolean isValidICAO(String name) {
		if (name == null) {
			return false;
		}
		name = name.toUpperCase().trim();
		if (name.length() == 4) {
			for (int i = 0; i < name.length(); i++) {
				char c = name.charAt(i);
				if ((c < 'A' || c > 'Z') && !Character.isDigit(c)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}
