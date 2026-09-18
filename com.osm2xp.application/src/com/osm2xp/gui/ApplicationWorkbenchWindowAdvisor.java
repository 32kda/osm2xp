package com.osm2xp.gui;

import java.io.PrintStream;

import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PerspectiveAdapter;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.registry.ActionSetRegistry;
import org.eclipse.ui.internal.registry.IActionSetDescriptor;
import org.osgi.service.prefs.BackingStoreException;

import com.osm2xp.controllers.BuildController;
import com.osm2xp.core.constants.CoreConstants;
import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.gui.perspectives.IGenerationModeProvider;

/**
 * ApplicationWorkbenchWindowAdvisor
 * 
 * @author Benjamin Blanchet
 * 
 */
@SuppressWarnings("restriction")
public class ApplicationWorkbenchWindowAdvisor extends WorkbenchWindowAdvisor {
	
	private static final String UI_LAYOUT_VERSION_KEY = "UI_LAYOUT_VERSION";
	private static int UI_LAYOUT_VERSION = 2;

	public ApplicationWorkbenchWindowAdvisor(
			IWorkbenchWindowConfigurer configurer) {
		super(configurer);
	}

	public ActionBarAdvisor createActionBarAdvisor(
			IActionBarConfigurer configurer) {
		return new ApplicationActionBarAdvisor(configurer);
	}

	public void preWindowOpen() {
		MessageConsole myConsole = new MessageConsole("", null);
		ConsolePlugin.getDefault().getConsoleManager()
				.addConsoles(new IConsole[] { myConsole });
		MessageConsoleStream stream = myConsole.newMessageStream();
		PrintStream myS = new PrintStream(stream);

		System.setOut(myS); // link standard output stream to the console
		System.setErr(myS); // link error output stream to the console

		IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
		configurer.setInitialSize(new Point(800, 600));
		configurer.setShowStatusLine(false);
		configurer.setShowCoolBar(true);
		configurer.setTitle("Osm2xp");
		configurer.setShowPerspectiveBar(true);
		configurer.setShowProgressIndicator(true);
		PlatformUI.getPreferenceStore().setValue(
				IWorkbenchPreferenceConstants.SHOW_OTHER_IN_PERSPECTIVE_MENU,
				false);
		PlatformUI.getPreferenceStore().setValue(
				IWorkbenchPreferenceConstants.SHOW_OPEN_ON_PERSPECTIVE_BAR,
				false);
		PlatformUI.getPreferenceStore().setValue(
				IWorkbenchPreferenceConstants.SHOW_TRADITIONAL_STYLE_TABS,
				false);
		configurer.setShowPerspectiveBar(false);
		removeUnwantedToolItems();
	}
	
	@Override
	public void postWindowOpen() {
		super.postWindowOpen();
		IEclipsePreferences node = ConfigurationScope.INSTANCE.getNode(Activator.PLUGIN_ID);
		int version = node.getInt(UI_LAYOUT_VERSION_KEY, 0);
		if (version < UI_LAYOUT_VERSION) {
			getWindowConfigurer().getWindow().getActivePage().resetPerspective();
			node.putInt(UI_LAYOUT_VERSION_KEY,UI_LAYOUT_VERSION);
			try {
				node.flush();
			} catch (BackingStoreException e) {
				Activator.log(e);
			}
		}
	}

	/**
	 * 
	 */
	private void removeUnwantedToolItems() {
		ActionSetRegistry reg = WorkbenchPlugin.getDefault()
				.getActionSetRegistry();
		IActionSetDescriptor[] actionSets = reg.getActionSets();
		// remove unwanted menu items
		String actionSetId = "org.eclipse.ui.edit.text.actionSet";
		String searchSetId = "org.eclipse.search.searchActionSet";
		for (int i = 0; i < actionSets.length; i++) {
			if (actionSets[i].getId().equalsIgnoreCase(searchSetId)
					|| actionSets[i].getId().startsWith(actionSetId)) {
				IExtension ext = actionSets[i].getConfigurationElement()
						.getDeclaringExtension();
				reg.removeExtension(ext, new Object[] { actionSets[i] });
			}
		}

	}

	@Override
	public void postWindowCreate() {
		super.postWindowCreate();
//		Shell shell = PlatformUI.getWorkbench().getWorkbenchWindows()[0]
//				.getShell();
//		shell.setMaximized(true);
		displayWelcomeMessage();
		// reset last used file
		GlobalOptionsProvider.getOptions().setCurrentFilePath(null);
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().addPerspectiveListener(new PerspectiveAdapter() {
			
			@Override
			public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective,
					IWorkbenchPartReference partRef, String changeId) {
				if (perspective instanceof IGenerationModeProvider) {
					String generationMode = ((IGenerationModeProvider) perspective).getGenerationMode();
					BuildController.setGenerationMode(generationMode);
				}
			}
			
		});
	}
	
	public static void displayWelcomeMessage() {
		Osm2xpLogger.info("==================================================");
		Osm2xpLogger.info("Osm2XP " + CoreConstants.OSM2XP_VERSION
				+ " by Benjamin Blanchet and Dmitry Karpenko");
		Osm2xpLogger.info("==================================================");
	}

	@Override
	public void dispose() {

		try {
			GlobalOptionsProvider.saveOptions();
			XPlaneOptionsProvider.saveOptions();
//			FsxOptionsProvider.saveOptions();
//			FlyLegacyOptionsHelper.saveOptions();
			FlightGearOptionsProvider.saveOptions();
		} catch (Osm2xpBusinessException e) {
			Osm2xpLogger.error(e.getMessage());
		}
		super.dispose();
	}
}
