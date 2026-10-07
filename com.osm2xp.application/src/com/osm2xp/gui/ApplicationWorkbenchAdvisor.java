package com.osm2xp.gui;

import java.io.File;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.osgi.service.prefs.BackingStoreException;

import com.osm2xp.constants.Perspectives;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.model.facades.FacadeSetManager;
import com.osm2xp.utils.ProcessExecutor;

/**
 * ApplicationWorkbenchAdvisor
 * 
 * @author Benjamin Blanchet
 * 
 */
public class ApplicationWorkbenchAdvisor extends WorkbenchAdvisor {

	private static final String UI_LAYOUT_VERSION_KEY = "UI_LAYOUT_VERSION";
	private static final int UI_LAYOUT_VERSION = 3;

	public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(
			IWorkbenchWindowConfigurer configurer) {
		return new ApplicationWorkbenchWindowAdvisor(configurer);
	}

	public String getInitialWindowPerspectiveId() {
		return Perspectives.PERSPECTIVE_STARTUP;

	}

	@Override
	public void initialize(IWorkbenchConfigurer configurer) {
		// super.initialize(configurer);
		// configurer.setSaveAndRestore(true);
		resetUiLayoutIfNeeded();
		migrateVer3Settings();
	}

	/**
	 * Discards the persisted e4 workbench model when the UI layout version
	 * changes. The model is restored before the workbench window is opened, so
	 * this has to run from {@link #initialize(IWorkbenchConfigurer)} - resetting
	 * the active perspective in {@code postWindowOpen} is too late and leaves the
	 * shared views of the other perspectives in an inconsistent state.
	 */
	private void resetUiLayoutIfNeeded() {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
		int version = node.getInt(UI_LAYOUT_VERSION_KEY, 0);
		if (version < UI_LAYOUT_VERSION) {
			deletePersistedWorkbenchModel();
			node.putInt(UI_LAYOUT_VERSION_KEY, UI_LAYOUT_VERSION);
			try {
				node.flush();
			} catch (BackingStoreException e) {
				Osm2xpLogger.error(e);
			}
		}
	}

	private void deletePersistedWorkbenchModel() {
		try {
			File pluginsFolder = new File(Platform.getLocation().toFile(), ".metadata/.plugins");
			File workbenchFolder = new File(pluginsFolder, "org.eclipse.e4.workbench");
			File[] files = workbenchFolder.listFiles();
			if (files != null) {
				for (File file : files) {
					if (!file.delete()) {
						Osm2xpLogger.error("Could not delete stale workbench model " + file.getAbsolutePath());
					}
				}
			}
		} catch (Exception e) {
			Osm2xpLogger.error("Error resetting workbench layout", e);
		}
	}

	private void migrateVer3Settings() {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID);
		String facadeSets = node.get(FacadeSetManager.FACADE_SETS_PROP,"");
		if (!StringUtils.isEmpty(facadeSets)) {
			XPlaneOptionsProvider.getOptions().setFacadeSets(facadeSets);
			node.remove(FacadeSetManager.FACADE_SETS_PROP);
			try {
				node.flush();
			} catch (BackingStoreException e) {
				Osm2xpLogger.log(e);
			}
		}
	}
	
	@Override
	public boolean preShutdown() {
		ProcessExecutor.getExecutor().shutdown();
		return super.preShutdown();
	}
}
