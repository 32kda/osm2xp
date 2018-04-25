package com.osm2xp.controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import math.geom2d.Point2D;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import com.osm2xp.constants.Perspectives;
import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.gui.Activator;
import com.osm2xp.gui.views.MainSceneryFileView;
import com.osm2xp.jobs.GenerateTileJob;
import com.osm2xp.jobs.MutexRule;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.project.Coordinates;
import com.osm2xp.parsers.relationsLister.RelationsLister;
import com.osm2xp.parsers.relationsLister.RelationsListerFactory;
import com.osm2xp.parsers.tilesLister.TilesLister;
import com.osm2xp.parsers.tilesLister.TilesListerFactory;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.Osm2xpProjectHelper;
import com.osm2xp.utils.helpers.StatsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;

/**
 * Build controller.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class BuildController {
	private String folderPath;
	private MutexRule rule = new MutexRule();

	/**
	 * @throws Osm2xpBusinessException
	 */
	public void launchBuild() throws Osm2xpBusinessException {
		String currentFilePath = GuiOptionsHelper.getOptions()
				.getCurrentFilePath();
		String path = StringUtils.stripToEmpty(currentFilePath).trim();
		if (path.isEmpty()) {
			MessageDialog.openError(Display.getDefault().getActiveShell(),"No file specified", "No file with OSM data (*.pbf, *.osm...) specified. Please choose valid file");
			try {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(MainSceneryFileView.ID);
			} catch (PartInitException e) {
				Activator.log(e);
			}
			return;
		}
		File currentFile = new File(path);
		if (!currentFile.isFile()) {
			MessageDialog.openError(Display.getDefault().getActiveShell(),"Invalid file specified", "Can't open OSM data file " + path + ". Please check this file exists");
			try {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(MainSceneryFileView.ID);
			} catch (PartInitException e) {
				Activator.log(e);
			}
			return;
		}
		// if choosen output mode will generate file, first check that user is
		// ok to overwrite file is present.
		if (GuiOptionsHelper.isOutputFormatAFileGenerator()) {
			folderPath = currentFile.getParent() + File.separator
					+ GuiOptionsHelper.getSceneName();

			if (!new File(folderPath).exists()
					|| MessageDialog.openConfirm(Display.getCurrent()
							.getActiveShell(), "Confirm", folderPath
							+ " already exists, delete?")) {
				startGeneration(currentFile);
			}
		} else {
			startGeneration(currentFile);
		}
	}

	/**
	 * @throws Osm2xpBusinessException
	 */
	public void restartImportedProject() throws Osm2xpBusinessException {

		// switch to build perspective
		switchToBuildPerspective();
		GuiOptionsHelper.getOptions().setCurrentFilePath(
				Osm2xpProjectHelper.getOsm2XpProject().getFile());
		File currentFile = new File(Osm2xpProjectHelper.getOsm2XpProject()
				.getFile());
		this.folderPath = Osm2xpProjectHelper.getProjectFile().getParent();

		for (Coordinates coordinates : Osm2xpProjectHelper.getOsm2XpProject()
				.getCoordinatesList().getCoordinates()) {
			Point2D tuile = new Point2D(coordinates.getLongitude(),
					coordinates.getLatitude());
			try {
				generateSingleTile(currentFile, tuile, folderPath, null);
			} catch (Osm2xpBusinessException e) {
				Osm2xpLogger.error("Error generating tile", e);
			}
		}

	}

	/**
	 * @param currentFile
	 * @throws Osm2xpBusinessException
	 */
	private void startGeneration(File currentFile)
			throws Osm2xpBusinessException {
		// delete existing file if exists
		if (GuiOptionsHelper.isOutputFormatAFileGenerator()
				&& new File(folderPath).exists()) {
			FilesUtils.deleteDirectory(new File(folderPath));
		}
		// switch to build perspective
		switchToBuildPerspective();
		// get user setted cordinates
		Point2D coordinates = GuiOptionsHelper.getSelectedCoordinates();
		// launch generation
		
		if (coordinates == null) {
			if (GuiOptionsHelper.getOptions().isSinglePass()) {
				generateWholeFileOnASinglePass(currentFile, folderPath, null);
			} else {
				generateWholeFile(currentFile, folderPath);
			}
		} else {
			generateSingleTile(currentFile, coordinates, folderPath, null);
		}

//		new ParsingExperimentJob(currentFile).schedule(); //Experimental to check new osmosis API
	}

	private void switchToBuildPerspective() {
		// switch perspective
		IWorkbench workbench = PlatformUI.getWorkbench();
		try {
			workbench.showPerspective(Perspectives.PERSPECTIVE_BUILD,
					workbench.getActiveWorkbenchWindow());
		} catch (WorkbenchException e) {
			Osm2xpLogger.warning("Error switching perspective", e);
		}
	}

	/**
	 * @param currentFile
	 * @param coordinates
	 * @param folderPath
	 * @param list
	 * @throws Osm2xpBusinessException
	 */
	private void generateWholeFileOnASinglePass(File currentFile,
			final String folderPath, List<Relation> relationsList)
			throws Osm2xpBusinessException {
		String jobTitle = "Generate " + currentFile.getName();
		final GenerateTileJob job = new GenerateTileJob(jobTitle, currentFile,
				null, folderPath, null, "todoJob");
		job.setRule(new MutexRule());
		job.addJobChangeListener(new JobChangeAdapter() {

			@Override
			public void done(IJobChangeEvent event) {
				job.setFamilly("endedJob");
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
						if ((Job.getJobManager().find("todoJob")).length == 0
								&& GuiOptionsHelper
										.isOutputFormatAFileGenerator()) {
							try {
								StatsHelper.RecapStats(folderPath);

							} catch (Osm2xpBusinessException e) {
								Osm2xpLogger.warning(
										"Error saving recap stats.", e);
							}
							Osm2xpLogger.info("Generation finished.");

							// MiscUtils.switchPerspective(GuiOptionsHelper
							// .getOptions().getOutputFormat());

						}
					}
				});

			}

		});

		job.setRule(rule);
		job.schedule();

	}

	/**
	 * @param currentFile
	 * @param coordinates
	 * @param folderPath
	 * @param list
	 * @throws Osm2xpBusinessException
	 */
	private void generateSingleTile(File currentFile, Point2D coordinates,
			final String folderPath, List<Relation> relationsList)
			throws Osm2xpBusinessException {
		String jobTitle = "Generate tile " + +(int) coordinates.y + " / "
				+ (int) coordinates.x + " of file " + currentFile.getName();
		final GenerateTileJob job = new GenerateTileJob(jobTitle, currentFile,
				coordinates, folderPath, relationsList, "todoJob");
		job.setRule(new MutexRule());
		job.addJobChangeListener(new JobChangeAdapter() {


			@Override
			public void done(IJobChangeEvent event) {
				job.setFamilly("endedJob");
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
						if ((Job.getJobManager().find("todoJob")).length == 0) {
							try {
								StatsHelper.RecapStats(folderPath);

							} catch (Osm2xpBusinessException e) {
								Osm2xpLogger.warning(
										"Error saving recap stats.", e);
							}
							Osm2xpLogger.info("Generation finished.");

							// MiscUtils.switchPerspective(GuiOptionsHelper
							// .getOptions().getOutputFormat());

						}
					}
				});

			}

			@Override
			public void awake(IJobChangeEvent event) {

			}

			@Override
			public void aboutToRun(IJobChangeEvent event) {

			}
		});

		job.setRule(rule);
		job.schedule();

	}

	/**
	 * @param currentFile
	 * @param folderPath
	 */
	private void generateWholeFile(final File currentFile,
			final String folderPath) {
		Job job = new Job("Listing tiles ") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final TilesLister tilesLister = TilesListerFactory
						.getTilesLister(currentFile);
				final RelationsLister relationsLister = RelationsListerFactory
						.getRelationsLister(currentFile);
				Osm2xpLogger.info("Listing relations in file " + currentFile.getName());
				try {
					relationsLister.process();
					Osm2xpLogger.info(relationsLister.getRelationsList().size()
							+ " relations found.");
				} catch (Osm2xpBusinessException e) {
					Osm2xpLogger.error(e.getMessage());
				}
				Osm2xpLogger.info("Listing tiles in file " + currentFile.getName());
				try {
					tilesLister.process();
				} catch (Osm2xpBusinessException e) {
					Osm2xpLogger.error(
							"Error listing tiles :\n" + e.getMessage(), e);
				}

				// If you want to update the UI
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
					}
				});

				// get tiles list
				List<Point2D> tilesList = new ArrayList<Point2D>(
						tilesLister.getTilesList());
				Osm2xpLogger.info("listing of tiles complete");
				Osm2xpLogger.info(tilesList.size() + " tile(s) found");
				// init the current project, only if the output mode will
				// generate files
				if (GuiOptionsHelper.isOutputFormatAFileGenerator()) {
					try {
						Osm2xpProjectHelper.initProject(tilesList, folderPath,
								GuiOptionsHelper.getOptions()
										.getCurrentFilePath());
					} catch (Osm2xpBusinessException e1) {
						Osm2xpLogger.error("Error creating project file", e1);
					}
				}
				// launch a build for each tile
				for (Point2D tuile : tilesList) {
					try {
						generateSingleTile(currentFile, tuile, folderPath,
								relationsLister.getRelationsList());
					} catch (Osm2xpBusinessException e) {
						Osm2xpLogger.error("Error generating tile", e);
						canceling();
					}
				}
				if (tilesList.isEmpty()) {
					try {
						GuiOptionsHelper.getOptions().setSinglePass(true);
						generateWholeFileOnASinglePass(currentFile, folderPath, relationsLister.getRelationsList());
					} catch (Osm2xpBusinessException e) {
						Osm2xpLogger.error("Error generating tile", e);
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e);
					}
				}
				return Status.OK_STATUS;

			}
		};

		job.schedule();

	}
}
