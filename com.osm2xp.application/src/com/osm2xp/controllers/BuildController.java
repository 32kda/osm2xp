package com.osm2xp.controllers;

import java.io.File;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.osm2xp.constants.Perspectives;
import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.gui.Activator;
import com.osm2xp.gui.handlers.modes.CommandXplane10Mode;
import com.osm2xp.gui.views.MainSceneryFileView;
import com.osm2xp.jobs.GenerateJob;
import com.osm2xp.jobs.GenerateTilesJob;
import com.osm2xp.jobs.GenerateWholeFileJob;
import com.osm2xp.jobs.MutexRule;
import com.osm2xp.model.facades.FacadeSetManager;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.ITranslatorProvider;
import com.osm2xp.translators.TranslatorBuilder;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.ui.UiUtil;

/**
 * Build controller.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class BuildController {
	
	private static String mode = CommandXplane10Mode.MODE;
	
	private String folderPath;
	private MutexRule rule = new MutexRule();

	/**
	 * @throws Osm2xpBusinessException
	 */
	public void launchBuild() throws Osm2xpBusinessException {
		File currentFile = getSelectedFile();
		if (currentFile == null) {
			return;
		}
		// if choosen output mode will generate file, first check that user is
		// ok to overwrite file is present.
		boolean deleteFolder = false;
		if (GuiOptionsHelper.isOutputFormatAFileGenerator(mode)) {
			folderPath = currentFile.getParent() + File.separator
					+ GuiOptionsHelper.getSceneName();

			File folder = new File(folderPath);
			if (folder.exists()) {				
				deleteFolder = checkDeleteFolder(folder);
				if (!deleteFolder) {
					return;
				}
			}
		} 
		startGeneration(currentFile, deleteFolder);
		
	}

	public static File getSelectedFile() {
		String currentFilePath = GlobalOptionsProvider.getOptions()
				.getCurrentFilePath();
		String path = StringUtils.stripToEmpty(currentFilePath).trim();
		if (path.isEmpty()) {
			MessageDialog.openError(Display.getDefault().getActiveShell(),"No file specified", "No file with OSM data (*.pbf, *.osm...) specified. Please choose valid file");
			try {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(MainSceneryFileView.ID);
			} catch (PartInitException e) {
				Activator.log(e);
			}
			return null;
		}
		File currentFile = new File(path);
		if (!currentFile.isFile()) {
			MessageDialog.openError(Display.getDefault().getActiveShell(),"Invalid file specified", "Can't open OSM data file " + path + ". Please check this file exists");
			try {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(MainSceneryFileView.ID);
			} catch (PartInitException e) {
				Activator.log(e);
			}
			return null;
		}
		return currentFile;
	}
	
	public static boolean checkDeleteFolder(File currentFolder) {
		if (!currentFolder.exists()) {
			return false;
		}
		return MessageDialog.openConfirm(Display.getDefault()
						.getActiveShell(), "Confirm", currentFolder.getAbsolutePath()
						+ " already exists, delete?");
	}

	/**
	 * @param currentFile
	 * @param deleteFolder 
	 * @throws Osm2xpBusinessException
	 */
	private void startGeneration(File currentFile, boolean deleteFolder)
			throws Osm2xpBusinessException {
		// delete existing file if exists
		StatsProvider.reinit();
		// switch to build perspective
		UiUtil.switchPerspective(Perspectives.PERSPECTIVE_BUILD);
		// get user setted cordinates
		FacadeSetManager.clearCache();
		generateWholeFile(currentFile, folderPath, deleteFolder);

//		new ParsingExperimentJob(currentFile).schedule(); //Experimental to check new osmosis API
	}


	/**
	 * @param currentFile
	 * @param folderPath
	 * @param deleteFolder 
	 */
	private void generateWholeFile(final File currentFile,
			final String folderPath, boolean deleteFolder) {
		Job tilesJob = new Job("Preparing for generation") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (deleteFolder) {
					Osm2xpLogger.info("Deleting folder " + folderPath);
					FilesUtils.deleteDirectory(new File(folderPath));
				}
				
				GenerateJob tilesJob = getGenerationJob(currentFile, folderPath);
				
				tilesJob.setRule(new MutexRule());
				tilesJob.addJobChangeListener(new JobChangeAdapter() {


					@Override
					public void done(IJobChangeEvent event) {
						tilesJob.setFamily("endedJob");
						Display.getDefault().asyncExec(new Runnable() {
							@Override
							public void run() {
								if ((Job.getJobManager().find("todoJob")).length == 0) {
									
									Osm2xpLogger.info("Generation finished.");
									
									// MiscUtils.switchPerspective(GuiOptionsHelper
									// .getOptions().getOutputFormat());

								}
							}
						});

					}
					
				});

				tilesJob.setRule(rule);
				tilesJob.schedule();
				
//				if (tilesList.isEmpty()) {
//					try {
//						GlobalOptionsProvider.getOptions().setSinglePass(true);
//						generateWholeFileOnASinglePass(currentFile, folderPath);
//					} catch (Osm2xpBusinessException e) {
//						Osm2xpLogger.error("Error generating tile", e);
//						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e);
//					}
//				}
				return Status.OK_STATUS;

			}
		};

		tilesJob.schedule();

	}
	
	private GenerateJob getGenerationJob(final File currentFile,
			final String folderPath) {
		ITranslatorProvider translatorProvider = TranslatorBuilder.getTranslatorProvider(currentFile, folderPath, mode);
		if (translatorProvider != null) {
			return new GenerateTilesJob(currentFile, folderPath, translatorProvider);
		} 
		ITranslator translator= TranslatorBuilder.getTranslator(currentFile, null, folderPath, mode);
		if (translator != null) {
			return new GenerateWholeFileJob(currentFile, folderPath, translator);
		}
		return null;
	}

	public static String getGenerationMode() {
		return mode;
	}

	public static void setGenerationMode(String mode) {
		BuildController.mode = mode;
	}
}
