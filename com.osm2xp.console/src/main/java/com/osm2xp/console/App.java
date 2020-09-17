package com.osm2xp.console;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.osm2xp.converters.impl.AbstractTileDataConverter;
import com.osm2xp.converters.impl.MultiTileDataConverter;
import com.osm2xp.core.exceptions.DataSinkException;
import com.osm2xp.core.parsers.IOSMDataVisitor;
import com.osm2xp.core.parsers.IParser;
import com.osm2xp.core.parsers.IVisitingParser;
import com.osm2xp.datastore.DataSinkFactory;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.generation.preferences.BasicPreferences;
import com.osm2xp.generation.preferences.PreferenceService;
import com.osm2xp.parsers.builders.ParserBuilder;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.ITranslatorProvider;
import com.osm2xp.translators.TranslatorBuilder;
import com.osm2xp.utils.ProcessExecutor;


/**
 * Main console app class
 *
 */
public class App 
{
    private static final String HELP = "help";
	private static final String DBMODE = "dbmode";
	private static final String FACADE_SETS = "facade-sets";
	private static final String CONFIG_FOLDER = "config-folder";
	private static final String SCENERY_NAME = "scenery-name";
	private static final String MODE = "mode";
	private static Options options;

	public static void main( String[] args )
    {
		options = createOptions();
    	if (args.length == 0) {
    		System.out.println("Input file should be specified!");
    		printHelp();
    		return;
    	}
    	if (args.length > 0) {
    		CommandLine commandLine = parseCommandLine(args);
    		if (commandLine == null) {
    			return;
    		}
    		if (commandLine.hasOption(HELP)) {
    			printHelp();
    		}
    		if (commandLine.getArgList().size() < 1) {
    			System.out.println("Input file should be specified");
    		}
    		String inputFileName = commandLine.getArgs()[0].trim();
    		File inputFile = new File(inputFileName);
    		if (!inputFile.isFile()) {
    			System.out.println("Input file " + inputFileName + " is invalid");
    		}
    		String customFolder = commandLine.getOptionValue(CONFIG_FOLDER);
    		if (customFolder != null) {
    			File dir = new File(customFolder);
    			if (!dir.isDirectory()) {
    				System.out.println("Specified folder " + customFolder + " does not exist.");
    				return;
    			}
				PathsService.getPathsProvider().setBasicFolder(dir);
			}
    		
    		String modeStr = commandLine.getOptionValue(MODE);
    		String outputFormat;
    		if (modeStr != null) {
				outputFormat = modeStr.toUpperCase();
			} else {
				outputFormat = "XPLANE10";
			}
    		//TODO no validation for outputFormat. Should this be validated or refactored? 
    		//TODO interpret other args
    		
    		
    		String sceneryName = commandLine.getOptionValue(SCENERY_NAME);
    		if (sceneryName == null) {
    			sceneryName = computeDefaultSceneName(inputFile);
			}
    		if (commandLine.hasOption(DBMODE) && !GlobalOptionsProvider.getOptions().isDatabaseMode()) {
    			GlobalOptionsProvider.getOptions().setDatabaseMode(true);
    		}
    		GlobalOptionsProvider.getOptions().setCurrentFilePath(inputFile.getAbsolutePath());
    		
    		if (!TranslatorBuilder.isSupported(outputFormat)) {
				System.out.println("Output format " + outputFormat + " is invalid. Known formats are: " + TranslatorBuilder.getRegisteredFormatsStr());
				return;
			}
    		
    		File targetFolder = new File(inputFile.getParent(), sceneryName);
    		if (targetFolder.exists()) { //TODO ask for deletion or require special flag ?
    			System.out.println("Target folder " + sceneryName + " exists and will be deleted.");
    			try {
    				FileUtils.deleteDirectory(targetFolder);
    			} catch (IOException e) {
    				System.out.println("Target folder " + sceneryName + " deletion failed, this can cause generation to fail. Reason:");
    				e.printStackTrace();
    			}
    		}
    		
    		if (!loadOptions(outputFormat, commandLine)) {
    			return;
    		}
    		
    		try {
    			long t1 = System.currentTimeMillis();
    			System.out.println("Generation started at "  + new Date());
    			IParser parser = null;
    			ITranslatorProvider translatorProvider = TranslatorBuilder.getTranslatorProvider(inputFile, targetFolder.getAbsolutePath(), outputFormat);
    			IDataSink dataSink = DataSinkFactory.getDataSink();
    			if (translatorProvider != null) {
    				parser = ParserBuilder.getMultiTileParser(inputFile, translatorProvider, dataSink);
    			}
    			if (parser == null) {
					ITranslator translator = TranslatorBuilder.getTranslator(inputFile, null, targetFolder.getAbsolutePath(), outputFormat);
					if (translator != null) {
						parser = ParserBuilder.getParser(inputFile, translator, dataSink);
					}
				}
    			if (parser == null) {
    				IOSMDataVisitor dataVisitor = TranslatorBuilder.getDataVisitor(inputFile, sceneryName, outputFormat);
    				if (dataVisitor != null) {
						parser = ParserBuilder.getParser(inputFile, dataVisitor);
					}
    			}
    			StatsProvider.reinit();
    			
    			parser.process();
    			if (parser instanceof IVisitingParser && ((IVisitingParser) parser).getVisitor() instanceof MultiTileDataConverter) {
    				System.out.println("Finished generation of " +  ((AbstractTileDataConverter) ((IVisitingParser) parser).getVisitor()).getTilesCount() + " tiles, target folder " + sceneryName);
    			} else {
    				System.out.println("Generation finished, target folder " + sceneryName);
    			}
    			ProcessExecutor.getExecutor().shutdown();
    			System.out.println("Generation took " + formatTimeDelta(System.currentTimeMillis() - t1));
    		} catch (DataSinkException e) {
    			e.printStackTrace();
    		}
		}
    }

