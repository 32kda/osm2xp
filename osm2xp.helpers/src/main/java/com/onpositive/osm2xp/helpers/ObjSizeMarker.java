package com.onpositive.osm2xp.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.osm2xp.core.logging.Osm2xpLogger;

public class ObjSizeMarker {
	
	private static final Pattern FULL_MARKING_PATTERN = Pattern.compile("([\\d]+(\\.[\\d]+)?)x([\\d]+(\\.[\\d]+)?)h([\\d]+(\\.[\\d]+)?)");
	private static final Pattern STRICTED_MARKING_PATTERN = Pattern.compile("([\\d]+(\\.[\\d]+)?)x([\\d]+(\\.[\\d]+)?)");
	
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Model folder should be specified!");
			return;
		}
		File rootFolder = new File(args[0]);
		if (!rootFolder.isDirectory()) {
			System.err.println("Folder " + args[0] + " is not valid folder!");
			return;
		}
		processFolder(rootFolder);		
	}

	private static void processFolder(File folder) {
		File[] listFiles = folder.listFiles();
		for (File file : listFiles) {
			if (file.isDirectory()) {
				processFolder(file);
			} else if (file.getName().endsWith(".obj") && !isMarked(file.getName())) {
				processObjFile(file);
			}			
		}
	}

	private static boolean isMarked(String name) {
		return FULL_MARKING_PATTERN.matcher(name).find();
	}

	private static void processObjFile(File file) {
		try {
			@Nullable
			String head = Files.asCharSource(file, StandardCharsets.UTF_8).readFirstLine();
			if (StringUtils.stripToEmpty(head.trim()).equals("I")) {
				double maxX = Double.MIN_VALUE;
				double minX = Double.MAX_VALUE;
				double maxZ = Double.MIN_VALUE;
				double minZ = Double.MAX_VALUE;
				double maxHeight = Double.MIN_VALUE;
				ImmutableList<String> lines = Files.asCharSource(file, StandardCharsets.UTF_8).readLines();
				for (String line : lines) {
					if (line.startsWith("VT")) {
						String[] parts = line.split("\\s+");
						double[] vals = extractNumbers(parts);
						if (vals[0] > maxX) {
							maxX = vals[0];
						}
						if (vals[0] < minX) {
							minX = vals[0];
						}
						if (vals[2] > maxZ) {
							maxZ = vals[2];
						}
						if (vals[2] < minZ) {
							minZ = vals[2];
						}
						if (vals[1] > maxHeight) {
							maxHeight = vals[1];
						}
					}
				}
				if (maxX > Double.MIN_VALUE) {
					double maxXLen = maxX - minX;
					double maxZLen = maxZ - minZ;
					int x = (int) Math.round(maxXLen);
					int z = (int) Math.round(maxZLen);
					int h = (int) Math.round(maxHeight);
					
					String curName = file.getName();
//					String marking = String.format(Locale.ROOT, "%.1fx%.1fh%.1f", 
//							maxXLen > maxZLen ? maxXLen : maxZLen,
//									maxXLen > maxZLen ? maxZLen : maxXLen,
//											maxHeight);
					String marking = String.format(Locale.ROOT, "%dx%dh%d", x > z ? x : z, x > z ? z : x, h);
					Matcher matcher = STRICTED_MARKING_PATTERN.matcher(curName);
					String newName;
					if (matcher.matches()) {
						newName = matcher.replaceFirst(marking);
					} else {
						int idx = curName.lastIndexOf('.');
						newName = curName.substring(0, idx) + '_' + marking + curName.substring(idx);
					}
					File output = new File(file.getParentFile(), "output");
					output.mkdir();
					FileUtils.copyFile(file, new File(output, newName));
				}
			}
		} catch (IOException e) {
			Osm2xpLogger.log(e);
		}
		
	}

	private static double[] extractNumbers(String[] parts) {
		List<String> numParts = new ArrayList<>();
		for (String part : parts) {
			String trim = part.trim();
			if (trim.length() > 0 && (trim.charAt(0) == '-' || Character.isDigit(trim.charAt(0)))) {
				numParts.add(trim);
			}
		}
		double[] result = new double[numParts.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = Double.parseDouble(numParts.get(i));
		}
		return result;
	}
}
