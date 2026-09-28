package com.osm2xp.gui.views;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.gui.views.panels.flightGear.FlightGearForestsPanel;

/**
 * FlightGear forests (terrain vegetation) options view.
 *
 * @author osm2xp
 */
public class FlightGearForestsView extends AbstractOptionsView implements IContextProvider {

	public static final String ID = "com.osm2xp.viewFlightGearForestsTab";

	public FlightGearForestsView() {
		super("Forests (vegetation) options", "images/toolbarsIcons/leaf_32.png");
	}

	@Override
	protected void createFormControls() {
		Section sectionForests = createSection("Forests (terrain vegetation)", true);
		FlightGearForestsPanel forestsPanel = new FlightGearForestsPanel(sectionForests, SWT.BORDER);
		toolkit.adapt(forestsPanel, true, true);
		sectionForests.setClient(forestsPanel);
	}

	@Override
	public int getContextChangeMask() {
		return 0;
	}

	@Override
	public IContext getContext(Object target) {
		return HelpSystem.getContext("com.osm2xp.objectsHelpContext");
	}

	@Override
	public String getSearchExpression(Object target) {
		return "forests";
	}
}