	protected static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("osm2xpc [input file] [option1] [option2] ...", options);
	}

	private static boolean loadOptions(String outputFormat, CommandLine commandLine) {
		PreferenceService.setProgramPreferences(new BasicPreferences());
		if (outputFormat.toUpperCase().startsWith("XPLANE") || outputFormat.toUpperCase().equals("XP_AIRFIELDS")) {
			
//			XPlaneOptionsProvider.loadDefaultOptions(); //TODO support custom options location
			if (XPlaneOptionsProvider.getOptions() == null) {
				System.out.println("Failed to load X-Plane generation options from file " + XPlaneOptionsProvider.getDefaultOptionsFile());
				System.out.println("It should be present in {current folder}/xplane, please use -c option to specify another folder");
				return false;
			}
			String[] optionValues = commandLine.getOptionValues(FACADE_SETS);
			if (optionValues != null && optionValues.length > 0) {
				XPlaneOptionsProvider.getOptions().setFacadeSets(StringUtils.join(optionValues,';'));
			} else if (XPlaneOptionsProvider.getOptions().getFacadeSets() == null) {
				String defaultFacadeSets = XPlaneOptionsProvider.getDefaultFacadeSets();
				System.out.println("No facade sets configured, will use default: " + defaultFacadeSets);
				XPlaneOptionsProvider.getOptions().setFacadeSets(defaultFacadeSets);
			}
		}
		
		return true;
	}

	private static Options createOptions() {
		Options options = new Options();
		options.addOption( "m", MODE, true, "Generation mode, 'xplane10' by default");
		options.addOption( "s", SCENERY_NAME, true, "scenery name");
		options.addOption( "c", CONFIG_FOLDER, true, "use this folder instead of current one when looking for "
				+ "configuration, resources, tools etc. Can be used to have generation profiles.");
		Option facadeSetsOption = new Option("f", FACADE_SETS, true, "facade sets for X-Plane scenario generation, one or more paths separated with spaces");
		facadeSetsOption.setArgs(Option.UNLIMITED_VALUES);				
		options.addOption(facadeSetsOption);
//		options.addOption( "o", "options", true, "Generation options file for given mode. Will use default if it's not specified");
		options.addOption( "d", DBMODE, false, "Use database mode - will store some data during generation on disk, which allows to process larger input files");
		options.addOption( "h", HELP, false, "Print this information message");
		return options;
	}

	private static String formatTimeDelta(long millis) {
		return String.format("%d min, %d sec", 
			    TimeUnit.MILLISECONDS.toMinutes(millis),
			    TimeUnit.MILLISECONDS.toSeconds(millis) - 
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
			);
	}

	protected static String computeDefaultSceneName(File inputFile) {
		String sceneName = inputFile.getName();
    	int idx = sceneName.lastIndexOf('.');
    	if (idx > 0) {
			sceneName = sceneName.substring(0, idx);
		}
    	return sceneName;
	}
	
	private static CommandLine parseCommandLine(String[] args) {
		CommandLineParser parser = new DefaultParser();
		try {
			return parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println("Invalid command line.");
			e.printStackTrace();
		}
		return null;
	}
}
