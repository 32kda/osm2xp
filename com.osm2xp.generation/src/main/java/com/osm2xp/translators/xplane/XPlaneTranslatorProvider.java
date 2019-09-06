package com.osm2xp.translators.xplane;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import com.osm2xp.core.parsers.IOSMDataVisitor;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.generation.areas.LanduseAreasAnalyzer;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.model.facades.FacadeSetManager;
import com.osm2xp.translators.AbstractTranslatorProvider;
import com.osm2xp.translators.IPreprocessorProvider;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.impl.ImageDebugTranslationListener;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.DsfUtils;
import com.osm2xp.writers.IHeaderedWriter;
import com.osm2xp.writers.impl.DsfWriterImpl;

import math.geom2d.Point2D;

public abstract class XPlaneTranslatorProvider extends AbstractTranslatorProvider implements IPreprocessorProvider {

	protected String facadeSetsStr;
	protected DsfObjectsProvider dsfObjectsProvider;

	public XPlaneTranslatorProvider(File binaryFile, String folderPath) {
		super(binaryFile, folderPath);
		facadeSetsStr = XPlaneOptionsProvider.getOptions().getFacadeSets();
		if (facadeSetsStr == null) {
			facadeSetsStr = XPlaneOptionsProvider.getDefaultFacadeSets();
		}
		dsfObjectsProvider = new DsfObjectsProvider(folderPath, FacadeSetManager.getManager(facadeSetsStr, new File(folderPath)));		
	}

	@Override
	public ITranslator getTranslator(Point2D currentTile) {
		// create stats folder
//				if (XPlaneOptionsProvider.getOptions().isGeneratePdfStats()
//						|| XPlaneOptionsProvider.getOptions().isGenerateXmlStats()) {
//					new File(folderPath + File.separatorChar + "stats").mkdirs();
//				}

		// write the library file if needed
		if (!XPlaneOptionsProvider.getOptions().isPackageFacades()) {
			DsfUtils.writeLibraryFile(folderPath, dsfObjectsProvider);
		}
		
		IHeaderedWriter writer = new DsfWriterImpl(folderPath, currentTile, XPlaneOptionsProvider.getOptions().isDeleteSrc());
		XPlaneTranslatorImpl translatorImpl = createTranslator(currentTile, writer);
		if (XPlaneOptionsProvider.getOptions().isGenerateDebugImg()) {
			translatorImpl.setTranslationListener(new ImageDebugTranslationListener());
		}
		return translatorImpl;
	}

	protected abstract XPlaneTranslatorImpl createTranslator(Point2D currentTile, IHeaderedWriter writer);
	
	@Override
	public Collection<IOSMDataVisitor> createPreprocessors(IDataSink dataSink) {
		return Collections.singletonList(new LanduseAreasAnalyzer(dataSink));
	}

}
