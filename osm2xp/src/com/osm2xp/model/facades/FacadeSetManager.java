package com.osm2xp.model.facades;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.osm2xp.exceptions.Osm2xpTechnicalException;
import com.osm2xp.gui.Activator;
import com.osm2xp.translators.BuildingType;
import com.osm2xp.utils.DsfUtils;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.MiscUtils;
import com.osm2xp.utils.helpers.FacadeSetHelper;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;

public class FacadeSetManager {
	
	public static final String FACADE_SETS_PROP = "facadeSets";
	
	protected Multimap<BuildingType, Facade> buildingFacades = HashMultimap.create();
	
	protected Multimap<BarrierType, Facade> barrierFacades = HashMultimap.create();

	public FacadeSetManager(File targetFolder) {
		List<FacadeSet> list = new ArrayList<FacadeSet>();
		String[] setPaths = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).get(FACADE_SETS_PROP,"").split(File.pathSeparator);
		for (String pathStr : setPaths) {
			File folder = new File(pathStr);
			if (pathStr.endsWith(FacadeSetHelper.FACADE_SET_DESCRIPTOR_FILE_NAME)) {
				folder = folder.getParentFile();
			}
			if (XplaneOptionsHelper.getOptions().isPackageFacades()
					&& XplaneOptionsHelper.getOptions().isGenerateBuildings()) {
				copyFacades(folder, targetFolder);
			}
			if (folder.isDirectory()) {
				list.add(FacadeSetHelper.getFacadeSet(folder.getAbsolutePath()));
			}
		}
		list.stream().flatMap(set -> set.getFacades().stream()).forEach(facade -> addFacade(facade));
	}

	protected void copyFacades(File srcFolder, File targetFolder) {
		if (srcFolder.isFile()) {
			srcFolder = srcFolder.getParentFile();
		}
		File facadesFolder = new File(targetFolder, "facades");
		facadesFolder.mkdirs();

		try {
			FilesUtils.copyDirectory(srcFolder, facadesFolder, false);
			DsfUtils.applyFacadeLod(targetFolder);
			if (!XplaneOptionsHelper.getOptions().isHardBuildings()) {
				DsfUtils.removeConcreteRoofsAndWalls(targetFolder);
			}
		} catch (FileNotFoundException e) {
			throw new Osm2xpTechnicalException(e);
		} catch (IOException e) {
			throw new Osm2xpTechnicalException(e);
		}
	}

	protected void addFacade(Facade facade) {
		BarrierType barrierType = facade.getBarrierType(); //If barrier
		if (barrierType != null) {
			barrierFacades.put(barrierType, facade);
			return;
		}
		if (facade.isResidential()) {
			buildingFacades.put(BuildingType.RESIDENTIAL, facade);
		}
		if (facade.isCommercial()) {
			buildingFacades.put(BuildingType.COMMERCIAL, facade);
		}
		if (facade.isIndustrial()) {
			buildingFacades.put(BuildingType.INDUSTRIAL, facade);
		}
		if (!facade.isCommercial() && !facade.isIndustrial() && !facade.isResidential()) { //Residential by default
			buildingFacades.put(BuildingType.RESIDENTIAL, facade);
		}
	}
	
	public Facade getRandomFacade(BuildingType type, int height, boolean simple) {
		Collection<Facade> facades = buildingFacades.get(type);
		if (facades != null && !facades.isEmpty()) {
			List<Facade> matching = facades.stream()
					.filter(facade -> 
					facade.getMaxHeight() == 0 || (facade.getMinHeight() <= height && facade.getMaxHeight() >= height))
					.filter(facade -> !simple ? !facade.isSimpleBuildingOnly() : true)
					.collect(Collectors.toList());
			if (matching.size() > 0) {
				Collections.shuffle(matching);
				return matching.get(0);
			}
		}
		Activator.log(IStatus.WARNING, "Unable to find proper facade for building type " + type + " height= " + height + " simple= " + simple);
		if (facades.size() > 0) {
			Facade[] array = facades.toArray(new Facade[0]);
			return array[new Random().nextInt(array.length)];
		}
		Collection<Facade> values = buildingFacades.values();
		return values.iterator().next();
	}
	
	public List<String> getAllFacadeStrings() {
		List<String> facadeStrings = barrierFacades.values().stream().distinct().map(facade -> facade.getFile()).sorted().collect(Collectors.toList());
		facadeStrings.addAll(buildingFacades.values().stream().distinct().map(facade -> facade.getFile()).sorted().collect(Collectors.toList()));
		return facadeStrings;
	}
	
	public Facade getRandomBarrierFacade(BarrierType barrierType) {
		if (barrierFacades.isEmpty()) {
			return null;
		}
		Collection<Facade> facades = barrierFacades.get(barrierType);
		if (facades.isEmpty()) {
			facades = barrierFacades.values();
		}
		Facade[] facadeArray = facades.toArray(new Facade[0]);
		return (facadeArray[new Random().nextInt(facades.size())]);
	}
	
	public Facade getRandomHouseSlopedFacade(BuildingType buildingType, double minVector, double height,
			Color buildingColor) {

		
		// find facades which are good in terms of vector size for the sloped
		// roof
		Collection<Facade> goodFacades = buildingFacades.get(buildingType);
		if (goodFacades == null || goodFacades.isEmpty()) { //Use all facades if no facades for this type are registered
			goodFacades = buildingFacades.values();
		}
		List<Facade> resList = goodFacades.stream().filter(facade -> facade.isSloped() && (facade.getMinVectorLength() <= minVector && facade
						.getMaxVectorLength() >= minVector)).collect(Collectors.toList());
		Collections.shuffle(resList);
		if (buildingColor == null && resList.size() > 0) {
			return resList.get(0);
		}
//		if (this.facadeSetManager != null) {
//			List<Facade> slopedFacades = this.getSlopedHousesList();
//			Collections.shuffle(slopedFacades);
//			for (Facade facade : slopedFacades) {
//				if ((facade.getMinVectorLength() <= minVector && facade
//						.getMaxVectorLength() >= minVector)) {
//					goodFacades.add(facade);
//				}
//			}
//		}

		// now pick the one with the roof color closest the building one

		Facade facadeResult = null;
		Double colorDiff = null;

		for (Facade facade : resList) {
			// only look at facades with roof color information
			if (StringUtils.isNotBlank(facade.getRoofColor())) {
				// create a color object
				String rgbValues[] = facade.getRoofColor().split(",");
				Color currentFacadeRoofColor = new Color(
						Integer.parseInt(rgbValues[0]),
						Integer.parseInt(rgbValues[1]),
						Integer.parseInt(rgbValues[2]));
				// compute the difference beetween building roof color and
				// facade roof color
				Double colorDifference = MiscUtils.colorDistance(buildingColor,
						currentFacadeRoofColor);

				// store current Facade if good
				if (colorDiff == null || (colorDiff > colorDifference)) {
					colorDiff = colorDifference;
					facadeResult = facade;
				}
			}
		}

		return facadeResult;
	}


}