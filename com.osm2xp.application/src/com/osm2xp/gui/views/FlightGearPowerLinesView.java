package com.osm2xp.gui.views;

import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.swt.SWT;
import org.eclipse.ui.forms.widgets.Section;

import com.osm2xp.gui.views.panels.flightGear.FlightGearPowerLinesPanel;

/**
 * FlightGear power lines options view.
 *
 * @author osm2xp
 */
public class FlightGearPowerLinesView extends AbstractOptionsView implements IContextProvider {

	public FlightGearPowerLinesView() {
		super("Power lines options", "images/toolbarsIcons/light-16.png");
	}

	@Override
	protected void createFormControls() {
		Section sectionPowerLines = createSection("Power lines", true);
		FlightGearPowerLinesPanel powerLinesPanel = new FlightGearPowerLinesPanel(sectionPowerLines, SWT.BORDER);
		toolkit.adapt(powerLinesPanel, true, true);
		sectionPowerLines.setClient(powerLinesPanel);
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
		return "power lines";
	}
}
