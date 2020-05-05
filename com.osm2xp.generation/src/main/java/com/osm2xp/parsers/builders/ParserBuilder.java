package com.osm2xp.parsers.builders;

import java.io.File;
import java.util.Collection;

import com.osm2xp.converters.impl.AbstractTileDataConverter;
import com.osm2xp.converters.impl.AbstractTranslatingConverter;
import com.osm2xp.converters.impl.GeneralTranslatingConverter;
import com.osm2xp.converters.impl.MultiTileDataConverter;
import com.osm2xp.converters.impl.SingleTileDataConverter;
import com.osm2xp.converters.impl.SpecificTranslatingConverter;
import com.osm2xp.core.exceptions.DataSinkException;
import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.CompositeVisitor;
import com.osm2xp.core.parsers.IOSMDataVisitor;
import com.osm2xp.core.parsers.IParser;
import com.osm2xp.core.parsers.IVisitingParser;
import com.osm2xp.core.parsers.impl.SaxParserImpl;
import com.osm2xp.core.parsers.impl.ShapefileParserImpl;
import com.osm2xp.core.parsers.impl.TranslatingBinaryParser;
import com.osm2xp.datastore.DataSinkFactory;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.translators.IPreprocessorProvider;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.ITranslatorProvider;
import com.osm2xp.translators.airfield.XPAirfieldTranslationAdapter;

import math.geom2d.Point2D;

/**
 * ParserBuilder.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class ParserBuilder {

	/**
	 * Build the parser implementation for the type of file
	 * 
	 * @param currentTile tile to create parser for
	 * @param folderPath
	 * @param outputFormat 
	 * @return
	 * @throws Osm2xpBusinessException
	 * @throws DataSinkException
	 * @throws NumberFormatException
	 * @throws Exception
	 */
//	public static IParser getParser(Point2D currentTile, File currentFile,
//			String folderPath, String outputFormat)
//			throws DataSinkException {
//		ITranslator translator = TranslatorBuilder.getTranslator(currentFile,
//				currentTile, folderPath, outputFormat);
//		// if a roof color file is available, load it into a map and give it to
//		// the parser
//		Map<Long, Color> roofsColorMap = null;
//		if (PathsService.getPathsProvider().getRoofColorFile() != null) {
//			roofsColorMap = FilesUtils.loadG2xplColorFile(PathsService.getPathsProvider()
//					.getRoofColorFile());
//		}
//		IDataSink dataSink = DataSinkFactory.getProcessor();
//		AbstractTranslatingConverter converter = new GeneralTranslatingConverter(translator, dataSink, roofsColorMap);
//		return getParser(currentFile, converter);
//	}
	public static IParser getParser(File currentFile,
			ITranslator translator, IDataSink dataSink)
					throws DataSinkException {	
		AbstractTranslatingConverter converter = new GeneralTranslatingConverter(translator, dataSink);
		return getParser(currentFile, converter);
	}

	public static IParser getParser(File currentFile, IOSMDataVisitor converter) {
		// PBF FILE
		if (currentFile.getName().toLowerCase().endsWith(".pbf")) {
			return new TranslatingBinaryParser(currentFile, converter);
		}
		// OSM FILE
		else if (currentFile.getName().toLowerCase().endsWith(".osm")) {
			return new SaxParserImpl(currentFile, converter);
		}
		// SHP FILE
		else if (currentFile.getName().toLowerCase().endsWith(".shp")) {
			return new ShapefileParserImpl(currentFile, converter, GlobalOptionsProvider.getShapefileTag());
		}
		return null;
	}
	
	public static IParser getXPAirfieldGeneratingParser(File currentFile,
			String folderPath) {
		try {
			return getParser(currentFile, new SpecificTranslatingConverter(new XPAirfieldTranslationAdapter(folderPath), DataSinkFactory.getDataSink()));
		} catch (DataSinkException e) {
			Osm2xpLogger.log(e);
		}
		return null;
	}
	
	
	/**
	 * Build the parser implementation 
	 * 
	 * @return {@link IVisitingParser} instance
	 * @throws Osm2xpBusinessException
	 * @throws DataSinkException
	 */
	public static IVisitingParser getMultiTileParser(File currentFile,ITranslatorProvider translatorProvider, IDataSink dataSink)
		throws DataSinkException {
		AbstractTileDataConverter converter = new MultiTileDataConverter(dataSink, translatorProvider);		
		return (IVisitingParser) getParser(currentFile, converter);
	}
	
	/**
	 * Build the parser implementation for the single tile
	 * 
	 * @return {@link IVisitingParser} instance
	 * @throws Osm2xpBusinessException
	 * @throws DataSinkException
	 */
	public static IVisitingParser getSingleTileParser(File currentFile, ITranslatorProvider translatorProvider, IDataSink dataSink, Point2D tile)
			throws DataSinkException {
		AbstractTileDataConverter converter = new SingleTileDataConverter(dataSink, translatorProvider, tile);		
		return (IVisitingParser) getParser(currentFile, converter);
	}

	public static IVisitingParser getPreprocessParser(File currentFile, ITranslatorProvider translatorProvider, IDataSink dataSink) {
		if (translatorProvider instanceof IPreprocessorProvider) {
			Collection<IOSMDataVisitor> preprocessors = ((IPreprocessorProvider) translatorProvider).createPreprocessors(dataSink);
			if (!preprocessors.isEmpty()) {
				IOSMDataVisitor preprocessor = preprocessors.size() > 1 ? new CompositeVisitor(preprocessors) : preprocessors.iterator().next();
				return (IVisitingParser) getParser(currentFile, preprocessor);
			}
		}
		
		return null;
	}

}
