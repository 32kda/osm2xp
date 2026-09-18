package com.osm2xp.gui.views;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.gui.views.panels.flightGear.FlightGearBuildingsPanel;

/**
 * FlightGear buildings (OSMBuildings shader / BUILDING_LIST) options view.
 *
 * @author osm2xp
 */
public class FlightGearBuildingsView extends AbstractOptionsView implements IContextProvider {

	public FlightGearBuildingsView() {
		super("Buildings (shader) options", "images/toolbarsIcons/house_32.png");
	}

	@Override
	protected void createFormControls() {
		Section sectionBuildings = createSection("Buildings (OSMBuildings shader)", true);
		FlightGearBuildingsPanel buildingsPanel = new FlightGearBuildingsPanel(sectionBuildings, SWT.BORDER);
		toolkit.adapt(buildingsPanel, true, true);
		sectionBuildings.setClient(buildingsPanel);
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
		return "buildings";
	}
}
