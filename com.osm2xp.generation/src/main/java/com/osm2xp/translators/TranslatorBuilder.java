package com.osm2xp.translators;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.osm2xp.core.parsers.IOSMDataVisitor;
import com.osm2xp.translators.airfield.XPAirfieldGeneratorFactory;
import com.osm2xp.translators.xplane.XP10TranslatorProviderFactory;
import com.osm2xp.translators.xplane.XP9TranslatorProviderFactory;

import math.geom2d.Point2D;

/**
 * TranslatorBuilder.
 * 
 * @author Dmitry Karpenko 
 * @author Benjamin Blanchet
 * 
 */
public class TranslatorBuilder {
	
	private static Map<String, ITranslatorFactory> translatorFactories = new HashMap<>();
	
	static {
		registerFactory(new XP10TranslatorProviderFactory());
		registerFactory(new XP9TranslatorProviderFactory());
		registerFactory(new XPAirfieldGeneratorFactory());
		registerFactory(new ConsoleTranslatorFactory());
		registerFactory(new G2XPLTranslatorFactory());
		registerFactory(new OSMTranslatorFactory());
		registerFactory(new FlightGearTranslatorFactory());
	}

	public static void registerFactory(ITranslatorFactory factory) {
		translatorFactories.put(factory.getOutputMode(), factory);
	}
	
	public static ITranslator getTranslator(File currentFile,
			Point2D currentTile, String folderPath, String outputFormat) {
		ITileTranslatorFactory factory = getTileTranslatorFactory(outputFormat);
		if (factory != null) {
			return factory.getTranslator(currentFile,currentTile, folderPath);
		}
		return null;
	}
	
	private static ITileTranslatorFactory getTileTranslatorFactory(String outputFormat) {
		ITranslatorFactory translatorFactory = translatorFactories.get(outputFormat);
		if (translatorFactory instanceof ITileTranslatorFactory) {
			return (ITileTranslatorFactory) translatorFactory;
		}
		return null;
	}
	
	private static ITranslatorProviderFactory getTranslatorProviderFactory(String outputFormat) {
		ITranslatorFactory translatorFactory = translatorFactories.get(outputFormat);
		if (translatorFactory instanceof ITranslatorProviderFactory) {
			return (ITranslatorProviderFactory) translatorFactory;
		}
		return null;
	}
	
	private static IDataVisitorFactory getDataVisitorFactory(String outputFormat) {
		ITranslatorFactory translatorFactory = translatorFactories.get(outputFormat);
		if (translatorFactory instanceof IDataVisitorFactory) {
			return (IDataVisitorFactory) translatorFactory;
		}
		return null;
	}

	public static IOSMDataVisitor getDataVisitor(File currentFile, String folderPath, String outputFomat) {
		 IDataVisitorFactory factory = getDataVisitorFactory(outputFomat);
		 if (factory != null) {
			return factory.getVisitor(folderPath);
		}
		return null;
	}

	public static ITranslatorProvider getTranslatorProvider(File currentFile, String folderPath, String generationMode) {
		ITranslatorProviderFactory factory = getTranslatorProviderFactory(generationMode);
		if (factory != null) {
			return factory.getTranslatorProvider(currentFile, folderPath);
		}
		return null;
	}

	public static String getRegisteredFormatsStr() {
		List<String> list = new ArrayList<String>(translatorFactories.keySet());
		list.addAll(translatorFactories.keySet());
		return list.stream().distinct().sorted().collect(Collectors.joining(", "));
	}
	
	public static boolean isSupported(String format) {
		return translatorFactories.containsKey(format);
	}
	
	public static boolean isFileWriting(String format) {
		ITranslatorFactory factory = translatorFactories.get(format);
		if (factory != null) {
			return factory.isFileWriting();
		}
		return false;
	}

}
