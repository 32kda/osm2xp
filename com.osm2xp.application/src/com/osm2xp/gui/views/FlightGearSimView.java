package com.osm2xp.gui.views;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.gui.views.panels.flightGear.FlightGearSimPanel;

/**
 * FlightGear simulator (installation paths) options view.
 *
 * @author osm2xp
 */
public class FlightGearSimView extends AbstractOptionsView implements IContextProvider {

	public static final String ID = "com.osm2xp.viewFlightGearSimTab";

	public FlightGearSimView() {
		super("FlightGear sim options", "images/toolbarsIcons/advanced_32.png");
	}

	@Override
	protected void createFormControls() {
		Section sectionSim = createSection("FlightGear installation", true);
		FlightGearSimPanel simPanel = new FlightGearSimPanel(sectionSim, SWT.BORDER);
		toolkit.adapt(simPanel, true, true);
		sectionSim.setClient(simPanel);
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
		return "FlightGear sim";
	}
}
