package com.osm2xp.gui.views;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.gui.views.panels.flightGear.FlightGearAirfieldsPanel;

/**
 * FlightGear airfields options view.
 *
 * @author osm2xp
 */
public class FlightGearAirfieldsView extends AbstractOptionsView implements IContextProvider {

	public static final String ID = "com.osm2xp.viewFlightGearAirfieldsTab";

	public FlightGearAirfieldsView() {
		super("Airfields options", "images/toolbarsIcons/airport_32.png");
	}

	@Override
	protected void createFormControls() {
		Section sectionAirfields = createSection("Airfields", true);
		FlightGearAirfieldsPanel airfieldsPanel = new FlightGearAirfieldsPanel(sectionAirfields);
		toolkit.adapt(airfieldsPanel, true, true);
		sectionAirfields.setClient(airfieldsPanel);
	}

	@Override
	public int getContextChangeMask() {
		return 0;
	}

	@Override
	public IContext getContext(Object target) {
		return HelpSystem.getContext("com.osm2xp.buildingsHelpContext");
	}

	@Override
	public String getSearchExpression(Object target) {
		return "airfields";
	}
}
